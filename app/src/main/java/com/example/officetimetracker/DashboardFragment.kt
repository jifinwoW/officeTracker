package com.example.officetimetracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
    private val logAdapter = LogAdapter()

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
            if (!sessionManager.isOnBreak()) options.add("Start Break")
            if (sessionManager.isOnBreak()) options.add("End Break")
            options.add("Check Out")
        } else {
            options.add("Check In")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Select Action")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Check In" -> handleCheckIn()
                    "Start Break" -> handleBreakIn()
                    "End Break" -> handleBreakOut()
                    "Check Out" -> handleCheckOut()
                }
            }
            .show()
    }

    private fun handleCheckIn() {
        val now = System.currentTimeMillis()
        sessionManager.saveCheckIn(now)
        addLog("Checked In at ${formatTime(now)}")
        startTimer()
    }

    private fun handleBreakIn() {
        val now = System.currentTimeMillis()
        sessionManager.startBreak(now)
        addLog("Break started at ${formatTime(now)}")
    }

    private fun handleBreakOut() {
        val now = System.currentTimeMillis()
        sessionManager.endBreak(now)
        addLog("Break ended at ${formatTime(now)}")
    }

    private fun handleCheckOut() {
        val now = System.currentTimeMillis()
        val worked = now - sessionManager.getCheckInMillis() - sessionManager.getTotalBreakMillis()
        addLog("Checked Out at ${formatTime(now)} | Worked: ${formatDuration(worked)}")

        sessionManager.saveTotalWorkedToday(worked)
        sessionManager.saveCheckOut(now)
        stopTimer()
        startTimer()
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
}
