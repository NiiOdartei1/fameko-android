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

        // Adopt session from Intent if provided (from the other module)
        val intentDriverId = intent.getStringExtra("driver_id")
        if (intentDriverId != null) {
            val name = intent.getStringExtra("driver_name") ?: "Driver"
            val status = intent.getStringExtra("driver_status") ?: "PENDING"
            sessionManager.saveSession(intentDriverId, name, status)
        }

        // Check if logged in. If not, go to login.
        if (!sessionManager.isLoggedIn()) {
            val intent = Intent(this, DriverLoginActivity::class.java)
            startActivity(intent)
            finish()
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
                    if (response.status != sessionManager.getDriverStatus()) {
                        sessionManager.updateStatus(response.status)
                        // No redirect needed - MapScreen UI will update via SessionManager or internal state
                    }
                }
            }
        }
    }
}
