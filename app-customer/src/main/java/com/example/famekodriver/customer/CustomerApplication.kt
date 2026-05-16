package com.example.famekodriver.customer

import android.app.Application
import java.io.File

class CustomerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize osmdroid configuration
        val ctx = applicationContext
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        
        org.osmdroid.config.Configuration.getInstance().load(ctx, prefs)
        
        // Use internal storage for tile cache to avoid permission issues on newer Android versions
        val basePath = File(ctx.cacheDir, "osmdroid")
        org.osmdroid.config.Configuration.getInstance().osmdroidBasePath = basePath
        val tileCache = File(basePath, "tiles")
        org.osmdroid.config.Configuration.getInstance().osmdroidTileCache = tileCache

        // User agent is required for OSM tile servers
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
    }
}
