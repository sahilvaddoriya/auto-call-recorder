package com.example.autocallrecorder

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusCard: MaterialCardView
    private lateinit var automationSwitch: MaterialSwitch
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "CallRecorderPrefs"
        const val KEY_AUTOMATION_ENABLED = "automation_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        statusText = findViewById(R.id.statusText)
        statusCard = findViewById(R.id.statusCard)
        automationSwitch = findViewById(R.id.automationSwitch)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)

        // Setup Toggle
        val isEnabled = prefs.getBoolean(KEY_AUTOMATION_ENABLED, true)
        automationSwitch.isChecked = isEnabled
        automationSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTOMATION_ENABLED, isChecked).apply()
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        
        // Status updates on card click as well
        statusCard.setOnClickListener {
             startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun checkPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 101)
        }
    }

    private fun updateServiceStatus() {
        val isServiceEnabled = isAccessibilityServiceEnabled(CallRecorderService::class.java)
        
        if (isServiceEnabled) {
            statusText.text = "ACTIVE"
            val activeColor = com.google.android.material.color.MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorPrimary)
            val containerColor = com.google.android.material.color.MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorPrimaryContainer)
            
            statusText.setTextColor(activeColor)
            statusCard.setCardBackgroundColor(containerColor)
        } else {
            statusText.text = "INACTIVE"
            val errorColor = com.google.android.material.color.MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorError)
            // val containerColor = com.google.android.material.color.MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorErrorContainer)
            
            statusText.setTextColor(errorColor)
            // statusCard.setCardBackgroundColor(containerColor)
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == packageName && enabledServiceInfo.name == service.name) {
                return true
            }
        }
        return false
    }
}
