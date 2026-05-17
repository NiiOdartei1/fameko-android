package com.example.famekodriver.driver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.famekodriver.driver.map.MapScreen
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)

        // Check status on start
        if (sessionManager.getDriverStatus() != "APPROVED") {
            redirectToDashboard()
            return
        }

        startStatusPolling(sessionManager)
        
        setContent {
            MapScreen(
                onNavigateToProfile = {
                    val intent = Intent(this@MainActivity, DriverProfileActivity::class.java)
                    startActivity(intent)
                },
                onLogout = {
                    sessionManager.logout()
                    val intent = Intent(this@MainActivity, DriverLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            )
        }
    }

    private fun startStatusPolling(sessionManager: SessionManager) {
        val driverId = sessionManager.getDriverId() ?: return
        lifecycleScope.launch {
            while (true) {
                delay(15000) // Poll every 15 seconds for map screen
                repository.getDriverStatus(driverId).onSuccess { response ->
                    if (response.status != "APPROVED") {
                        sessionManager.updateStatus(response.status)
                        redirectToDashboard()
                    }
                }
            }
        }
    }

    private fun redirectToDashboard() {
        val intent = Intent("com.example.famekodriver.OPEN_DASHBOARD")
        intent.setClassName("com.example.famekodriver", "com.example.famekodriver.MainActivity")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }
}
