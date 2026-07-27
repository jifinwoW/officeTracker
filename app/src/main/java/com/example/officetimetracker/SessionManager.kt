package com.example.officetimetracker

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(val message: String, val timestamp: Long)

class SessionManager(context: Context) {
    // --- Date-based log and time retrieval ---
    fun getAllLogs(): Map<String, List<LogEntry>> {
        val allLogs = mutableMapOf<String, List<LogEntry>>()
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith("logs-") && value is String) {
                val dateKey = key.removePrefix("logs-")
                val type = object : com.google.gson.reflect.TypeToken<List<LogEntry>>() {}.type
                val logs: List<LogEntry> = Gson().fromJson(value, type)
                allLogs[dateKey] = logs
            }
        }
        return allLogs
    }

    fun getCheckInMillisForDate(dateKey: String): Long =
        prefs.getLong("checkin_time-$dateKey", 0L)

    fun getCheckOutMillisForDate(dateKey: String): Long =
        prefs.getLong("checkout_time-$dateKey", 0L)

    fun getTotalBreakMillisForDate(dateKey: String): Long =
        prefs.getLong("total_break-$dateKey", 0L)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("office_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CHECKIN = "checkin_time"
        private const val KEY_BREAK_START = "break_start"
        private const val KEY_TOTAL_BREAK = "total_break"
        private const val KEY_LOGS = "logs"
        private const val KEY_CHECKOUT = "checkout_time"
        private const val KEY_TOTAL_WORKED_TODAY = "total_worked_today"
        private const val KEY_TOTAL_WORKED_DATE = "total_worked_date"
        private const val KEY_WORKING_HOURS = "working_hours"
        private const val KEY_USER_NAME = "user_name"
    }

    private val todayKey: String
        get() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    // --- Check In ---
    fun saveCheckIn(timeMillis: Long) {
        prefs.edit()
            .putLong("$KEY_CHECKIN-$todayKey", timeMillis)
            .putLong("$KEY_TOTAL_BREAK-$todayKey", 0L)
            .remove("$KEY_BREAK_START-$todayKey")
            .remove("$KEY_CHECKOUT-$todayKey")
            .commit()
    }

    fun getCheckInMillis(): Long = prefs.getLong("$KEY_CHECKIN-$todayKey", 0L)

    fun isCheckedIn(): Boolean = getCheckInMillis() > 0L && !hasCheckedOutToday()

    fun clearCheckIn() {
        prefs.edit()
            .remove("$KEY_CHECKIN-$todayKey")
            .remove("$KEY_BREAK_START-$todayKey")
            .putLong("$KEY_TOTAL_BREAK-$todayKey", 0L)
            .commit()
    }

    // --- Break ---
    fun startBreak(timeMillis: Long) {
        prefs.edit().putLong("$KEY_BREAK_START-$todayKey", timeMillis).commit()
    }

    fun getBreakStartMillis(): Long = prefs.getLong("$KEY_BREAK_START-$todayKey", 0L)

    fun endBreak(timeMillis: Long) {
        val breakStart = getBreakStartMillis()
        val duration = if (breakStart > 0) timeMillis - breakStart else 0L
        val totalBreak = getTotalBreakMillis() + duration
        prefs.edit()
            .putLong("$KEY_TOTAL_BREAK-$todayKey", totalBreak)
            .remove("$KEY_BREAK_START-$todayKey")
            .commit()
    }

    fun isOnBreak(): Boolean = getBreakStartMillis() > 0L

    fun getTotalBreakMillis(): Long = prefs.getLong("$KEY_TOTAL_BREAK-$todayKey", 0L)

    // --- Check Out ---
    fun saveCheckOut(timeMillis: Long) {
        prefs.edit().putLong("$KEY_CHECKOUT-$todayKey", timeMillis).commit()
    }

    fun hasCheckedOutToday(): Boolean = prefs.getLong("$KEY_CHECKOUT-$todayKey", 0L) > 0L

    // --- Logs ---
    fun addLog(message: String) {
        val logs = getLogsForToday().toMutableList()
        logs.add(LogEntry(message, System.currentTimeMillis()))
        prefs.edit().putString("$KEY_LOGS-$todayKey", Gson().toJson(logs)).apply()
    }

    fun getLogsForToday(): List<LogEntry> {
        val json = prefs.getString("$KEY_LOGS-$todayKey", null) ?: return emptyList()
        val type = object : TypeToken<List<LogEntry>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveLogsForToday(logs: List<LogEntry>) {
        prefs.edit().putString("$KEY_LOGS-$todayKey", Gson().toJson(logs)).apply()
    }

    fun clearTodaySession() {
        prefs.edit()
            .remove("$KEY_CHECKIN-$todayKey")
            .remove("$KEY_BREAK_START-$todayKey")
            .remove("$KEY_TOTAL_BREAK-$todayKey")
            .remove("$KEY_CHECKOUT-$todayKey")
            .apply()
    }

    fun rebuildTodaySessionFromLogs(logs: List<LogEntry>) {
        clearTodaySession()
        val sortedLogs = logs.sortedBy { it.timestamp }
        for (entry in sortedLogs) {
            when {
                entry.message.startsWith("Punch In") -> saveCheckIn(entry.timestamp)
                entry.message.startsWith("Punch Break") -> startBreak(entry.timestamp)
                entry.message.startsWith("Resumed Work") -> endBreak(entry.timestamp)
                entry.message.contains("Break ended") -> endBreak(entry.timestamp)
                entry.message.startsWith("Punch Out") -> {
                    if (isOnBreak()) endBreak(entry.timestamp)
                    saveCheckOut(entry.timestamp)
                    val worked = entry.timestamp - getCheckInMillis() - getTotalBreakMillis()
                    saveTotalWorkedToday(worked)
                }
            }
        }
    }

    fun saveTotalWorkedToday(ms: Long) {
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    prefs.edit().putLong(KEY_TOTAL_WORKED_TODAY, ms).apply()
    prefs.edit().putString(KEY_TOTAL_WORKED_DATE, today).apply()
}

fun getTotalWorkedToday(): Long {
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val savedDate = prefs.getString(KEY_TOTAL_WORKED_DATE, "")
    return if (savedDate == today) prefs.getLong(KEY_TOTAL_WORKED_TODAY, 0L) else 0L
}

    // --- Settings ---
    fun setWorkingHours(hours: Float) {
        prefs.edit().putFloat(KEY_WORKING_HOURS, hours).apply()
    }

    fun getWorkingHours(): Float = prefs.getFloat(KEY_WORKING_HOURS, 8.0f) // default to 8 hours

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "User") ?: "User"

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
