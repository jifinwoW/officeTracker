package com.example.officetimetracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var empIdLayout: TextInputLayout
    private lateinit var empIdEdit: TextInputEditText
    private lateinit var verifyButton: Button
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // If already logged in, skip straight to main app
        if (sessionManager.isEmployeeLoggedIn()) {
            openMainApp()
            return
        }

        setContentView(R.layout.activity_login)

        empIdLayout = findViewById(R.id.empIdLayout)
        empIdEdit = findViewById(R.id.empIdEdit)
        verifyButton = findViewById(R.id.verifyButton)
        progressIndicator = findViewById(R.id.progressIndicator)
        statusText = findViewById(R.id.statusText)

        // Pre-fill last used ID (if any, e.g. after a data wipe re-entry)
        val lastId = sessionManager.getEmployeeCode()
        if (lastId.isNotEmpty()) empIdEdit.setText(lastId)

        verifyButton.setOnClickListener { attemptVerify() }

        // Allow keyboard "Done" to trigger verify
        empIdEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptVerify()
                true
            } else false
        }
    }

    private fun attemptVerify() {
        val empId = empIdEdit.text.toString().trim()

        if (empId.isEmpty()) {
            empIdLayout.error = "Please enter your Employee ID"
            return
        }
        empIdLayout.error = null

        setLoading(true)
        statusText.text = "Verifying employee ID…"

        lifecycleScope.launch {
            when (val result = EmployeeApiService.validateEmployee(empId)) {
                is ApiResult.Success -> {
                    val employee = result.data
                    sessionManager.saveEmployeeData(
                        employeeId   = employee.employeeId,
                        employeeCode = employee.employeeCode,
                        employeeName = employee.employeeName,
                        designation  = employee.designation,
                        status       = employee.status,
                        employmentType = employee.employmentType,
                        dateOfJoining  = employee.dateOfJoining,
                        deviceId       = employee.deviceId,
                        companyId      = employee.companyId,
                        departmentId   = employee.departmentId
                    )
                    // Also seed the display name in the greeting key
                    sessionManager.setUserName(employee.employeeName)

                    Toast.makeText(
                        this@LoginActivity,
                        "Welcome, ${employee.employeeName}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    openMainApp()
                }

                is ApiResult.Error -> {
                    setLoading(false)
                    statusText.text = ""
                    empIdLayout.error = result.message
                }

                is ApiResult.NetworkError -> {
                    setLoading(false)
                    statusText.text = ""
                    empIdLayout.error = null
                    showNetworkErrorDialog()
                }
            }
        }
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showNetworkErrorDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Server Unreachable")
            .setMessage("Unable to connect to the office server.\n\nPlease make sure you are connected to the office Wi-Fi network and try again.")
            .setPositiveButton("Retry") { _, _ -> attemptVerify() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        verifyButton.isEnabled = !loading
        empIdEdit.isEnabled = !loading
        progressIndicator.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
