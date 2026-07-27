package com.example.officetimetracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import androidx.biometric.BiometricPrompt
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class DashboardFragment : Fragment() {

    private lateinit var fingerprintFab: ExtendedFloatingActionButton
    private lateinit var timerText: TextView
    private lateinit var remainingText: TextView
    private lateinit var greetingText: TextView
    private lateinit var estFinishText: TextView
    private lateinit var estFinishLayout: View
    private lateinit var workProgressBar: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var sessionManager: SessionManager
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val logAdapter = LogAdapter(::onEditLogEntry)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        fingerprintFab = view.findViewById(R.id.fingerprintFab)
        timerText = view.findViewById(R.id.timerText)
        remainingText = view.findViewById(R.id.remainingText)
        greetingText = view.findViewById(R.id.greetingText)
        estFinishText = view.findViewById(R.id.estFinishText)
        estFinishLayout = view.findViewById(R.id.estFinishLayout)
        workProgressBar = view.findViewById(R.id.workProgressBar)
        logRecyclerView = view.findViewById(R.id.logRecyclerView)

        sessionManager = SessionManager(requireContext())

        // RecyclerView setup
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        logRecyclerView.adapter = logAdapter
        logAdapter.setLogs(sessionManager.getLogsForToday())

        updateGreeting()

        executor = ContextCompat.getMainExecutor(requireContext())
        biometricPrompt = BiometricPrompt(requireActivity(), executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showActionDialog()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(requireContext(), "Auth failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Confirm your identity to log hours")
            .setNegativeButtonText("Cancel")
            .build()

        fingerprintFab.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

        startTimer()
        return view
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        greetingText.text = "$timeGreeting, ${sessionManager.getUserName()}"
    }

    private fun showActionDialog() {
        if (sessionManager.hasCheckedOutToday()) {
            Toast.makeText(requireContext(), "Daily shift completed.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        if (sessionManager.isCheckedIn()) {
            if (!sessionManager.isOnBreak()) options.add("Break")
            if (sessionManager.isOnBreak()) options.add("Resume")
            options.add("Punch Out")
        } else {
            options.add("Punch In")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Select Action")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Punch In" -> handlePunchIn()
                    "Break" -> handlePunchBreak()
                    "Resume" -> handleResumeWork()
                    "Punch Out" -> handlePunchOut()
                }
            }
            .show()
    }

    private fun handlePunchIn() {
        val now = System.currentTimeMillis()
        sessionManager.saveCheckIn(now)
        addLog("Punch In at ${formatTime(now)}")
        Toast.makeText(requireContext(), "Punched In", Toast.LENGTH_SHORT).show()
        startTimer()
        updateWidget()
    }

    private fun handlePunchBreak() {
        val now = System.currentTimeMillis()
        sessionManager.startBreak(now)
        addLog("Punch Break at ${formatTime(now)}")
        Toast.makeText(requireContext(), "Break Started", Toast.LENGTH_SHORT).show()
        updateWidget()
    }

    private fun handleResumeWork() {
        val now = System.currentTimeMillis()
        sessionManager.endBreak(now)
        addLog("Resumed Work at ${formatTime(now)}")
        Toast.makeText(requireContext(), "Work Resumed", Toast.LENGTH_SHORT).show()
        updateWidget()
    }

    private fun handlePunchOut() {
        val now = System.currentTimeMillis()
        
        // Auto-end break if active
        if (sessionManager.isOnBreak()) {
            sessionManager.endBreak(now)
            addLog("Break ended (Auto) at ${formatTime(now)}")
        }

        val worked = now - sessionManager.getCheckInMillis() - sessionManager.getTotalBreakMillis()
        addLog("Punch Out at ${formatTime(now)} | Worked: ${formatDuration(worked)}")

        sessionManager.saveTotalWorkedToday(worked)
        sessionManager.saveCheckOut(now)
        Toast.makeText(requireContext(), "Punched Out", Toast.LENGTH_SHORT).show()
        stopTimer()
        startTimer()
        updateWidget()
    }

    private fun startTimer() {
        stopTimer() // Prevent multiple runnables
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = when {
                    sessionManager.isCheckedIn() -> {
                        val checkIn = sessionManager.getCheckInMillis()
                        val totalBreak = if (sessionManager.isOnBreak()) {
                            val breakStart = sessionManager.getBreakStartMillis()
                            sessionManager.getTotalBreakMillis() + (System.currentTimeMillis() - breakStart)
                        } else sessionManager.getTotalBreakMillis()
                        System.currentTimeMillis() - checkIn - totalBreak
                    }
                    sessionManager.hasCheckedOutToday() -> sessionManager.getTotalWorkedToday()
                    else -> 0L
                }
                
                timerText.text = formatDuration(elapsed)
                
                val workingHours = sessionManager.getWorkingHours()
                val targetMs = (workingHours * 3600 * 1000).toLong()
                val remaining = (targetMs - elapsed).coerceAtLeast(0L)
                remainingText.text = "Target: ${formatDuration(remaining)} remaining"

                // Update Progress Bar
                val progress = if (targetMs > 0) (elapsed.toFloat() / targetMs * 100).toInt() else 0
                workProgressBar.setProgress(progress.coerceIn(0, 100), true)

                // Update Estimated Finish Time
                if (sessionManager.isCheckedIn()) {
                    val checkIn = sessionManager.getCheckInMillis()
                    val totalBreakSoFar = if (sessionManager.isOnBreak()) {
                        val breakStart = sessionManager.getBreakStartMillis()
                        sessionManager.getTotalBreakMillis() + (System.currentTimeMillis() - breakStart)
                    } else sessionManager.getTotalBreakMillis()
                    
                    val estimatedFinishMs = checkIn + targetMs + totalBreakSoFar
                    estFinishText.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(estimatedFinishMs))
                    estFinishLayout.visibility = View.VISIBLE
                } else {
                    estFinishLayout.visibility = View.GONE
                }
                
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun addLog(message: String) {
        sessionManager.addLog(message)
        logAdapter.setLogs(sessionManager.getLogsForToday())
        logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
    }

    private fun onEditLogEntry(position: Int) {
        val todayLogs = sessionManager.getLogsForToday().toMutableList()
        if (position < 0 || position >= todayLogs.size) return

        val selectedLog = todayLogs[position]
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedLog.timestamp }

        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            val newTimestamp = Calendar.getInstance().apply {
                timeInMillis = selectedLog.timestamp
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (newTimestamp == selectedLog.timestamp) return@TimePickerDialog

            todayLogs[position] = selectedLog.copy(timestamp = newTimestamp)
            val normalizedLogs = normalizeLogs(todayLogs)
            sessionManager.saveLogsForToday(normalizedLogs)
            sessionManager.rebuildTodaySessionFromLogs(normalizedLogs)
            logAdapter.setLogs(normalizedLogs)
            logRecyclerView.scrollToPosition(logAdapter.itemCount - 1)
            updateWidget()
            Toast.makeText(requireContext(), "Entry time updated", Toast.LENGTH_SHORT).show()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    private fun normalizeLogs(logs: List<LogEntry>): List<LogEntry> {
        val sorted = logs.sortedBy { it.timestamp }
        var currentCheckIn = 0L
        var breakStart = 0L
        var totalBreak = 0L

        return sorted.map { entry ->
            when {
                entry.message.startsWith("Punch In") -> {
                    currentCheckIn = entry.timestamp
                    breakStart = 0L
                    totalBreak = 0L
                    entry.copy(message = "Punch In at ${formatTime(entry.timestamp)}")
                }
                entry.message.startsWith("Punch Break") -> {
                    breakStart = entry.timestamp
                    entry.copy(message = "Punch Break at ${formatTime(entry.timestamp)}")
                }
                entry.message.startsWith("Resumed Work") -> {
                    if (breakStart > 0L) {
                        totalBreak += entry.timestamp - breakStart
                        breakStart = 0L
                    }
                    entry.copy(message = "Resumed Work at ${formatTime(entry.timestamp)}")
                }
                entry.message.contains("Break ended") -> {
                    if (breakStart > 0L) {
                        totalBreak += entry.timestamp - breakStart
                        breakStart = 0L
                    }
                    entry.copy(message = "Break ended (Auto) at ${formatTime(entry.timestamp)}")
                }
                entry.message.startsWith("Punch Out") -> {
                    if (breakStart > 0L) {
                        totalBreak += entry.timestamp - breakStart
                        breakStart = 0L
                    }
                    val worked = if (currentCheckIn > 0L) entry.timestamp - currentCheckIn - totalBreak else 0L
                    entry.copy(message = "Punch Out at ${formatTime(entry.timestamp)} | Worked: ${formatDuration(worked)}")
                }
                else -> {
                    val regex = "(at\\s+)(\\d{2}:\\d{2}:\\d{2}\\s+[AP]M)".toRegex()
                    if (regex.containsMatchIn(entry.message)) {
                        entry.copy(message = regex.replace(entry.message, "$1${formatTime(entry.timestamp)}"))
                    } else {
                        entry
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(ms))

    private fun formatDuration(ms: Long): String {
        var seconds = ms / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun updateWidget() {
        val intent = Intent(requireContext(), PunchWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(requireContext())
            .getAppWidgetIds(ComponentName(requireContext(), PunchWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        requireContext().sendBroadcast(intent)
    }
}
