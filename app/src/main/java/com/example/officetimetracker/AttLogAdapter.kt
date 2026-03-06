package com.example.officetimetracker

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class AttLogAdapter : RecyclerView.Adapter<AttLogAdapter.ViewHolder>() {

    private var logs: List<AttLogEntry> = emptyList()
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    fun setLogs(logs: List<AttLogEntry>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    /** Called by the fragment every second to refresh the last "in" entry's live duration. */
    fun tickLastEntry() {
        if (logs.isNotEmpty()) notifyItemChanged(logs.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_att_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = logs[position]
        val isIn = entry.direction.lowercase() == "in"

        val colorIn  = ContextCompat.getColor(holder.itemView.context, R.color.success_green)
        val colorOut = ContextCompat.getColor(holder.itemView.context, R.color.error_red)
        val entryColor = if (isIn) colorIn else colorOut

        // Accent bar
        holder.accentBar.setBackgroundColor(entryColor)

        // Direction badge (rounded rect with solid color)
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(holder.itemView.context, 6f)
            setColor(entryColor)
        }
        holder.directionBadge.background = badgeBg
        holder.directionBadge.backgroundTintList = null  // clear any leftover tint
        holder.directionBadge.text = if (isIn) "IN" else "OUT"

        // Time
        val timeMs = parseTime(entry.logDate)
        holder.timeText.text = if (timeMs > 0L) timeFmt.format(Date(timeMs)) else entry.logDate

        // Duration label
        val nextEntry = logs.getOrNull(position + 1)
        when {
            nextEntry != null -> {
                val nextMs = parseTime(nextEntry.logDate)
                val dur = nextMs - timeMs
                holder.durationText.text = if (isIn) "Worked ${formatDur(dur)}" else "Away for ${formatDur(dur)}"
                holder.durationText.visibility = View.VISIBLE
            }
            isIn -> {
                // Last log is "in" — person currently in office; live duration
                val dur = System.currentTimeMillis() - timeMs
                holder.durationText.text = "Currently in office · ${formatDur(dur)}"
                holder.durationText.visibility = View.VISIBLE
            }
            else -> {
                holder.durationText.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = logs.size

    private fun parseTime(str: String): Long =
        try { sdf.parse(str)?.time ?: 0L } catch (e: Exception) { 0L }

    private fun formatDur(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m"
        else "${m}m"
    }

    private fun dpToPx(context: android.content.Context, dp: Float): Float =
        dp * context.resources.displayMetrics.density

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val accentBar: View       = itemView.findViewById(R.id.logAccentBar)
        val directionBadge: TextView = itemView.findViewById(R.id.directionBadge)
        val timeText: TextView    = itemView.findViewById(R.id.logTimeText)
        val durationText: TextView = itemView.findViewById(R.id.logDurationText)
    }
}
