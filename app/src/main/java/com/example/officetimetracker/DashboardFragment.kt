package com.example.officetimetracker

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var sessionManager: SessionManager

    // Employee card views
    private lateinit var greetingText: TextView
    private lateinit var empIdText: TextView
    private lateinit var empDesigText: TextView
    private lateinit var empStatusText: TextView

    // Date navigation views
    private lateinit var btnPrevDay: ImageButton
    private lateinit var btnNextDay: ImageButton
    private lateinit var dateLabelText: TextView

    // Summary card views
    private lateinit var totalWorkedText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var estFinishText: TextView
    private lateinit var firstInText: TextView
    private lateinit var lastOutText: TextView
    private lateinit var attendanceBadgeCard: MaterialCardView
    private lateinit var attendanceBadgeText: TextView

    // Log list views
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var attLogRecyclerView: RecyclerView
    private lateinit var logsProgressIndicator: CircularProgressIndicator
    private lateinit var emptyLogsView: LinearLayout
    private lateinit var emptyLogsText: TextView
    private lateinit var btnRefresh: ImageButton

    // Adapter & data
    private lateinit var attLogAdapter: AttLogAdapter
    private var allLogs: List<AttLogEntry> = emptyList()
    private var selectedDate: Calendar = Calendar.getInstance()

    // Live timer for "currently in office" state
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        sessionManager = SessionManager(requireContext())

        // Employee card
        greetingText   = view.findViewById(R.id.greetingText)
        empIdText      = view.findViewById(R.id.empIdText)
        empDesigText   = view.findViewById(R.id.empDesigText)
        empStatusText  = view.findViewById(R.id.empStatusText)

        // Date nav
        btnPrevDay     = view.findViewById(R.id.btnPrevDay)
        btnNextDay     = view.findViewById(R.id.btnNextDay)
        dateLabelText  = view.findViewById(R.id.dateLabelText)

        // Summary
        totalWorkedText     = view.findViewById(R.id.totalWorkedText)
        remainingTimeText   = view.findViewById(R.id.remainingTimeText)
        estFinishText       = view.findViewById(R.id.estFinishText)
        firstInText         = view.findViewById(R.id.firstInText)
        lastOutText         = view.findViewById(R.id.lastOutText)
        attendanceBadgeCard = view.findViewById(R.id.attendanceBadgeCard)
        attendanceBadgeText = view.findViewById(R.id.attendanceBadgeText)

        // Log list
        swipeRefreshLayout   = view.findViewById(R.id.swipeRefreshLayout)
        attLogRecyclerView   = view.findViewById(R.id.attLogRecyclerView)
        logsProgressIndicator = view.findViewById(R.id.logsProgressIndicator)
        emptyLogsView        = view.findViewById(R.id.emptyLogsView)
        emptyLogsText        = view.findViewById(R.id.emptyLogsText)
        btnRefresh           = view.findViewById(R.id.btnRefresh)

        // RecyclerView setup
        attLogAdapter = AttLogAdapter()
        attLogRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        attLogRecyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        attLogRecyclerView.adapter = attLogAdapter

        // Date navigation clicks
        btnPrevDay.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            displayLogsForSelectedDate()
        }
        btnNextDay.setOnClickListener {
            if (!isSameDay(selectedDate, Calendar.getInstance())) {
                selectedDate.add(Calendar.DAY_OF_YEAR, 1)
                displayLogsForSelectedDate()
            }
        }

        btnRefresh.setOnClickListener { fetchLogs() }

        swipeRefreshLayout.setColorSchemeResources(R.color.accent_blue)
        swipeRefreshLayout.setOnRefreshListener { fetchLogs() }

        return view
    }

    override fun onResume() {
        super.onResume()
        populateEmployeeCard()
        if (allLogs.isEmpty()) fetchLogs() else displayLogsForSelectedDate()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTimer()
    }

    // -------------------------------------------------------------------------
    // Employee card
    // -------------------------------------------------------------------------

    private fun populateEmployeeCard() {
        val name        = sessionManager.getEmployeeDisplayName()
        val code        = sessionManager.getEmployeeCode()
        val designation = sessionManager.getEmployeeDesignation()
        val status      = sessionManager.getEmployeeStatus()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greetingText.text = "${when (hour) {
            in 0..11  -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else      -> "Good Evening"
        }}, $name"

        empIdText.text    = "ID: $code"
        empDesigText.text = if (designation.isNotBlank()) designation else "Employee"
        empStatusText.text = status
    }

    // -------------------------------------------------------------------------
    // API fetch
    // -------------------------------------------------------------------------

    private fun fetchLogs() {
        setLoadingState()
        viewLifecycleOwner.lifecycleScope.launch {
            val empCode = sessionManager.getEmployeeCode()
            when (val result = EmployeeApiService.getAttendanceLogs(empCode)) {
                is ApiResult.Success -> {
                    allLogs = result.data.sortedBy { it.logDate }
                    displayLogsForSelectedDate()
                }
                is ApiResult.Error -> {
                    showEmptyState("Could not load logs: ${result.message}")
                    clearSummary()
                }
                is ApiResult.NetworkError -> {
                    showEmptyState("Cannot reach the office server.\nPlease connect to the office Wi-Fi and try again.")
                    clearSummary()
                    com.google.android.material.snackbar.Snackbar
                        .make(requireView(), "Server unreachable — not on office Wi-Fi?", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction("Retry") { fetchLogs() }
                        .show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Display for selected date
    // -------------------------------------------------------------------------

    private fun displayLogsForSelectedDate() {
        stopTimer()
        updateDateNavButtons()

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
        val dayLogs = allLogs.filter { it.logDate.startsWith(dateKey) }.sortedBy { it.logDate }

        if (dayLogs.isEmpty()) {
            showEmptyState("No attendance records for this date")
            clearSummary()
        } else {
            showLogsList(dayLogs)
            renderSummary(dayLogs)
        }
    }

    // -------------------------------------------------------------------------
    // Summary calculation + live timer
    // -------------------------------------------------------------------------

    private fun renderSummary(logs: List<AttLogEntry>) {
        var totalWorkedMs = 0L
        var lastInMs      = 0L
        var firstInMs     = 0L
        var lastOutMs     = 0L
        var isCurrentlyIn = false

        for (log in logs) {
            val t = parseTime(log.logDate)
            when (log.direction.lowercase()) {
                "in" -> {
                    if (firstInMs == 0L) firstInMs = t
                    lastInMs = t
                    isCurrentlyIn = true
                }
                "out" -> {
                    lastOutMs = t
                    if (lastInMs > 0L) {
                        totalWorkedMs += (t - lastInMs)
                        lastInMs = 0L
                    }
                    isCurrentlyIn = false
                }
            }
        }

        // First In / Last Out labels
        firstInText.text  = if (firstInMs > 0L) timeFmt.format(Date(firstInMs)) else "—"
        lastOutText.text  = if (lastOutMs > 0L) timeFmt.format(Date(lastOutMs)) else "—"

        val isToday = isSameDay(selectedDate, Calendar.getInstance())
        val baseWorked = totalWorkedMs
        val targetMs = (sessionManager.getWorkingHours() * 3_600_000L).toLong()

        if (isCurrentlyIn && isToday) {
            val liveInStart = lastInMs
            setBadge("In Office", Color.parseColor("#10B981"))

            // Start live 1-second ticker
            timerRunnable = object : Runnable {
                override fun run() {
                    val liveWorked = baseWorked + (System.currentTimeMillis() - liveInStart)
                    totalWorkedText.text = formatDuration(liveWorked)
                    updateRemainingTime(liveWorked, targetMs, isCurrentlyIn = true)
                    attLogAdapter.tickLastEntry()
                    handler.postDelayed(this, 1000L)
                }
            }
            handler.post(timerRunnable!!)
        } else {
            totalWorkedText.text = formatDuration(baseWorked)
            updateRemainingTime(baseWorked, targetMs, isCurrentlyIn = false)
            setBadge(
                if (lastOutMs > 0L) "Checked Out" else "No Activity",
                if (lastOutMs > 0L) Color.parseColor("#EF4444") else Color.parseColor("#64748B")
            )
        }
    }

    private fun updateRemainingTime(workedMs: Long, targetMs: Long, isCurrentlyIn: Boolean) {
        val remaining = targetMs - workedMs
        if (remaining > 0) {
            remainingTimeText.text = "${formatDuration(remaining)} remaining"
            estFinishText.text = if (isCurrentlyIn) {
                val eta = System.currentTimeMillis() + remaining
                "Est. finish: ${timeFmt.format(Date(eta))}"
            } else {
                ""
            }
        } else {
            remainingTimeText.text = "Target hours complete \u2713"
            estFinishText.text = ""
        }
    }

    private fun clearSummary() {
        totalWorkedText.text   = "00:00:00"
        remainingTimeText.text = ""
        estFinishText.text     = ""
        firstInText.text       = "\u2014"
        lastOutText.text       = "\u2014"
        setBadge("No Data", Color.parseColor("#64748B"))
    }

    private fun setBadge(label: String, color: Int) {
        attendanceBadgeText.text = label
        attendanceBadgeCard.setCardBackgroundColor(color)
    }

    // -------------------------------------------------------------------------
    // UI state helpers
    // -------------------------------------------------------------------------

    private fun setLoadingState() {
        swipeRefreshLayout.isRefreshing = true
        logsProgressIndicator.visibility = View.GONE
        attLogRecyclerView.visibility    = View.GONE
        emptyLogsView.visibility         = View.GONE
    }

    private fun showEmptyState(message: String) {
        swipeRefreshLayout.isRefreshing  = false
        logsProgressIndicator.visibility = View.GONE
        attLogRecyclerView.visibility    = View.GONE
        emptyLogsView.visibility         = View.VISIBLE
        emptyLogsText.text               = message
    }

    private fun showLogsList(logs: List<AttLogEntry>) {
        attLogAdapter.setLogs(logs)
        swipeRefreshLayout.isRefreshing  = false
        logsProgressIndicator.visibility = View.GONE
        emptyLogsView.visibility         = View.GONE
        attLogRecyclerView.visibility    = View.VISIBLE
    }

    private fun updateDateNavButtons() {
        val today = Calendar.getInstance()
        val isToday = isSameDay(selectedDate, today)
        btnNextDay.alpha      = if (isToday) 0.3f else 1.0f
        btnNextDay.isEnabled  = !isToday

        val isYesterday = run {
            val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            isSameDay(selectedDate, y)
        }
        val formatted = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(selectedDate.time)
        dateLabelText.text = when {
            isToday     -> "Today · $formatted"
            isYesterday -> "Yesterday · $formatted"
            else        -> formatted
        }
    }

    // -------------------------------------------------------------------------
    // Timer
    // -------------------------------------------------------------------------

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    private fun parseTime(str: String): Long =
        try { sdf.parse(str)?.time ?: 0L } catch (e: Exception) { 0L }

    private fun formatDuration(ms: Long): String {
        var s = ms / 1000
        val h = s / 3600; s %= 3600
        val m = s / 60;   s %= 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
