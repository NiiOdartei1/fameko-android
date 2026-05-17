package com.example.famekodriver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository = DriverRepository()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        
        if (sessionManager.getDriverStatus() == "APPROVED") {
            if (navigateToMap()) return
        }
        
        startApprovalPolling(sessionManager)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "Fameko Driver", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Welcome, ${sessionManager.getDriverName()}", fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(text = "Status: ${sessionManager.getDriverStatus()}", 
                        color = if (sessionManager.getDriverStatus() == "SUSPENDED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    
                    if (sessionManager.getDriverStatus() == "SUSPENDED") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your account has been suspended. Please contact support.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    if (sessionManager.getDriverStatus() != "APPROVED" && sessionManager.getDriverStatus() != "SUSPENDED") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val intent = Intent(this@MainActivity, DriverProfileActivity::class.java)
                            startActivity(intent)
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Upload Documents")
                        }
                    }

                    Spacer(modifier = Modifier.height(64.dp))
                    Text(text = "Please run the 'app-driver' module for the full Map experience.", color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        sessionManager.logout()
                        finish()
                    }) {
                        Text("Logout")
                    }
                }
            }
        }
    }

    private fun navigateToMap(): Boolean {
        return try {
            android.util.Log.d("FamekoNav", "Attempting to navigate to Map...")
            val intent = Intent("com.example.famekodriver.driver.OPEN_MAP")
            intent.setClassName("com.example.famekodriver.driver", "com.example.famekodriver.driver.MainActivity")
            
            // Pass session info to the other app
            intent.putExtra("driver_id", sessionManager.getDriverId())
            intent.putExtra("driver_name", sessionManager.getDriverName())
            intent.putExtra("driver_status", sessionManager.getDriverStatus())

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            android.util.Log.d("FamekoNav", "Navigation intent sent successfully")
            finish()
            true
        } catch (e: Exception) {
            android.util.Log.e("FamekoNav", "Navigation failed: ${e.message}", e)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.getDriverStatus() == "APPROVED") {
            navigateToMap()
        }
    }

    private fun startApprovalPolling(sessionManager: SessionManager) {
        val driverId = sessionManager.getDriverId() ?: return

        lifecycleScope.launch {
            while (true) {
                delay(10000) // Poll every 10 seconds
                repository.getDriverStatus(driverId).onSuccess { response ->
                    val oldStatus = sessionManager.getDriverStatus()
                    if (response.status != oldStatus) {
                        sessionManager.updateStatus(response.status)
                        
                        if (response.status == "APPROVED") {
                            Toast.makeText(this@MainActivity, "Account Approved!", Toast.LENGTH_LONG).show()
                            if (!navigateToMap()) {
                                recreate()
                            }
                        } else if (response.status == "SUSPENDED") {
                            Toast.makeText(this@MainActivity, "Account Suspended!", Toast.LENGTH_LONG).show()
                            recreate()
                        } else {
                            recreate()
                        }
                    }
                }
            }
        }
    }
}
