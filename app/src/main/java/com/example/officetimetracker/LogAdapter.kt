package com.example.officetimetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(private val onEditClick: (LogEntry, Int) -> Unit) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs: List<LogEntry> = emptyList()
    private var isEditable: Boolean = true

    fun setEditable(editable: Boolean) {
        this.isEditable = editable
        notifyDataSetChanged()
    }

    fun setLogs(logs: List<LogEntry>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.logText.text = log.message
        holder.editButton.visibility = if (isEditable) View.VISIBLE else View.GONE
        holder.editButton.setOnClickListener { onEditClick(log, position) }
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logText: TextView = itemView.findViewById(R.id.logMessage)
        val editButton: View = itemView.findViewById(R.id.btnEditLog)
    }
}
