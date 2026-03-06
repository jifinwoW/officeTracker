package com.example.officetimetracker

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(val message: String, val timestamp: Long)

class SessionManager(context: Context) {
    // --- Date-based log and time retrieval ---
    fun getAllLogs(): Map<String, List<LogEntry>> {
        val allLogs = mutableMapOf<String, List<LogEntry>>()
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith("logs-") && value is String) {
                val dateKey = key.removePrefix("logs-")
                val type = object : com.google.gson.reflect.TypeToken<List<LogEntry>>() {}.type
                val logs: List<LogEntry> = Gson().fromJson(value, type)
                allLogs[dateKey] = logs
            }
        }
        return allLogs
    }

    fun getCheckInMillisForDate(dateKey: String): Long =
        prefs.getLong("checkin_time-$dateKey", 0L)

    fun getCheckOutMillisForDate(dateKey: String): Long =
        prefs.getLong("checkout_time-$dateKey", 0L)

    fun getTotalWorkedForDate(dateKey: String): Long =
        prefs.getLong("total_worked-$dateKey", 0L)

    fun getTotalBreakMillisForDate(dateKey: String): Long =
        prefs.getLong("total_break-$dateKey", 0L)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("office_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CHECKIN = "checkin_time"
        private const val KEY_BREAK_START = "break_start"
        private const val KEY_TOTAL_BREAK = "total_break"
        private const val KEY_LOGS = "logs"
        private const val KEY_CHECKOUT = "checkout_time"
        private const val KEY_TOTAL_WORKED_TODAY = "total_worked_today"
        private const val KEY_TOTAL_WORKED_DATE = "total_worked_date"
        private const val KEY_WORKING_HOURS = "working_hours"
        private const val KEY_USER_NAME = "user_name"

        // --- Employee / ESS keys ---
        private const val KEY_EMP_ID      = "employee_id"
        private const val KEY_EMP_CODE    = "employee_code"
        private const val KEY_EMP_NAME    = "employee_name"
        private const val KEY_EMP_DESIG   = "employee_designation"
        private const val KEY_EMP_STATUS  = "employee_status"
        private const val KEY_EMP_TYPE    = "employee_type"
        private const val KEY_EMP_DOJ     = "employee_doj"
        private const val KEY_EMP_DEVICE  = "employee_device_id"
        private const val KEY_EMP_COMPANY = "employee_company_id"
        private const val KEY_EMP_DEPT    = "employee_dept_id"
        private const val KEY_EMP_LOGGED_IN = "employee_logged_in"
    }

    private val todayKey: String
        get() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    // --- Check In ---
    fun saveCheckIn(timeMillis: Long) {
        saveCheckInForDate(todayKey, timeMillis)
    }

    fun updateCheckIn(timeMillis: Long) {
        prefs.edit().putLong("$KEY_CHECKIN-$todayKey", timeMillis).commit()
    }

    fun saveCheckInForDate(dateKey: String, timeMillis: Long) {
        prefs.edit()
            .putLong("$KEY_CHECKIN-$dateKey", timeMillis)
            .putLong("$KEY_TOTAL_BREAK-$dateKey", 0L)
            .remove("$KEY_BREAK_START-$dateKey")
            .remove("$KEY_CHECKOUT-$dateKey")
            .commit()
    }

    fun getCheckInMillis(): Long = prefs.getLong("$KEY_CHECKIN-$todayKey", 0L)

    fun isCheckedIn(): Boolean = getCheckInMillis() > 0L && !hasCheckedOutToday()

    fun clearCheckIn() {
        prefs.edit()
            .remove("$KEY_CHECKIN-$todayKey")
            .remove("$KEY_BREAK_START-$todayKey")
            .putLong("$KEY_TOTAL_BREAK-$todayKey", 0L)
            .commit()
    }

    // --- Break ---
    fun startBreak(timeMillis: Long) {
        prefs.edit().putLong("$KEY_BREAK_START-$todayKey", timeMillis).commit()
    }

    fun getBreakStartMillis(): Long = prefs.getLong("$KEY_BREAK_START-$todayKey", 0L)

    fun endBreak(timeMillis: Long) {
        val breakStart = getBreakStartMillis()
        val duration = if (breakStart > 0) timeMillis - breakStart else 0L
        val totalBreak = getTotalBreakMillis() + duration
        prefs.edit()
            .putLong("$KEY_TOTAL_BREAK-$todayKey", totalBreak)
            .remove("$KEY_BREAK_START-$todayKey")
            .commit()
    }

    fun isOnBreak(): Boolean = getBreakStartMillis() > 0L

    fun getTotalBreakMillis(): Long = prefs.getLong("$KEY_TOTAL_BREAK-$todayKey", 0L)

    fun setTotalBreakMillis(ms: Long) {
        prefs.edit().putLong("$KEY_TOTAL_BREAK-$todayKey", ms).commit()
    }

    // --- Check Out ---
    fun saveCheckOut(timeMillis: Long) {
        saveCheckOutForDate(todayKey, timeMillis)
    }

    fun updateCheckOut(timeMillis: Long) {
        prefs.edit().putLong("$KEY_CHECKOUT-$todayKey", timeMillis).commit()
    }

    fun saveCheckOutForDate(dateKey: String, timeMillis: Long) {
        prefs.edit().putLong("$KEY_CHECKOUT-$dateKey", timeMillis).commit()
    }

    fun hasCheckedOutToday(): Boolean = prefs.getLong("$KEY_CHECKOUT-$todayKey", 0L) > 0L

    // --- Logs ---
    fun addLog(message: String) {
        addLogForDate(todayKey, message)
    }

    fun addLogForDate(dateKey: String, message: String) {
        val logs = getLogsForDate(dateKey).toMutableList()
        logs.add(LogEntry(message, System.currentTimeMillis()))
        prefs.edit().putString("$KEY_LOGS-$dateKey", Gson().toJson(logs)).commit()
    }

    fun updateLogsForDate(dateKey: String, logs: List<LogEntry>) {
        prefs.edit().putString("$KEY_LOGS-$dateKey", Gson().toJson(logs)).commit()
    }

    fun getLogsForToday(): List<LogEntry> = getLogsForDate(todayKey)

    fun getLogsForDate(dateKey: String): List<LogEntry> {
        val json = prefs.getString("$KEY_LOGS-$dateKey", null) ?: return emptyList()
        val type = object : TypeToken<List<LogEntry>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun recalculateDailyStats(dateKey: String) {
        val logs = getLogsForDate(dateKey).sortedBy { it.timestamp }

        var checkInTime: Long = 0
        var checkOutTime: Long = 0
        var totalBreakMs: Long = 0
        var pendingBreakStart: Long = 0
        var manualWorkedMs: Long = 0

        for (log in logs) {
            val msg = log.message
            val time = log.timestamp

            when {
                msg.contains("Punch In") -> checkInTime = time
                msg.contains("Punch Out") -> checkOutTime = time
                msg.contains("Break") && !msg.contains("ended") && !msg.contains("Resumed") && !msg.contains("Resume") -> pendingBreakStart = time
                msg.contains("Resumed Work") || msg.contains("Break ended") || msg.contains("Resume") -> {
                    if (pendingBreakStart > 0) {
                        totalBreakMs += (time - pendingBreakStart)
                        pendingBreakStart = 0
                    }
                }
                msg.contains("Manual Entry:") -> {
                    val workedPart = msg.substringAfter("Worked: ", "")
                    if (workedPart.isNotEmpty()) {
                        manualWorkedMs += parseDurationToMs(workedPart)
                    }
                }
            }
        }

        val edit = prefs.edit()
        edit.putLong("$KEY_CHECKIN-$dateKey", checkInTime)
        edit.putLong("$KEY_CHECKOUT-$dateKey", checkOutTime)
        edit.putLong("$KEY_TOTAL_BREAK-$dateKey", totalBreakMs)

        val sessionWorked = if (checkInTime > 0 && checkOutTime > 0) {
            (checkOutTime - checkInTime - totalBreakMs).coerceAtLeast(0L)
        } else 0L

        // We store the sum of session + manual work as the 'totalWorkedToday' for that day
        val totalToday = sessionWorked + manualWorkedMs
        edit.putLong("total_worked-$dateKey", totalToday)

        // If it's today, update the current shift total worked
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (dateKey == todayStr) {
            edit.putLong(KEY_TOTAL_WORKED_TODAY, totalToday)
            edit.putString(KEY_TOTAL_WORKED_DATE, todayStr)
        }

        edit.commit()
        
        // Finally, ensure the Punch Out log message reflects the correct duration
        if (checkInTime > 0 && checkOutTime > 0) {
            updateCheckoutLogMessage(dateKey)
        }
    }

    private fun updateCheckoutLogMessage(dateKey: String) {
        val checkIn = getCheckInMillisForDate(dateKey)
        val checkOut = getCheckOutMillisForDate(dateKey)
        val totalBreak = getTotalBreakMillisForDate(dateKey)

        if (checkIn > 0 && checkOut > 0) {
            val worked = (checkOut - checkIn - totalBreak).coerceAtLeast(0L)
            val logs = getLogsForDate(dateKey).toMutableList()
            val index = logs.indexOfLast { it.message.contains("Punch Out") }
            if (index != -1) {
                val oldMsg = logs[index].message
                val prefix = oldMsg.substringBefore("|").trim()
                val newMsg = "$prefix | Worked: ${formatDuration(worked)}"
                if (oldMsg != newMsg) {
                    logs[index] = LogEntry(newMsg, logs[index].timestamp)
                    updateLogsForDate(dateKey, logs)
                }
            }
        }
    }

    private fun parseDurationToMs(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].trim().toLong()
                val m = parts[1].trim().toLong()
                val s = parts[2].trim().toLong()
                (h * 3600 + m * 60 + s) * 1000
            } else 0L
        } catch (e: Exception) { 0L }
    }

    fun formatDuration(ms: Long): String {
        var seconds = ms / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
    fun saveTotalWorkedToday(ms: Long) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        prefs.edit().putLong(KEY_TOTAL_WORKED_TODAY, ms)
            .putString(KEY_TOTAL_WORKED_DATE, today)
            .commit()
    }

