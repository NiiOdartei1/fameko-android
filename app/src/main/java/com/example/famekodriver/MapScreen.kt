package com.example.famekodriver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.material.icons.filled.NotificationsActive
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.famekodriver.core.domain.model.HeatmapPoint
import com.example.famekodriver.core.domain.model.SurgeInfo
import org.osmdroid.views.overlay.Polygon
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import java.util.Locale
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.example.famekodriver.core.domain.model.Delivery
import com.example.famekodriver.core.domain.model.DeliveryStatus
import com.example.famekodriver.core.domain.model.FamekoEvent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.famekodriver.core.domain.model.DriverStats
import com.example.famekodriver.core.domain.model.RouteLocation
import com.example.famekodriver.core.domain.model.RouteRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToChat: (Int, String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DriverRepository() }
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var isOnline by remember { mutableStateOf(sessionManager.isOnline()) }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var activeRequest by remember { mutableStateOf<Delivery?>(null) }
    var currentDelivery by remember { mutableStateOf<Delivery?>(null) }
    var incomingCall by remember { mutableStateOf<FamekoEvent.IncomingCall?>(null) }
    var isAccepting by remember { mutableStateOf(false) }
    var driverStats by remember { mutableStateOf(DriverStats()) }
    var driverStatus by remember { mutableStateOf(sessionManager.getDriverStatus()) }
    var navigationPath by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var driverLatLng by remember { mutableStateOf<GeoPoint?>(null) }
    var heatmapPoints by remember { mutableStateOf<List<HeatmapPoint>>(emptyList()) }
    var currentSurge by remember { mutableStateOf<SurgeInfo?>(null) }

    // Sound Management
    val ringtone = remember {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        RingtoneManager.getRingtone(context, uri)
    }

    val notificationSound = remember {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)
    }

    LaunchedEffect(activeRequest, incomingCall) {
        if (activeRequest != null || incomingCall != null) {
            ringtone?.play()
        } else {
            ringtone?.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ringtone?.stop()
        }
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            repository.getHeatmapData().onSuccess { points ->
                heatmapPoints = points
            }
            repository.getCurrentSurge().onSuccess { surge ->
                currentSurge = surge
            }
        } else {
            heatmapPoints = emptyList()
            currentSurge = null
        }
    }

    LaunchedEffect(isOnline, currentDelivery) {
        val intent = Intent(context, LocationService::class.java)
        if (isOnline || currentDelivery != null) {
            intent.action = LocationService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    LaunchedEffect(isOnline) {
        val driverId = sessionManager.getDriverId() ?: return@LaunchedEffect
        sessionManager.setOnline(isOnline)
        scope.launch {
            repository.updateOnlineStatus(driverId, isOnline)
        }
        
        if (isOnline) {
            repository.startWebSocket(driverId)
        } else {
            repository.stopWebSocket()
            activeRequest = null
        }
    }

    LaunchedEffect(Unit) {
        repository.events.collect { event ->
            when (event) {
                is FamekoEvent.NewDeliveryRequest -> {
                    if (currentDelivery == null) {
                        activeRequest = event.delivery
                    }
                }
                is FamekoEvent.NewMessage -> {
                    // Show notification or update chat UI
                }
                is FamekoEvent.IncomingCall -> {
                    incomingCall = event
                }
                is FamekoEvent.CallEnded, is FamekoEvent.CallRejected -> {
                    incomingCall = null
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(hasLocationPermission, isOnline, currentDelivery) {
        if (hasLocationPermission) {
            val driverId = sessionManager.getDriverId() ?: "DRIVER-1"
            while (isActive) {
                try {
                    if (isOnline || currentDelivery != null) {
                        @SuppressLint("MissingPermission")
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                location?.let { loc ->
                                    driverLatLng = GeoPoint(loc.latitude, loc.longitude)
                                    scope.launch {
                                        repository.updateLocation(driverId, loc.latitude, loc.longitude, loc.bearing)
                                    }
                                }
                            }
                    }
                    
                    repository.getDriverStatus(driverId).onSuccess { 
                        driverStatus = it.status
                        sessionManager.updateStatus(it.status)
                    }
                    repository.getDriverStats(driverId).onSuccess { stats -> driverStats = stats }
                    
                    repository.getMyDeliveries(driverId).onSuccess { myDeliveries ->
                        currentDelivery = if (myDeliveries.isNotEmpty()) {
                            myDeliveries.firstOrNull { it.status != DeliveryStatus.DELIVERED && it.status != DeliveryStatus.CANCELLED }
                        } else {
                            null
                        }
                    }

                    if (isOnline && currentDelivery == null && activeRequest == null) {
                        repository.getAvailableDeliveries().onSuccess { deliveries ->
                            if (deliveries.isNotEmpty()) {
                                activeRequest = deliveries.first()
                            }
                        }
                    }
                    
                    // Faster updates when on a delivery (3s) vs idle (10s)
                    val pollInterval = if (currentDelivery != null) 3000L else 10000L
                    delay(pollInterval) 
                } catch (e: Exception) {
                    Log.e("MapScreen", "Error in background loop", e)
                    delay(5000)
                }
            }
        }
    }

    LaunchedEffect(currentDelivery, driverLatLng) {
        if (currentDelivery != null && driverLatLng != null) {
            val destination = if (currentDelivery!!.status == DeliveryStatus.ASSIGNED) {
                GeoPoint(currentDelivery!!.pickupLat ?: 0.0, currentDelivery!!.pickupLng ?: 0.0)
            } else {
                GeoPoint(currentDelivery!!.dropoffLat ?: 0.0, currentDelivery!!.dropoffLng ?: 0.0)
            }

            if (destination.latitude != 0.0) {
                repository.calculateRoute(
                    RouteRequest(
                        start = RouteLocation(driverLatLng!!.latitude, driverLatLng!!.longitude),
                        end = RouteLocation(destination.latitude, destination.longitude)
                    )
                ).onSuccess { response ->
                    navigationPath = response.routeCoords.map { coords -> GeoPoint(coords[1], coords[0]) }
                }
            }
        } else {
            navigationPath = emptyList()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Delivery Map") },
                actions = {
                    IconButton(onClick = onNavigateToWallet) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                // SOS Button - Only show when Online or on a Delivery
                if (isOnline || currentDelivery != null) {
                    FloatingActionButton(
                        onClick = {
                            @SuppressLint("MissingPermission")
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let { loc ->
                                    scope.launch {
                                        val driverId = sessionManager.getDriverId() ?: "DRIVER-1"
                                        repository.triggerSOS(driverId, loc.latitude, loc.longitude).onSuccess {
                                            Toast.makeText(context, "SOS ALERT SENT! Help is on the way.", Toast.LENGTH_LONG).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Failed to send SOS. Call emergency services!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        containerColor = Color.Red,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "SOS")
                    }
                }

                if (hasLocationPermission && activeRequest == null) {
                    FloatingActionButton(
                        onClick = {
                            @SuppressLint("MissingPermission")
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let { loc ->
                                    mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.0, 1000L)
                                }
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFFFF6B35)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(5.6037, -0.1870))
                        
                        if (hasLocationPermission) {
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            locationOverlay.enableMyLocation()
                            overlays.add(locationOverlay)
                        }
                        mapView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    mv.overlays.removeAll { overlay -> overlay !is MyLocationNewOverlay }
                    
                    // Render Heatmap (Circles)
                    heatmapPoints.forEach { point ->
                        val circle = Polygon(mv)
                        circle.points = Polygon.pointsAsCircle(GeoPoint(point.latitude, point.longitude), 300.0) // 300m radius
                        circle.fillPaint.color = Color(1f, 0f, 0f, point.intensity.toFloat()).toArgb() // Red with intensity alpha
                        circle.outlinePaint.strokeWidth = 0f
                        mv.overlays.add(circle)
                    }

                    if (navigationPath.isNotEmpty()) {
                        val line = Polyline(mv)
                        line.setPoints(navigationPath)
                        line.outlinePaint.color = "#004E89".toColorInt()
                        line.outlinePaint.strokeWidth = 12f
                        mv.overlays.add(line)
                        
                        val target = navigationPath.last()
                        val marker = Marker(mv)
                        marker.position = target
                        marker.title = if (currentDelivery?.status == DeliveryStatus.ASSIGNED) "Pickup" else "Destination"
                        mv.overlays.add(marker)
                    }
                    mv.invalidate()
                }
            )

            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (driverStatus != "APPROVED") {
                    RegistrationNotice(status = driverStatus, onGoToProfile = onNavigateToProfile)
                }

                currentSurge?.let { surge ->
                    if (surge.isActive) {
                        Surface(
                            color = Color(0xFFFF6B35),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${surge.multiplier}x Surge Active!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TODAY'S EARNINGS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("₵${String.format(Locale.getDefault(), "%.2f", driverStats.earningsToday)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF28A745))
                        }
                        VerticalDivider(modifier = Modifier.height(30.dp).padding(horizontal = 16.dp), color = Color.LightGray)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RATING", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.getDefault(), "%.1f", driverStats.rating)} ⭐", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFB400))
                        }
                    }
                }

                // In-App Navigation HUD
                if (navigationPath.isNotEmpty() && currentDelivery != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF004E89),
                        contentColor = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (currentDelivery!!.status == DeliveryStatus.ASSIGNED) "Heading to Pickup" else "Heading to Drop-off",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = if (currentDelivery!!.status == DeliveryStatus.ASSIGNED) currentDelivery!!.pickupLocation else currentDelivery!!.dropoffLocation,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.getDefault(), "%.1f", currentDelivery!!.distanceKm)} km",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text("Remaining", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }

            currentDelivery?.let { delivery ->
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                val statusText = when(delivery.status) {
                                    DeliveryStatus.ASSIGNED -> "GOING TO PICKUP"
                                    DeliveryStatus.IN_TRANSIT -> "TRIP IN PROGRESS"
                                    else -> delivery.status.name
                                }
                                Text(statusText, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35), fontSize = 12.sp)
                                Text(delivery.customerName ?: "Customer", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Row {
                                    IconButton(
                                        onClick = {
                                            val phone = delivery.customerPhone ?: ""
                                            if (phone.isNotEmpty()) {
                                                val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Call, "Call Customer", tint = Color(0xFF28A745))
                                    }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        onNavigateToChat(delivery.orderId, delivery.customerName ?: "Customer")
                                    },
                                    modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, "Chat", tint = Color(0xFF004E89))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val driverId = sessionManager.getDriverId() ?: ""
                                        scope.launch {
                                            repository.getShareableTripLink(driverId, delivery.id).onSuccess { response ->
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "Track my Fameko delivery: ${response.shareUrl}")
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share Trip"))
                                            }
                                        }
                                    },
                                    modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape)
                                ) {
                                    Icon(Icons.Default.Share, "Share Trip", tint = Color(0xFF004E89))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            val lat = if (delivery.status == DeliveryStatus.ASSIGNED) delivery.pickupLat else delivery.dropoffLat
                                            val lng = if (delivery.status == DeliveryStatus.ASSIGNED) delivery.pickupLng else delivery.dropoffLng
                                            val gmmIntentUri = "google.navigation:q=$lat,$lng".toUri()
                                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                            mapIntent.setPackage("com.google.android.apps.maps")
                                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                context.startActivity(mapIntent)
                                            } else {
                                                val webIntent = Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng".toUri())
                                                context.startActivity(webIntent)
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFFF0F0F0), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Navigation, "Navigate", tint = Color(0xFF004E89))
                                    }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val locationIcon = if (delivery.status == DeliveryStatus.ASSIGNED) Icons.Default.MyLocation else Icons.Default.Navigation
                            val locationLabel = if (delivery.status == DeliveryStatus.ASSIGNED) "Pickup" else "Destination"
                            val address = if (delivery.status == DeliveryStatus.ASSIGNED) delivery.pickupLocation else delivery.dropoffLocation
                            Icon(locationIcon, null, tint = if (delivery.status == DeliveryStatus.ASSIGNED) Color(0xFF34D186) else Color(0xFFDC3545), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(locationLabel, fontSize = 11.sp, color = Color.Gray)
                                Text(address, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        val buttonText = when(delivery.status) {
                            DeliveryStatus.ASSIGNED -> "ARRIVED AT PICKUP"
                            DeliveryStatus.IN_TRANSIT -> "COMPLETE TRIP"
                            else -> "CONTINUE"
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val nextStatus = when(delivery.status) {
                                        DeliveryStatus.ASSIGNED -> DeliveryStatus.IN_TRANSIT
                                        DeliveryStatus.IN_TRANSIT -> DeliveryStatus.DELIVERED
                                        else -> delivery.status
                                    }
                                    repository.updateDeliveryStatus(delivery.id, nextStatus).onSuccess {
                                        Toast.makeText(context, "Status Updated!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004E89))
                        ) {
                            Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            activeRequest?.let { delivery ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                    Card(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New Delivery Request", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("GHS ${String.format(Locale.getDefault(), "%.2f", delivery.estimatedEarnings)}", fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Color(0xFF28A745))
                                    Text("Estimated Earnings", fontSize = 12.sp, color = Color.Gray)
                                }
                                Surface(color = Color(0xFFFFEAD1), shape = RoundedCornerShape(12.dp)) {
                                    Text("${String.format(Locale.getDefault(), "%.1f", delivery.distanceKm)} km", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = Color(0xFFE67E22))
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MyLocation, "Pickup", tint = Color(0xFF34D186), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(delivery.pickupLocation, fontSize = 14.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Navigation, "Drop-off", tint = Color(0xFFDC3545), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(delivery.dropoffLocation, fontSize = 14.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { activeRequest = null }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)) {
                                    Text("Ignore", color = Color.Gray)
                                }
                                Button(
                                    onClick = {
                                        isAccepting = true
                                        scope.launch {
                                            val driverId = sessionManager.getDriverId() ?: "DRIVER-1"
                                            repository.acceptDelivery(driverId, delivery.id).onSuccess {
                                                isAccepting = false
                                                Toast.makeText(context, "Request Accepted!", Toast.LENGTH_SHORT).show()
                                            }.onFailure { error ->
                                                isAccepting = false
                                                activeRequest = null
                                                Toast.makeText(context, "Failed to accept: ${error.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f).height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                                    enabled = !isAccepting
                                ) {
                                    if (isAccepting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text("ACCEPT", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (currentDelivery == null) {
                Button(
                    onClick = { isOnline = !isOnline },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (activeRequest != null) 420.dp else 32.dp)
                        .height(64.dp)
                        .fillMaxWidth(0.6f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) Color(0xFFDC3545) else Color(0xFF28A745)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        if (isOnline) "GO OFFLINE" else "GO ONLINE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            incomingCall?.let { call ->
                AlertDialog(
                    onDismissRequest = { /* Don't dismiss by clicking outside */ },
                    title = { Text("Incoming Call") },
                    text = { Text("Customer ${call.callerName} is calling you...") },
                    confirmButton = {
                        Button(
                            onClick = {
                                incomingCall = null
                                // Accept logic here (navigate to call screen)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745))
                        ) {
                            Icon(Icons.Default.Call, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Accept")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                incomingCall = null
                                // Reject logic here
                            }
                        ) {
                            Text("Reject", color = Color.Red)
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun RegistrationNotice(status: String, onGoToProfile: () -> Unit) {
    val isSuspended = status == "SUSPENDED"
    val containerColor = if (isSuspended) Color(0xFFF8D7DA) else Color(0xFFFFF3CD)
    val contentColor = if (isSuspended) Color(0xFF721C24) else Color(0xFF856404)
    val borderColor = if (isSuspended) Color(0xFFF5C6CB) else Color(0xFF856404)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when(status) {
                    "PENDING_DOCS" -> "Documents Required"
                    "SUSPENDED" -> "Account Suspended"
                    else -> "Account Pending Approval"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when(status) {
                    "PENDING_DOCS" -> "Please upload required documents in your Profile to start receiving orders."
                    "SUSPENDED" -> "Your account has been suspended. Please contact support for more information."
                    else -> "Your documents are under review. You'll be notified once approved."
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            if (status == "PENDING_DOCS") {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onGoToProfile,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = contentColor)
                ) {
                    Text("Go to Profile", fontSize = 12.sp)
                }
            }
        }
    }
}
