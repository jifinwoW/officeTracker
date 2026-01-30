package com.example.officetimetracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.*

class PunchWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PUNCH = "com.example.officetimetracker.ACTION_PUNCH"
        const val ACTION_BREAK = "com.example.officetimetracker.ACTION_BREAK"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val sessionManager = SessionManager(context)

        when (intent.action) {
            ACTION_PUNCH -> {
                handlePunchAction(context, sessionManager)
                updateAllWidgets(context)
            }
            ACTION_BREAK -> {
                handleBreakAction(context, sessionManager)
                updateAllWidgets(context)
            }
        }
    }

    private fun handlePunchAction(context: Context, sessionManager: SessionManager) {
        val now = System.currentTimeMillis()
        if (sessionManager.hasCheckedOutToday()) return

        if (sessionManager.isCheckedIn()) {
            // Punch Out
            val worked = now - sessionManager.getCheckInMillis() - sessionManager.getTotalBreakMillis()
            sessionManager.addLog("Punch Out (Widget) at ${formatTime(now)} | Worked: ${formatDuration(worked)}")
            sessionManager.saveTotalWorkedToday(worked)
            sessionManager.saveCheckOut(now)
        } else {
            // Punch In
            sessionManager.saveCheckIn(now)
            sessionManager.addLog("Punch In (Widget) at ${formatTime(now)}")
        }
    }

    private fun handleBreakAction(context: Context, sessionManager: SessionManager) {
        val now = System.currentTimeMillis()
        if (!sessionManager.isCheckedIn() || sessionManager.hasCheckedOutToday()) return

        if (sessionManager.isOnBreak()) {
            sessionManager.endBreak(now)
            sessionManager.addLog("Resumed Work (Widget) at ${formatTime(now)}")
        } else {
            sessionManager.startBreak(now)
            sessionManager.addLog("Break (Widget) at ${formatTime(now)}")
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PunchWidgetProvider::class.java))
        for (id in ids) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val sessionManager = SessionManager(context)
        val views = RemoteViews(context.packageName, R.layout.widget_punch)

        val isCheckedIn = sessionManager.isCheckedIn()
        val isOnBreak = sessionManager.isOnBreak()
        val hasCheckedOut = sessionManager.hasCheckedOutToday()

        // Update Status and Estimated Finish
        when {
            hasCheckedOut -> {
                views.setTextViewText(R.id.widgetStatusText, "Shift Completed")
                views.setTextViewText(R.id.btnWidgetAction, "Done")
                views.setViewVisibility(R.id.btnWidgetBreak, View.GONE)
                views.setViewVisibility(R.id.widgetEstFinishText, View.GONE)
            }
            isCheckedIn -> {
                views.setTextViewText(R.id.widgetStatusText, if (isOnBreak) "On Break" else "Working")
                views.setTextViewText(R.id.btnWidgetAction, "Punch Out")
                views.setViewVisibility(R.id.btnWidgetBreak, View.VISIBLE)
                views.setTextViewText(R.id.btnWidgetBreak, if (isOnBreak) "Resume" else "Break")
                
                // Calculate Estimated Punch Out
                val targetMs = (sessionManager.getWorkingHours() * 3600 * 1000).toLong()
                val totalBreakSoFar = if (isOnBreak) {
                    sessionManager.getTotalBreakMillis() + (System.currentTimeMillis() - sessionManager.getBreakStartMillis())
                } else sessionManager.getTotalBreakMillis()
                
                val estimatedFinishMs = sessionManager.getCheckInMillis() + targetMs + totalBreakSoFar
                views.setTextViewText(R.id.widgetEstFinishText, "Est. Out: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(estimatedFinishMs))}")
                views.setViewVisibility(R.id.widgetEstFinishText, View.VISIBLE)
            }
            else -> {
                views.setTextViewText(R.id.widgetStatusText, "Ready to Start")
                views.setTextViewText(R.id.btnWidgetAction, "Punch In")
                views.setViewVisibility(R.id.btnWidgetBreak, View.GONE)
                views.setViewVisibility(R.id.widgetEstFinishText, View.GONE)
            }
        }

        // Timer Logic
        if (isCheckedIn && !isOnBreak) {
            // Live Ticking mode
            val workedSoFar = System.currentTimeMillis() - sessionManager.getCheckInMillis() - sessionManager.getTotalBreakMillis()
            val base = SystemClock.elapsedRealtime() - workedSoFar
            
            views.setViewVisibility(R.id.widgetChronometer, View.VISIBLE)
            views.setViewVisibility(R.id.widgetTimerText, View.GONE)
            views.setChronometer(R.id.widgetChronometer, base, null, true)
        } else {
            // Static mode (On break or stopped)
            val elapsed = when {
                isCheckedIn && isOnBreak -> {
                    val totalBreak = sessionManager.getTotalBreakMillis() + (System.currentTimeMillis() - sessionManager.getBreakStartMillis())
                    System.currentTimeMillis() - sessionManager.getCheckInMillis() - totalBreak
                }
                hasCheckedOut -> sessionManager.getTotalWorkedToday()
                else -> 0L
            }
            views.setViewVisibility(R.id.widgetChronometer, View.GONE)
            views.setViewVisibility(R.id.widgetTimerText, View.VISIBLE)
            views.setTextViewText(R.id.widgetTimerText, formatDuration(elapsed))
        }

        // Intents
        views.setOnClickPendingIntent(R.id.btnWidgetAction, getPendingSelfIntent(context, ACTION_PUNCH))
        views.setOnClickPendingIntent(R.id.btnWidgetBreak, getPendingSelfIntent(context, ACTION_BREAK))
        
        // Clicking widget title opens the app
        val configIntent = Intent(context, MainActivity::class.java)
        val configPendingIntent = PendingIntent.getActivity(context, 0, configIntent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.headerLayout, configPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PunchWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ms))

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            // Match Chronometer's hourly format (H:MM:SS)
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            // Match Chronometer's sub-hour format (MM:SS)
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
