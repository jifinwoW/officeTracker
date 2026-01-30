package com.example.officetimetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

data class DailyLog(val date: String, val logs: List<LogEntry>, val workedMs: Long)

class ReportAdapter : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    private var items: List<DailyLog> = emptyList()

    fun setData(data: List<DailyLog>) {
        items = data.sortedByDescending { it.date }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val dailyLog = items[position]
        holder.textDate.text = formatDate(dailyLog.date)
        holder.textWorked.text = "Total Worked: ${formatDuration(dailyLog.workedMs)}"
        
        val hasLogs = dailyLog.logs.isNotEmpty()
        holder.textStatus.text = if (hasLogs) "COMPLETED" else "NO LOGS"
        holder.textStatus.setBackgroundResource(if (hasLogs) R.drawable.status_badge_bg else R.drawable.status_badge_muted_bg)

        holder.logsContainer.removeAllViews()
        dailyLog.logs.forEach {
            val tv = TextView(holder.itemView.context).apply {
                text = "• ${it.message}"
                textSize = 12f
                setTextColor(context.getColor(R.color.text_secondary))
                setPadding(0, 4, 0, 4)
            }
            holder.logsContainer.addView(tv)
        }
    }

    override fun getItemCount() = items.size

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDate: TextView = view.findViewById(R.id.textDate)
        val textWorked: TextView = view.findViewById(R.id.textWorked)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val logsContainer: LinearLayout = view.findViewById(R.id.logsContainer)
    }

    private fun formatDuration(ms: Long): String {
        var seconds = ms / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun formatDate(dateKey: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val date = sdf.parse(dateKey)
            val outFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
            outFormat.format(date!!)
        } catch (e: Exception) {
            dateKey
        }
    }
}
