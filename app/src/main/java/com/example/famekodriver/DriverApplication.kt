package com.example.famekodriver

import android.app.Application
import org.osmdroid.config.Configuration
import androidx.preference.PreferenceManager

class DriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize osmdroid configuration
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName
    }
}
