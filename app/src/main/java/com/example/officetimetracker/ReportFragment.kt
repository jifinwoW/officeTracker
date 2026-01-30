
package com.example.officetimetracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        val dailyLogs: List<DailyLog> = logsMap.map { entry ->
            val dateKey = entry.key
            val logs = entry.value
            val workedMs = calculateWorkedTime(dateKey)
            DailyLog(dateKey, logs, workedMs)
        }.sortedByDescending { it.date }

        reportAdapter.setData(dailyLogs)
    }

    private fun calculateWorkedTime(dateKey: String): Long {
        val checkIn = sessionManager.getCheckInMillisForDate(dateKey)
        val checkOut = sessionManager.getCheckOutMillisForDate(dateKey)
        val totalBreak = sessionManager.getTotalBreakMillisForDate(dateKey)

        if (checkIn == 0L) return 0L
        val endTime = if (checkOut > 0L) checkOut else System.currentTimeMillis()
        return (endTime - checkIn - totalBreak).coerceAtLeast(0L)
    }
}
