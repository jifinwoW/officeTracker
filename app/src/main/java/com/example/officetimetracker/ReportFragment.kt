
package com.example.officetimetracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import com.example.officetimetracker.LogEntry

class ReportFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var reportAdapter: ReportAdapter
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        recyclerView = view.findViewById(R.id.reportRecyclerView)
        sessionManager = SessionManager(requireContext())

        reportAdapter = ReportAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = reportAdapter

        loadLogs()
        return view
    }

    private fun loadLogs() {
        val logsMap = sessionManager.getAllLogs() // dateKey -> List<LogEntry>
        val targetMs = (sessionManager.getWorkingHours() * 3600 * 1000).toLong()
        val dailyLogs: List<DailyLog> = logsMap.map { entry ->
            val dateKey = entry.key
            val logs = entry.value
            val workedMs = calculateWorkedTime(dateKey)
            val isCheckedOut = sessionManager.getCheckOutMillisForDate(dateKey) > 0L
            DailyLog(dateKey, logs, workedMs, targetMs, isCheckedOut)
        }.sortedByDescending { it.date }

        reportAdapter.setData(dailyLogs)
    }

    private fun calculateWorkedTime(dateKey: String): Long {
        val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val isToday = dateKey == todayKey
        var baseWorked = sessionManager.getTotalWorkedForDate(dateKey)
        
        // Fallback for historical data not yet recalculated with the new logic
        if (baseWorked == 0L && !isToday) {
            val checkIn = sessionManager.getCheckInMillisForDate(dateKey)
            val checkOut = sessionManager.getCheckOutMillisForDate(dateKey)
            val totalBreak = sessionManager.getTotalBreakMillisForDate(dateKey)
            if (checkIn > 0 && checkOut > 0) {
                baseWorked = (checkOut - checkIn - totalBreak).coerceAtLeast(0L)
            }
        }

        return if (isToday && sessionManager.isCheckedIn()) {
            val checkIn = sessionManager.getCheckInMillis()
            val totalBreak = if (sessionManager.isOnBreak()) {
                val breakStart = sessionManager.getBreakStartMillis()
                sessionManager.getTotalBreakMillis() + (System.currentTimeMillis() - breakStart)
            } else sessionManager.getTotalBreakMillis()
            val currentSession = (System.currentTimeMillis() - checkIn - totalBreak).coerceAtLeast(0L)
            baseWorked + currentSession
        } else {
            baseWorked
        }
    }
}
