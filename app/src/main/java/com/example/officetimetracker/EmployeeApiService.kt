package com.example.officetimetracker

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ----------- Response model from /api/user.aspx -----------

data class EmployeeResponse(
    @SerializedName("EmployeeId") val employeeId: Int = 0,
    @SerializedName("EmployeeName") val employeeName: String = "",
    @SerializedName("EmployeeCode") val employeeCode: String = "",
    @SerializedName("Designation") val designation: String = "",
    @SerializedName("Gender") val gender: String = "",
    @SerializedName("DepartmentId") val departmentId: Int = 0,
    @SerializedName("CompanyId") val companyId: Int = 0,
    @SerializedName("Status") val status: String = "",
    @SerializedName("EmployementType") val employmentType: String = "",
    @SerializedName("DOJ") val dateOfJoining: String = "",
    @SerializedName("DeviceId") val deviceId: Int = 0
)

// ----------- Sealed result wrapper -----------

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
    /** Thrown when the server is unreachable (not on office network, timeout, refused). */
    object NetworkError : ApiResult<Nothing>()
}



data class AttLogEntry(
    @SerializedName("DeviceLogId") val deviceLogId: Int = 0,
    @SerializedName("LogDate") val logDate: String = "",
    @SerializedName("Direction") val direction: String = "",
    @SerializedName("UserId") val userId: String = "",
    @SerializedName("DeviceId") val deviceId: Int = 0,
    @SerializedName("DownloadDate") val downloadDate: String = ""
)

// ----------- Service object -----------

object EmployeeApiService {

    private const val BASE_URL = "http://192.168.1.136:85"
    private const val TIMEOUT_MS = 15_000

    /** Returns true for exceptions that indicate the server is simply unreachable. */
    private fun isNetworkException(e: Exception): Boolean = when (e) {
        is java.net.ConnectException,
        is java.net.SocketTimeoutException,
        is java.net.UnknownHostException,
        is java.net.NoRouteToHostException -> true
        else -> false
    }

    /**
     * Validates an employee ID against the ESS API.
     * Returns ApiResult.Success with the EmployeeResponse, or ApiResult.Error with a message.
     */
    suspend fun validateEmployee(empId: String): ApiResult<EmployeeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/user.aspx")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                }

                val body = """{"emp_id":$empId}"""
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext ApiResult.Error("Server returned HTTP $responseCode")
                }

                val responseText = connection.inputStream.bufferedReader().readText()
                val type = object : TypeToken<List<EmployeeResponse>>() {}.type
                val list: List<EmployeeResponse> = Gson().fromJson(responseText, type)

                val employee = list.firstOrNull()
                    ?: return@withContext ApiResult.Error("Employee not found")

                if (employee.status.isBlank() || employee.employeeName.isBlank()) {
                    return@withContext ApiResult.Error("Invalid employee data received")
                }

                ApiResult.Success(employee)
            } catch (e: Exception) {
                if (isNetworkException(e)) ApiResult.NetworkError
                else ApiResult.Error("Connection failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }

    /**
     * Fetches all attendance log entries for a given employee ID from ESS.
     */
    suspend fun getAttendanceLogs(empId: String): ApiResult<List<AttLogEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/att_log.aspx")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                }

                val body = """{"emp_id":$empId}"""
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext ApiResult.Error("Server returned HTTP $responseCode")
                }

                val responseText = connection.inputStream.bufferedReader().readText()
                val type = object : TypeToken<List<AttLogEntry>>() {}.type
                val list: List<AttLogEntry> = Gson().fromJson(responseText, type)

                ApiResult.Success(list)
            } catch (e: Exception) {
                if (isNetworkException(e)) ApiResult.NetworkError
                else ApiResult.Error("Connection failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
}
