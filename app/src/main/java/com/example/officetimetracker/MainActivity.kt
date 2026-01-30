package com.example.officetimetracker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var sessionManager: SessionManager
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sessionManager = SessionManager(this)

        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { item ->
            val fragment = when(item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_reports -> ReportFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> null
            }
            
            fragment?.let { 
                switchFragment(it, item.itemId)
                true 
            } ?: false
        }

        // Initial fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, DashboardFragment())
            .commit()

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Authentication succeeded", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Confirm identity to access")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun switchFragment(fragment: Fragment, itemId: Int) {
        val currentId = bottomNavigationView.selectedItemId
        if (currentId == itemId) return

        val transaction = supportFragmentManager.beginTransaction()

        // Determine transition direction
        when {
            // Dashboard is the leftmost tab (1st)
            itemId == R.id.nav_dashboard -> {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            // Settings is the rightmost tab (3rd)
            itemId == R.id.nav_settings -> {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            // Reports is the middle tab (2nd)
            itemId == R.id.nav_reports -> {
                if (currentId == R.id.nav_dashboard) {
                    // Moving from left to right
                    transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                } else {
                    // Moving from right to left (from Settings)
                    transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            }
        }

        transaction.replace(R.id.fragmentContainer, fragment).commit()
    }
}
