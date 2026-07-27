package com.example.officetimetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(private val onEditClick: (position: Int) -> Unit) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs: List<LogEntry> = emptyList()

    fun setLogs(logs: List<LogEntry>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.logText.text = logs[position].message
        holder.editButton.setOnClickListener { onEditClick(position) }
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logText: TextView = itemView.findViewById(R.id.logMessage)
        val editButton: ImageButton = itemView.findViewById(R.id.editLogButton)
    }
}
