package com.example.famekodriver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.famekodriver.driver.map.MapScreen
import com.example.famekodriver.core.data.SessionManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MapScreen(
                onNavigateToProfile = {
                    // Navigate to DriverProfileActivity in this module
                    val intent = Intent(this@MainActivity, DriverProfileActivity::class.java)
                    startActivity(intent)
                },
                onLogout = {
                    val sessionManager = SessionManager(this@MainActivity)
                    sessionManager.logout()
                    val intent = Intent(this@MainActivity, DriverLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}
