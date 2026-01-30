package com.example.officetimetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private var logs: List<LogEntry> = emptyList()

    fun setLogs(logs: List<LogEntry>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.logText.text = logs[position].message
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logText: TextView = itemView.findViewById(android.R.id.text1)
    }
}
