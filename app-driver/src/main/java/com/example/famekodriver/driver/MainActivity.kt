package com.example.famekodriver.driver

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import com.example.famekodriver.driver.map.MapScreen
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)

        if (!sessionManager.isLoggedIn()) {
            val intent = Intent(this, DriverLoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        startStatusPolling(sessionManager)
        
        setContent {
            var currentStatus by remember { mutableStateOf(sessionManager.getDriverStatus()) }
            
            // Local polling to update UI immediately
            LaunchedEffect(Unit) {
                while(true) {
                    delay(5000)
                    currentStatus = sessionManager.getDriverStatus()
                }
            }

            if (currentStatus == "APPROVED") {
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
            } else {
                DriverDashboardScreen(
                    name = sessionManager.getDriverName() ?: "Driver",
                    status = currentStatus,
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
    }

    private fun startStatusPolling(sessionManager: SessionManager) {
        val driverId = sessionManager.getDriverId() ?: return
        lifecycleScope.launch {
            while (true) {
                delay(10000)
                repository.getDriverStatus(driverId).onSuccess { response ->
                    if (response.status != sessionManager.getDriverStatus()) {
                        sessionManager.updateStatus(response.status)
                    }
                }
            }
        }
    }
}

@Composable
fun DriverDashboardScreen(
    name: String,
    status: String,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Fameko Driver", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Registration Status", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Gray)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(text = "Welcome, $name", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val statusColor = when(status) {
                "APPROVED" -> androidx.compose.ui.graphics.Color(0xFF28A745)
                "SUSPENDED" -> MaterialTheme.colorScheme.error
                else -> androidx.compose.ui.graphics.Color(0xFFF08C00)
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (status == "SUSPENDED") {
                Text(
                    text = "Your account has been suspended.\nPlease contact support for assistance.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            } else if (status != "APPROVED") {
                Text(
                    text = if (status == "PENDING_DOCS") 
                        "Please upload the required documents to proceed." 
                        else "Your documents are being reviewed. We will notify you once approved.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onNavigateToProfile,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Upload Documents")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Text("Logout")
            }
        }
    }
}
