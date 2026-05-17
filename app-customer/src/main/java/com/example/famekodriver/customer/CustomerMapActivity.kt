package com.example.famekodriver.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.example.famekodriver.core.domain.model.DriverLocation
import com.example.famekodriver.core.domain.model.RouteLocation
import com.example.famekodriver.core.domain.model.RouteRequest
import com.example.famekodriver.core.network.OrderStatusResponse
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class CustomerMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CustomerMapScreen()
        }
    }
}

private fun carIconDrawable(context: Context, rawResId: Int, width: Int, height: Int): Drawable? {
    return try {
        context.resources.openRawResource(rawResId).use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
            BitmapDrawable(context.resources, scaledBitmap)
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMapScreen() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val repository = remember { DriverRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var carIcon by remember { mutableStateOf<Drawable?>(null) }
    
    LaunchedEffect(Unit) {
        val size = (32 * context.resources.displayMetrics.density).toInt()
        carIcon = carIconDrawable(context, com.example.famekodriver.customer.R.raw.car, size, size)
    }
    
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLocationPermission = it }

    LaunchedEffect(Unit) { 
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var drivers by remember { mutableStateOf<List<DriverLocation>>(emptyList()) }
    LaunchedEffect(hasLocationPermission) {
        while (true) {
            if (hasLocationPermission) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                @SuppressLint("MissingPermission")
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        scope.launch {
                            repository.getNearbyDrivers(it.latitude, it.longitude).onSuccess { list ->
                                drivers = list
                            }
                        }
                    }
                }
            }
            delay(5000)
        }
    }

    var pickupLocation by remember { mutableStateOf("") }
    var dropoffLocation by remember { mutableStateOf("") }
    var pickupGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var dropoffGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var polylineGeoPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var estimatedFare by remember { mutableStateOf<Double?>(null) }
    var routeInfo by remember { mutableStateOf<String?>(null) }
    var distanceKm by remember { mutableDoubleStateOf(0.0) }
    var durationMin by remember { mutableDoubleStateOf(0.0) }
    var isOrderPlacing by remember { mutableStateOf(false) }
    var selectedVehicleType by remember { mutableStateOf("Economy") }
    
    var currentOrderId by remember { mutableStateOf<Int?>(null) }
    var orderStatusData by remember { mutableStateOf<OrderStatusResponse?>(null) }

    var isPickupFocused by remember { mutableStateOf(false) }
    var isDropoffFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentOrderId) {
        if (currentOrderId != null) {
            while (true) {
                repository.getOrderStatus(currentOrderId!!).onSuccess { response ->
                    orderStatusData = response
                    if (response.status == "DELIVERED" || response.status == "CANCELLED") {
                        return@onSuccess
                    }
                }
                delay(5000)
            }
        }
    }

    var pickupSuggestions by remember { mutableStateOf<List<com.example.famekodriver.core.domain.model.LocationSuggestion>>(emptyList()) }
    var dropoffSuggestions by remember { mutableStateOf<List<com.example.famekodriver.core.domain.model.LocationSuggestion>>(emptyList()) }

    LaunchedEffect(pickupLocation, isPickupFocused) {
        if (isPickupFocused && pickupLocation.length > 2 && pickupGeoPoint == null) {
            delay(300)
            repository.getGeocodeSuggestions(pickupLocation).onSuccess { pickupSuggestions = it }
        } else if (!isPickupFocused || pickupLocation.isEmpty()) {
            pickupSuggestions = emptyList()
        }
    }

    LaunchedEffect(dropoffLocation, isDropoffFocused) {
        if (isDropoffFocused && dropoffLocation.length > 2 && dropoffGeoPoint == null) {
            delay(300)
            repository.getGeocodeSuggestions(dropoffLocation).onSuccess { dropoffSuggestions = it }
        } else if (!isDropoffFocused || dropoffLocation.isEmpty()) {
            dropoffSuggestions = emptyList()
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(5.6037, -0.1870))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    fun fetchRouteFromBackend() {
        if (pickupGeoPoint == null || dropoffGeoPoint == null) {
            Toast.makeText(context, "Please select locations from the suggestions", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        scope.launch {
            try {
                val routeRequest = RouteRequest(
                    start = RouteLocation(pickupGeoPoint!!.latitude, pickupGeoPoint!!.longitude),
                    end = RouteLocation(dropoffGeoPoint!!.latitude, dropoffGeoPoint!!.longitude),
                    vehicleType = "car",
                    routeType = "fastest"
                )

                repository.calculateRoute(routeRequest).onSuccess { response ->
                    val coords = response.routeCoords.map { GeoPoint(it[1], it[0]) }
                    polylineGeoPoints = coords
                    val dist = response.distanceM / 1000.0
                    val dur = response.etaMin
                    distanceKm = dist
                    durationMin = dur
                    
                    val baseFare = 5.0
                    val kmRate = 1.5
                    val minRate = 0.5
                    estimatedFare = baseFare + (dist * kmRate) + (dur * minRate)
                    val info = "%.1f km • %.0f min".format(dist, dur)
                    routeInfo = info

                    if (coords.isNotEmpty()) {
                        try {
                            val box = org.osmdroid.util.BoundingBox.fromGeoPoints(coords)
                            mapView.zoomToBoundingBox(box, true, 100)
                        } catch (e: Exception) {
                            Log.e("CustomerMap", "Failed to zoom", e)
                        }
                    }
                }.onFailure {
                    Toast.makeText(context, "Routing failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                mv.overlays.clear()
                if (hasLocationPermission) {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mv)
                    locationOverlay.enableMyLocation()
                    mv.overlays.add(locationOverlay)
                }
                if (polylineGeoPoints.isNotEmpty()) {
                    val line = Polyline(mv)
                    line.setPoints(polylineGeoPoints)
                    line.outlinePaint.color = android.graphics.Color.parseColor("#004E89")
                    line.outlinePaint.strokeWidth = 12f
                    line.outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    line.outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    mv.overlays.add(line)
                    
                    if (pickupGeoPoint != null) {
                        val startMarker = Marker(mv)
                        startMarker.position = pickupGeoPoint
                        startMarker.title = "Pickup"
                        startMarker.icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mv.overlays.add(startMarker)
                    }
                    
                    if (dropoffGeoPoint != null) {
                        val endMarker = Marker(mv)
                        endMarker.position = dropoffGeoPoint
                        endMarker.title = "Destination"
                        endMarker.icon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)
                        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mv.overlays.add(endMarker)
                    }
                }
                drivers.forEach { driver ->
                    val marker = Marker(mv)
                    marker.position = GeoPoint(driver.latitude, driver.longitude)
                    marker.title = "Fameko Driver"
                    if (carIcon != null) marker.icon = carIcon
                    marker.rotation = -driver.bearing 
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mv.overlays.add(marker)
                }
                mv.invalidate()
            }
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextField(
                        value = pickupLocation,
                        onValueChange = { 
                            pickupLocation = it
                            if (pickupGeoPoint != null) {
                                pickupGeoPoint = null 
                                estimatedFare = null 
                                polylineGeoPoints = emptyList()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isPickupFocused = it.isFocused },
                        placeholder = { Text("Pickup Location") },
                        leadingIcon = { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF34D186)) },
                        trailingIcon = {
                            if (pickupLocation.isNotEmpty() && isPickupFocused) {
                                IconButton(onClick = { pickupLocation = ""; pickupGeoPoint = null }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF6F6F6), unfocusedContainerColor = Color(0xFFF6F6F6), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        enabled = !isLoading && currentOrderId == null
                    )
                    
                    if (isPickupFocused && currentOrderId == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Use Current Location", fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text("Set your current position as pickup", fontSize = 11.sp, color = Color.Gray) },
                                        leadingContent = { Icon(Icons.Default.MyLocation, null, tint = Color(0xFF34D186)) },
                                        modifier = Modifier.clickable {
                                            if (hasLocationPermission) {
                                                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                                @SuppressLint("MissingPermission")
                                                fusedLocationClient.lastLocation.addOnSuccessListener { deviceLocation ->
                                                    deviceLocation?.let { loc ->
                                                        scope.launch {
                                                            repository.reverseGeocode(loc.latitude, loc.longitude).onSuccess { suggestion ->
                                                                pickupLocation = suggestion.displayName
                                                                pickupGeoPoint = GeoPoint(loc.latitude, loc.longitude)
                                                            }.onFailure {
                                                                pickupLocation = "My Location"
                                                                pickupGeoPoint = GeoPoint(loc.latitude, loc.longitude)
                                                            }
                                                            pickupSuggestions = emptyList()
                                                            isPickupFocused = false
                                                            focusManager.clearFocus()
                                                        }
                                                    }
                                                }
                                            } else {
                                                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        }
                                    )
                                }
                                items(pickupSuggestions) { suggestion ->
                                    ListItem(
                                        headlineContent = { Text(suggestion.name ?: suggestion.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(suggestion.displayName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray) },
                                        leadingContent = { 
                                            Icon(
                                                if (suggestion.type == "coordinate") Icons.Default.LocationSearching else Icons.Default.History, 
                                                null, 
                                                tint = Color(0xFF004E89).copy(alpha = 0.6f)
                                            ) 
                                        },
                                        modifier = Modifier.clickable {
                                            pickupLocation = suggestion.displayName
                                            pickupGeoPoint = GeoPoint(suggestion.latitude.toDouble(), suggestion.longitude.toDouble())
                                            pickupSuggestions = emptyList()
                                            isPickupFocused = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = dropoffLocation,
                        onValueChange = { 
                            dropoffLocation = it 
                            if (dropoffGeoPoint != null) {
                                dropoffGeoPoint = null 
                                estimatedFare = null 
                                polylineGeoPoints = emptyList()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isDropoffFocused = it.isFocused },
                        placeholder = { Text("Where to?") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFFDC3545)) },
                        trailingIcon = {
                            if (dropoffLocation.isNotEmpty() && isDropoffFocused) {
                                IconButton(onClick = { dropoffLocation = ""; dropoffGeoPoint = null }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF6F6F6), unfocusedContainerColor = Color(0xFFF6F6F6), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        enabled = !isLoading && currentOrderId == null
                    )

                    if (dropoffSuggestions.isNotEmpty() && isDropoffFocused) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                items(dropoffSuggestions) { suggestion ->
                                    ListItem(
                                        headlineContent = { Text(suggestion.name ?: suggestion.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(suggestion.displayName, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray) },
                                        leadingContent = { 
                                            Icon(
                                                if (suggestion.type == "coordinate") Icons.Default.LocationSearching else Icons.Default.LocationOn, 
                                                null, 
                                                tint = Color(0xFFDC3545).copy(alpha = 0.6f)
                                            ) 
                                        },
                                        modifier = Modifier.clickable {
                                            dropoffLocation = suggestion.displayName
                                            dropoffGeoPoint = GeoPoint(suggestion.latitude.toDouble(), suggestion.longitude.toDouble())
                                            dropoffSuggestions = emptyList()
                                            isDropoffFocused = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (polylineGeoPoints.isEmpty() && currentOrderId == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { fetchRouteFromBackend() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004E89)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isLoading && pickupLocation.isNotBlank() && dropoffLocation.isNotBlank()
                        ) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            else Text("Confirm Destination", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (hasLocationPermission) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    @SuppressLint("MissingPermission")
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let { mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude), 15.0, 1000L) }
                    }
                }
            },
            modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd).padding(bottom = if (estimatedFare != null) 250.dp else 16.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF004E89)
        ) {
            Icon(Icons.Default.MyLocation, "My Location")
        }

        if (estimatedFare != null && !isLoading && currentOrderId == null) {
            Card(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                elevation = CardDefaults.cardElevation(12.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Service", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(routeInfo ?: "", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val vehicleTypes = listOf(
                        Triple("Economy", "Lite", Icons.Default.DirectionsCar),
                        Triple("Comfort", "Comfort", Icons.Default.LocalTaxi),
                        Triple("Bike", "Moto", Icons.Default.TwoWheeler)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        vehicleTypes.forEach { (typeId, name, icon) ->
                            val isSelected = selectedVehicleType == typeId
                            val multiplier = when(typeId) { "Comfort" -> 1.3; "Bike" -> 0.7; else -> 1.0 }
                            Card(
                                modifier = Modifier.weight(1f).clickable { selectedVehicleType = typeId },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF004E89).copy(alpha = 0.1f) else Color(0xFFF6F6F6)),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF004E89)) else null
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, tint = if (isSelected) Color(0xFF004E89) else Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) Color(0xFF004E89) else Color.Black)
                                    Text("₵%.1f".format(estimatedFare!! * multiplier), fontSize = 11.sp, color = if (isSelected) Color(0xFF004E89) else Color.Gray)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { 
                            isOrderPlacing = true
                            scope.launch {
                                val customerId = sessionManager.getDriverId() ?: "1" // Reusing driverId key for customer id in shared SessionManager
                                val multiplier = when(selectedVehicleType) { "Comfort" -> 1.3; "Bike" -> 0.7; else -> 1.0 }
                                repository.createOrder(customerId, pickupLocation, dropoffLocation, pickupGeoPoint!!.latitude, pickupGeoPoint!!.longitude, dropoffGeoPoint!!.latitude, dropoffGeoPoint!!.longitude, distanceKm, estimatedFare!! * multiplier, durationMin).onSuccess { id ->
                                    isOrderPlacing = false
                                    currentOrderId = id.toIntOrNull()
                                }.onFailure { e ->
                                    isOrderPlacing = false
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isOrderPlacing
                    ) {
                        if (isOrderPlacing) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text("Confirm $selectedVehicleType", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        if (orderStatusData != null) {
            when (orderStatusData!!.status) {
                "PENDING" -> SearchingOverlay(onCancel = { currentOrderId = null; orderStatusData = null })
                "ASSIGNED", "IN_TRANSIT" -> DriverAssignedOverlay(orderStatusData!!)
            }
        }
    }
}

@Composable
fun SearchingOverlay(onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawCircle(color = Color(0xFF004E89), radius = size.minDimension / 2 * scale, alpha = alpha)
                }
                Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(100.dp), shadowElevation = 8.dp) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF004E89)) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Finding your driver...", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connecting you to the nearest Fameko", color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(64.dp))
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)), shape = RoundedCornerShape(24.dp)) {
                Text("Cancel Request", color = Color.White)
            }
        }
    }
}

@Composable
fun DriverAssignedOverlay(data: OrderStatusResponse) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.LightGray, modifier = Modifier.size(60.dp)) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(data.driverName ?: "Driver", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB400), modifier = Modifier.size(16.dp))
                            Text((data.driverRating ?: 5.0).toString(), fontSize = 14.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("•", color = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(data.driverVehicle ?: "Car", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                    Row {
                        IconButton(onClick = {}) { Icon(Icons.Default.Phone, null, tint = Color(0xFF28A745)) }
                        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF004E89)) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color(0xFFF6F6F6), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalTaxi, null, tint = Color(0xFF004E89))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = if (data.status == "ASSIGNED") "Driver is on the way" else "Trip in progress", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
