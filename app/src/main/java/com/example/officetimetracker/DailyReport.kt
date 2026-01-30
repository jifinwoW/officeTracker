package com.example.officetimetracker

data class DailyReport(
    val date: String,
    val workedMinutes: Int, // total worked minutes
    val breakMinutes: Int   // total break minutes
) {
    val productivity: Int
        get() = ((workedMinutes.toFloat() / 480f) * 100).toInt() // 480 mins = 8 hours
}
