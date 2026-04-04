package com.sahil.autocallrecorder

import android.app.Application
import com.google.android.material.color.DynamicColors

class AutoCallRecorderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply dynamic colors to all activities in the app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
