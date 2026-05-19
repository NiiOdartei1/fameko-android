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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen {
    object Map : Screen()
    object Wallet : Screen()
    object Settings : Screen()
    data class Chat(val conversationId: Int, val customerName: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private val repository = DriverRepository()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        
        startApprovalPolling(sessionManager)

        setContent {
            var currentStatus by remember { mutableStateOf(sessionManager.getDriverStatus()) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Map) }
            
            val context = LocalContext.current
            var lastBackPressTime by remember { mutableLongStateOf(0L) }

            BackHandler {
                if (currentScreen != Screen.Map) {
                    currentScreen = Screen.Map
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000) {
                        finish()
                    } else {
                        lastBackPressTime = currentTime
                        Toast.makeText(context, "Double tap back to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LaunchedEffect(Unit) {
                while(true) {
                    delay(5000)
                    currentStatus = sessionManager.getDriverStatus()
                }
            }

            if (currentStatus == "APPROVED") {
                when (val screen = currentScreen) {
                    is Screen.Map -> {
                        MapScreen(
                            onNavigateToSettings = {
                                currentScreen = Screen.Settings
                            },
                            onNavigateToChat = { convId, name ->
                                currentScreen = Screen.Chat(convId, name)
                            }
                        )
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            onBack = { currentScreen = Screen.Map },
                            onNavigateToProfile = {
                                val intent = Intent(this@MainActivity, DriverProfileActivity::class.java)
                                startActivity(intent)
                            },
                            onNavigateToWallet = {
                                currentScreen = Screen.Wallet
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
                    is Screen.Wallet -> {
                        WalletScreen(onBack = { currentScreen = Screen.Map })
                    }
                    is Screen.Chat -> {
                        ChatScreen(
                            conversationId = screen.conversationId,
                            customerName = screen.customerName,
                            onBack = { currentScreen = Screen.Map }
                        )
                    }
                }
            } else {
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
                        Text(text = "Status: $currentStatus", 
                            color = if (currentStatus == "SUSPENDED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        
                        if (currentStatus == "SUSPENDED") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your account has been suspended. Please contact support.",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        if (currentStatus != "APPROVED" && currentStatus != "SUSPENDED") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                val intent = Intent(this@MainActivity, DriverProfileActivity::class.java)
                                startActivity(intent)
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Upload Documents")
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = {
                            sessionManager.logout()
                            val intent = Intent(this@MainActivity, DriverLoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }) {
                            Text("Logout")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
                        } else if (response.status == "SUSPENDED") {
                            Toast.makeText(this@MainActivity, "Account Suspended!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
