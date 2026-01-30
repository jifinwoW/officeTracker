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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.biometric.BiometricPrompt
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator

class DashboardFragment : Fragment() {

    private lateinit var fingerprintFab: FloatingActionButton
    private lateinit var timerText: TextView
    private lateinit var remainingText: TextView
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
        logRecyclerView = view.findViewById(R.id.logRecyclerView)

        // --- Add glow animation here ---
    val glowView = view.findViewById<View>(R.id.fabGlow)
    val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
        glowView,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f),
        PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0.8f)
    ).apply {
        duration = 1000
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
    }
    scaleUp.start()

        sessionManager = SessionManager(requireContext())

        // RecyclerView setup
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        logRecyclerView.adapter = logAdapter
        logAdapter.setLogs(sessionManager.getLogsForToday())

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
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Cancel")
            .build()

        fingerprintFab.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }

        startTimer()
        return view
    }

    private fun showActionDialog() {
        // After check-out, no options today
        if (sessionManager.hasCheckedOutToday()) {
            Toast.makeText(requireContext(), "You have already checked out today.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        if (sessionManager.isCheckedIn()) {
            if (!sessionManager.isOnBreak()) options.add("Break In")
            if (sessionManager.isOnBreak()) options.add("Break Out")
            options.add("Check Out")
        } else {
            options.add("Check In")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Select Action")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Check In" -> handleCheckIn()
                    "Break In" -> handleBreakIn()
                    "Break Out" -> handleBreakOut()
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
        stopTimer()
    }

    private fun handleBreakOut() {
        val now = System.currentTimeMillis()
        sessionManager.endBreak(now)
        addLog("Break ended at ${formatTime(now)}")
        startTimer()
    }

    private fun handleCheckOut() {
        val now = System.currentTimeMillis()
        val worked = now - sessionManager.getCheckInMillis() - sessionManager.getTotalBreakMillis()
        addLog("Checked Out at ${formatTime(now)} | Worked: ${formatDuration(worked)}")

        // Save total worked today
        sessionManager.saveTotalWorkedToday(worked)
        sessionManager.saveCheckOut(now)
        stopTimer()
        startTimer() // show timer even after checkout
    }

    private fun startTimer() {
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
                timerText.text = "Worked: ${formatDuration(elapsed)}"
                val eightHoursMs = 8 * 3600 * 1000L
                val remaining = (eightHoursMs - elapsed).coerceAtLeast(0L)
                remainingText.text = "Remaining: ${formatDuration(remaining)}"
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
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    private fun formatDuration(ms: Long): String {
        var seconds = ms / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
