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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        
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
                    Text(text = "Status: ${sessionManager.getDriverStatus()}", color = MaterialTheme.colorScheme.primary)
                    
                    if (sessionManager.getDriverStatus() != "APPROVED") {
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

    private fun startApprovalPolling(sessionManager: SessionManager) {
        val driverId = sessionManager.getDriverId() ?: return
        if (sessionManager.getDriverStatus() == "APPROVED") return

        lifecycleScope.launch {
            while (true) {
                delay(10000) // Poll every 10 seconds
                repository.getDriverStatus(driverId).onSuccess { response ->
                    if (response.status == "APPROVED") {
                        sessionManager.updateStatus("APPROVED")
                        Toast.makeText(this@MainActivity, "Account Approved!", Toast.LENGTH_LONG).show()
                        
                        // Try to redirect to the Map Screen in the other module
                        try {
                            val intent = Intent()
                            intent.setClassName(this@MainActivity, "com.example.famekodriver.driver.MainActivity")
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            // Fallback if not in same process/module correctly during debug
                            recreate() 
                        }
                    }
                }
            }
        }
    }
}
