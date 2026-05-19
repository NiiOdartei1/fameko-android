package com.example.famekodriver

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToWallet: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val repository = remember { DriverRepository() }
    val scope = rememberCoroutineScope()
    
    var isOnline by remember { mutableStateOf(sessionManager.isOnline()) }
    var driverStats by remember { mutableStateOf(com.example.famekodriver.core.domain.model.DriverStats()) }
    val driverId = sessionManager.getDriverId() ?: ""

    LaunchedEffect(Unit) {
        if (driverId.isNotEmpty()) {
            repository.getDriverStats(driverId).onSuccess { stats -> driverStats = stats }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Status") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Availability Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFAFAFA)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isOnline) "Status: Online" else "Status: Offline",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isOnline) Color(0xFF2E7D32) else Color.Gray
                        )
                        Text(
                            text = if (isOnline) "You are visible to customers" else "You are not receiving orders",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isOnline,
                        onCheckedChange = { checked ->
                            isOnline = checked
                            sessionManager.setOnline(checked)
                            scope.launch {
                                repository.updateOnlineStatus(driverId, checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF28A745),
                            checkedTrackColor = Color(0xFF28A745).copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // Quick Stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TODAY", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("₵${String.format(Locale.getDefault(), "%.2f", driverStats.earningsToday)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF28A745))
                    }
                    VerticalDivider(modifier = Modifier.height(30.dp).padding(horizontal = 16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RATING", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.getDefault(), "%.1f", driverStats.rating)} ⭐", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB400))
                    }
                }
            }

            Text("Management", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)

            SettingsItem(
                icon = Icons.Default.Person,
                title = "Profile & Documents",
                subtitle = "Manage your information",
                onClick = onNavigateToProfile
            )

            SettingsItem(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Wallet",
                subtitle = "Earnings and transactions",
                onClick = onNavigateToWallet
            )

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notification Settings",
                subtitle = "Manage alerts",
                onClick = { /* TODO */ }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF004E89), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}