fun getTotalWorkedToday(): Long {
    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val savedDate = prefs.getString(KEY_TOTAL_WORKED_DATE, "")
    return if (savedDate == today) prefs.getLong(KEY_TOTAL_WORKED_TODAY, 0L) else 0L
}

    // --- Settings ---
    fun setWorkingHours(hours: Float) {
        prefs.edit().putFloat(KEY_WORKING_HOURS, hours).apply()
    }

    fun getWorkingHours(): Float = prefs.getFloat(KEY_WORKING_HOURS, 8.0f) // default to 8 hours

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "User") ?: "User"

    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    // =========================================================
    // --- Employee / ESS data ---
    // =========================================================

    fun saveEmployeeData(
        employeeId: Int,
        employeeCode: String,
        employeeName: String,
        designation: String,
        status: String,
        employmentType: String,
        dateOfJoining: String,
        deviceId: Int,
        companyId: Int,
        departmentId: Int
    ) {
        prefs.edit()
            .putInt(KEY_EMP_ID, employeeId)
            .putString(KEY_EMP_CODE, employeeCode)
            .putString(KEY_EMP_NAME, employeeName)
            .putString(KEY_EMP_DESIG, designation)
            .putString(KEY_EMP_STATUS, status)
            .putString(KEY_EMP_TYPE, employmentType)
            .putString(KEY_EMP_DOJ, dateOfJoining)
            .putInt(KEY_EMP_DEVICE, deviceId)
            .putInt(KEY_EMP_COMPANY, companyId)
            .putInt(KEY_EMP_DEPT, departmentId)
            .putBoolean(KEY_EMP_LOGGED_IN, true)
            .apply()
    }

    fun isEmployeeLoggedIn(): Boolean = prefs.getBoolean(KEY_EMP_LOGGED_IN, false)

    fun getEmployeeId(): Int = prefs.getInt(KEY_EMP_ID, 0)

    fun getEmployeeCode(): String = prefs.getString(KEY_EMP_CODE, "") ?: ""

    fun getEmployeeDisplayName(): String = prefs.getString(KEY_EMP_NAME, "") ?: ""

    fun getEmployeeDesignation(): String = prefs.getString(KEY_EMP_DESIG, "") ?: ""

    fun getEmployeeStatus(): String = prefs.getString(KEY_EMP_STATUS, "") ?: ""

    fun getEmployeeType(): String = prefs.getString(KEY_EMP_TYPE, "") ?: ""

    fun getEmployeeDOJ(): String = prefs.getString(KEY_EMP_DOJ, "") ?: ""

    fun clearEmployeeSession() {
        prefs.edit()
            .remove(KEY_EMP_LOGGED_IN)
            .apply()
    }
}
