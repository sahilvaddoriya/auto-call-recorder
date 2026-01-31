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
        val statusLabel = findViewById<TextView>(R.id.statusLabel)
        val statusCaption = findViewById<TextView>(R.id.statusCaption)
        
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

        // Battery Optimization Logic
        val btnBatteryOptimizations = findViewById<Button>(R.id.btnBatteryOptimizations)
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            btnBatteryOptimizations.visibility = android.view.View.GONE
        } else {
            btnBatteryOptimizations.visibility = android.view.View.VISIBLE
            btnBatteryOptimizations.setOnClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        
        // Re-check battery optimization status
        val btnBatteryOptimizations = findViewById<Button>(R.id.btnBatteryOptimizations)
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            btnBatteryOptimizations.visibility = android.view.View.GONE
        }
    }

    private fun checkPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 101)
        }
    }

    private fun updateServiceStatus() {
        val isServiceEnabled = isAccessibilityServiceEnabled(CallRecorderService::class.java)
        val statusLabel = findViewById<TextView>(R.id.statusLabel)
        val statusCaption = findViewById<TextView>(R.id.statusCaption)
        
        if (isServiceEnabled) {
            statusText.text = "ACTIVE"
            val containerColor = com.google.android.material.color.MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorPrimaryContainer)
            val contentColor = com.google.android.material.color.MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorOnPrimaryContainer)
            
            statusCard.setCardBackgroundColor(containerColor)
            statusText.setTextColor(contentColor)
            statusLabel.setTextColor(contentColor)
            statusCaption.setTextColor(contentColor)
        } else {
            statusText.text = "INACTIVE"
            val containerColor = com.google.android.material.color.MaterialColors.getColor(statusCard, com.google.android.material.R.attr.colorErrorContainer)
            val contentColor = com.google.android.material.color.MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorOnErrorContainer)
            
            statusCard.setCardBackgroundColor(containerColor)
            statusText.setTextColor(contentColor)
            statusLabel.setTextColor(contentColor)
            statusCaption.setTextColor(contentColor)
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
