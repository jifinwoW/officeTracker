

package com.example.officetimetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.officetimetracker.DailyReport



class DailyReportAdapter(private val reports: List<DailyReport>) :
    RecyclerView.Adapter<DailyReportAdapter.ReportViewHolder>() {

    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.textDate)
        val workedText: TextView = itemView.findViewById(R.id.textWorked)
        val statusText: TextView = itemView.findViewById(R.id.textStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]

        holder.dateText.text = report.date
        val hours = report.workedMinutes / 60
        val minutes = report.workedMinutes % 60
        holder.workedText.text = "Worked: %d hr %02d min".format(hours, minutes)
        holder.statusText.text = "Productivity: ${report.productivity}%"
    }

    override fun getItemCount() = reports.size
}

