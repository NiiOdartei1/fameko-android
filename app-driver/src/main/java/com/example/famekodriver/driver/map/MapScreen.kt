package com.example.famekodriver.driver.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import java.util.Locale
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.example.famekodriver.core.domain.model.Delivery
import com.example.famekodriver.core.domain.model.DeliveryStatus
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DriverRepository() }
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
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

    // MapView reference
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // State for incoming request and active delivery
    var activeRequest by remember { mutableStateOf<Delivery?>(null) }
    var currentDelivery by remember { mutableStateOf<Delivery?>(null) }
    var isAccepting by remember { mutableStateOf(false) }
    
    // Stats for overlay
    var driverStats by remember { mutableStateOf(DriverStats()) }
    
    // Navigation polyline
    var navigationPath by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var driverLatLng by remember { mutableStateOf<GeoPoint?>(null) }

    // Periodically update driver location, check for new requests, and update stats
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val driverId = sessionManager.getDriverId() ?: "DRIVER-1"
            while (true) {
                try {
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
                    
                    // Update stats
                    repository.getDriverStats(driverId).onSuccess { stats -> driverStats = stats }
                    
                    // Check if driver already has an active delivery
                    repository.getMyDeliveries(driverId).onSuccess { myDeliveries ->
                        currentDelivery = if (myDeliveries.isNotEmpty()) {
                            myDeliveries.firstOrNull { it.status != DeliveryStatus.DELIVERED && it.status != DeliveryStatus.CANCELLED }
                        } else {
                            null
                        }
                    }

                    // Poll for available deliveries if not currently on a trip
                    if (currentDelivery == null && activeRequest == null) {
                        repository.getAvailableDeliveries().onSuccess { deliveries ->
                            if (deliveries.isNotEmpty()) {
                                activeRequest = deliveries.first()
                            }
                        }
                    }
                    
                    delay(10000) 
                } catch (e: Exception) {
                    Log.e("MapScreen", "Error in background loop", e)
                }
            }
        }
    }

    // Update navigation route when location or delivery changes
    LaunchedEffect(currentDelivery, driverLatLng) {
        if (currentDelivery != null && driverLatLng != null) {
            val destination = if (currentDelivery!!.status == DeliveryStatus.ASSIGNED) {
                // Navigating to Pickup
                GeoPoint(currentDelivery!!.pickupLat ?: 0.0, currentDelivery!!.pickupLng ?: 0.0)
            } else {
                // Navigating to Dropoff
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
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(5.6037, -0.1870)) // Accra
                        
                        if (hasLocationPermission) {
                            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            locationOverlay.enableMyLocation()
                            overlays.add(locationOverlay)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    mv.overlays.removeAll { overlay -> overlay !is MyLocationNewOverlay }
                    
                    // Draw navigation polyline
                    if (navigationPath.isNotEmpty()) {
                        val line = Polyline(mv)
                        line.setPoints(navigationPath)
                        line.outlinePaint.color = "#004E89".toColorInt()
                        line.outlinePaint.strokeWidth = 12f
                        mv.overlays.add(line)
                        
                        // Add target marker
                        val target = navigationPath.last()
                        val marker = Marker(mv)
                        marker.position = target
                        marker.title = if (currentDelivery?.status == DeliveryStatus.ASSIGNED) "Pickup" else "Destination"
                        mv.overlays.add(marker)
                    }
                    mv.invalidate()
                }
            )

            // Earnings Overlay at the top
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
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
                        Text("COMPLETED", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("${driverStats.completedToday}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF004E89))
                    }
                }
            }

            // Active Trip Card
            currentDelivery?.let { delivery ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val statusText = when(delivery.status) {
                                    DeliveryStatus.ASSIGNED -> "GOING TO PICKUP"
                                    DeliveryStatus.IN_TRANSIT -> "TRIP IN PROGRESS"
                                    else -> delivery.status.name
                                }
                                Text(statusText, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35), fontSize = 12.sp)
                                Text(delivery.customerName ?: "Customer", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            
                            IconButton(onClick = { /* TODO: Call Customer */ }) {
                                Icon(Icons.Default.Navigation, "Navigate", tint = Color(0xFF004E89))
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

            // Incoming Request Card (Bolt/Uber style)
            activeRequest?.let { delivery ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)) // Dim background
                ) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "New Delivery Request",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.Gray
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "GHS ${String.format(Locale.getDefault(), "%.2f", delivery.estimatedEarnings)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 28.sp,
                                        color = Color(0xFF28A745)
                                    )
                                    Text(
                                        text = "Estimated Earnings",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Surface(
                                    color = Color(0xFFFFEAD1),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%.1f", delivery.distanceKm)} km",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE67E22)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Pickup/Drop-off simple info
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { activeRequest = null },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Ignore", color = Color.Gray)
                                }
                                
                                Button(
                                    onClick = {
                                        isAccepting = true
                                        scope.launch {
                                            val driverId = sessionManager.getDriverId() ?: "DRIVER-1"
                                            repository.acceptDelivery(driverId, delivery.id).onSuccess {
                                                isAccepting = false
                                                // Handle success - navigate to active delivery screen
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
        }
    }
}
