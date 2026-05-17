package com.example.famekodriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.famekodriver.core.data.SessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        
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
}
