package com.example.officetimetracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment() {

    private lateinit var userNameEdit: TextInputEditText
    private lateinit var workingHoursEdit: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var clearDataButton: Button
    private lateinit var switchEmployeeButton: Button
    private lateinit var sessionManager: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        sessionManager = SessionManager(requireContext())

        userNameEdit = view.findViewById(R.id.userNameEdit)
        workingHoursEdit = view.findViewById(R.id.workingHoursEdit)
        saveButton = view.findViewById(R.id.saveSettingsButton)
        clearDataButton = view.findViewById(R.id.clearDataButton)
        switchEmployeeButton = view.findViewById(R.id.switchEmployeeButton)

        // Load current values
        userNameEdit.setText(sessionManager.getEmployeeDisplayName().ifEmpty { sessionManager.getUserName() })
        workingHoursEdit.setText(sessionManager.getWorkingHours().toString())

        saveButton.setOnClickListener { saveSettings() }
        clearDataButton.setOnClickListener { showClearDataConfirmation() }
        switchEmployeeButton.setOnClickListener { showSwitchEmployeeConfirmation() }

        return view
    }

    private fun saveSettings() {
        val hoursStr = workingHoursEdit.text.toString().trim()

        val hours = hoursStr.toFloatOrNull()
        if (hours == null || hours <= 0 || hours > 24) {
            workingHoursEdit.error = "Enter valid hours (1-24)"
            return
        }

        sessionManager.setWorkingHours(hours)
        Toast.makeText(requireContext(), "Preferences updated", Toast.LENGTH_SHORT).show()
    }

    private fun showSwitchEmployeeConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Switch Employee?")
            .setMessage("You will be signed out and taken to the login screen.")
            .setPositiveButton("Sign Out") { _, _ ->
                sessionManager.clearEmployeeSession()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearDataConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Application?")
            .setMessage("All your logs and settings will be permanently removed. This cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                sessionManager.clearAllData()
                Toast.makeText(requireContext(), "Data wiped", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
