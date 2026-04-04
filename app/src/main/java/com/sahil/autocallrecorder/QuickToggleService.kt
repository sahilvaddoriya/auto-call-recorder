package com.sahil.autocallrecorder

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityManager

/**
 * A Quick Settings Tile that allows the user to quickly jump to the Accessibility settings
 * for this app. This is necessary because banking apps like SBI Yono block access
 * if ANY custom Accessibility Service is enabled.
 */
class QuickToggleService : TileService() {

    private val accessibilityStateChangeListener = AccessibilityManager.AccessibilityStateChangeListener {
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.addAccessibilityStateChangeListener(accessibilityStateChangeListener)
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.removeAccessibilityStateChangeListener(accessibilityStateChangeListener)
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.contains("CallRecorderService") }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val running = isServiceRunning()
        
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (running) "Recorder: ON" else "Recorder: OFF"
        tile.subtitle = if (running) "Tap to Disable" else "Tap to Enable"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val running = isServiceRunning()
        
        if (running) {
            Log.d("QuickToggleService", "Service is ON. Sending Disable Broadcast for SBI Yono workaround.")
            // 1. Send broadcast to the service to disable itself
            val intent = Intent("com.sahil.autocallrecorder.DISABLE_SERVICE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            // 2. Update UI immediately
            val tile = qsTile
            if (tile != null) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Recorder: OFF"
                tile.updateTile()
            }
        } else {
            Log.d("QuickToggleService", "Service is OFF. Opening Accessibility Settings via PendingIntent.")
            // Open Accessibility Settings for our specific service
            val targetIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            // Use PendingIntent for Android 14+ compatibility
            val pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                targetIntent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(pendingIntent)
                } else {
                    // Legacy support
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(targetIntent)
                }
            } catch (e: Exception) {
                Log.e("QuickToggleService", "Failed to open settings", e)
            }
        }
    }
}
