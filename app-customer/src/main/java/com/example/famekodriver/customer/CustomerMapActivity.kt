package com.example.famekodriver.customer

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.famekodriver.core.data.repository.DriverRepository
import com.example.famekodriver.core.domain.model.DriverLocation
import com.example.famekodriver.core.domain.model.RouteLocation
import com.example.famekodriver.core.domain.model.RouteRequest
import com.google.android.gms.location.LocationServices
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.viewinterop.AndroidView
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

private fun carIconDrawable(context: android.content.Context, rawResId: Int, width: Int, height: Int): android.graphics.drawable.Drawable? {
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
    val scope = rememberCoroutineScope()
    val repository = remember { DriverRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var carIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    
    // Load car icon asynchronously
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
    LaunchedEffect(Unit) {
        while (true) {
            try {
                repository.getAvailableDrivers().onSuccess { drivers = it }
            } catch (e: Exception) {
                Log.e("CustomerMap", "Failed to fetch drivers", e)
            }
            kotlinx.coroutines.delay(10000)
        }
    }

    // State for locations
    var pickupLocation by remember { mutableStateOf("") }
    var dropoffLocation by remember { mutableStateOf("") }
    var pickupGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var dropoffGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var polylineGeoPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var estimatedFare by remember { mutableStateOf<Double?>(null) }
    var routeInfo by remember { mutableStateOf<String?>(null) }
    var distanceKm by remember { mutableStateOf(0.0) }
    var durationMin by remember { mutableStateOf(0.0) }
    var isOrderPlacing by remember { mutableStateOf(false) }

    // Suggestions state
    var pickupSuggestions by remember { mutableStateOf<List<com.example.famekodriver.core.domain.model.LocationSuggestion>>(emptyList()) }
    var dropoffSuggestions by remember { mutableStateOf<List<com.example.famekodriver.core.domain.model.LocationSuggestion>>(emptyList()) }

    // Fetch suggestions (Pickup)
    LaunchedEffect(pickupLocation) {
        if (pickupLocation.length > 2 && pickupGeoPoint == null) {
            kotlinx.coroutines.delay(500)
            repository.getGeocodeSuggestions(pickupLocation).onSuccess { pickupSuggestions = it }
        } else {
            pickupSuggestions = emptyList()
        }
    }

    // Fetch suggestions (Dropoff)
    LaunchedEffect(dropoffLocation) {
        if (dropoffLocation.length > 2 && dropoffGeoPoint == null) {
            kotlinx.coroutines.delay(500)
            repository.getGeocodeSuggestions(dropoffLocation).onSuccess { dropoffSuggestions = it }
        } else {
            dropoffSuggestions = emptyList()
        }
    }

    // MapView creation and lifecycle management
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(5.6037, -0.1870)) // Accra
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

                    // Fare calculation logic
                    val dist = response.distanceM / 1000.0
                    val dur = response.etaMin
                    distanceKm = dist
                    durationMin = dur
                    
                    val baseFare = 5.0
                    val kmRate = 1.5
                    val minRate = 0.5
                    estimatedFare = baseFare + (dist * kmRate) + (dur * minRate)
                    routeInfo = "%.1f km • %.0f min".format(dist, dur)

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
            } catch (e: Exception) {
                Log.e("CustomerMap", "Error in route flow", e)
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
                // Robust overlay management
                mv.overlays.clear()
                
                // 1. My Location
                if (hasLocationPermission) {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mv)
                    locationOverlay.enableMyLocation()
                    mv.overlays.add(locationOverlay)
                }
                
                // 2. Add Polylines
                if (polylineGeoPoints.isNotEmpty()) {
                    val line = Polyline(mv)
                    line.setPoints(polylineGeoPoints)
                    line.outlinePaint.color = android.graphics.Color.parseColor("#004E89")
                    line.outlinePaint.strokeWidth = 12f
                    mv.overlays.add(line)
                    
                    // Start Marker
                    val startMarker = Marker(mv)
                    startMarker.position = pickupGeoPoint
                    startMarker.title = "Pickup"
                    startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mv.overlays.add(startMarker)
                    
                    // End Marker
                    val endMarker = Marker(mv)
                    endMarker.position = dropoffGeoPoint
                    endMarker.title = "Destination"
                    endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mv.overlays.add(endMarker)
                }
                
                // 3. Add Driver Markers
                drivers.forEach { driver ->
                    val marker = Marker(mv)
                    marker.position = GeoPoint(driver.latitude, driver.longitude)
                    marker.title = "Fameko Driver"
                    if (carIcon != null) {
                        marker.icon = carIcon
                    }
                    marker.rotation = -driver.bearing 
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mv.overlays.add(marker)
                }
                
                mv.invalidate()
            }
        )

        // Overlay UI
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Pickup Field
                    Column {
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
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Pickup Location") },
                            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF34D186)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF6F6F6), unfocusedContainerColor = Color(0xFFF6F6F6), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            enabled = !isLoading
                        )
                        
                        if (pickupSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                    items(pickupSuggestions) { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion.displayName) },
                                            modifier = Modifier.clickable {
                                                pickupLocation = suggestion.displayName
                                                pickupGeoPoint = GeoPoint(suggestion.latitude.toDouble(), suggestion.longitude.toDouble())
                                                pickupSuggestions = emptyList()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropoff Field
                    Column {
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
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Where to?") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFDC3545)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF6F6F6), unfocusedContainerColor = Color(0xFFF6F6F6), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            enabled = !isLoading
                        )

                        if (dropoffSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                    items(dropoffSuggestions) { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion.displayName) },
                                            modifier = Modifier.clickable {
                                                dropoffLocation = suggestion.displayName
                                                dropoffGeoPoint = GeoPoint(suggestion.latitude.toDouble(), suggestion.longitude.toDouble())
                                                dropoffSuggestions = emptyList()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { fetchRouteFromBackend() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004E89)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading && pickupLocation.isNotBlank() && dropoffLocation.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Destination", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Current Location FAB
        FloatingActionButton(
            onClick = {
                if (hasLocationPermission) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    @SuppressLint("MissingPermission")
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let { 
                            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude), 15.0, 1000L)
                        }
                    }
                }
            },
            modifier = Modifier.padding(16.dp).align(Alignment.BottomEnd).padding(bottom = 16.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF004E89)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My Location")
        }

        // Fare Summary Card
        if (estimatedFare != null && !isLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 80.dp), 
                elevation = CardDefaults.cardElevation(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Economy Delivery", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(routeInfo ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "GHS %.2f".format(estimatedFare),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF28A745) 
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { 
                            if (pickupGeoPoint != null && dropoffGeoPoint != null && estimatedFare != null) {
                                isOrderPlacing = true
                                scope.launch {
                                    repository.createOrder(
                                        customerId = "1", 
                                        pickupLocation = pickupLocation,
                                        dropoffLocation = dropoffLocation,
                                        pickupLat = pickupGeoPoint!!.latitude,
                                        pickupLng = pickupGeoPoint!!.longitude,
                                        dropoffLat = dropoffGeoPoint!!.latitude,
                                        dropoffLng = dropoffGeoPoint!!.longitude,
                                        distanceKm = distanceKm,
                                        estimatedFare = estimatedFare!!,
                                        durationMin = durationMin
                                    ).onSuccess { orderId ->
                                        isOrderPlacing = false
                                        Toast.makeText(context, "Order #$orderId placed!", Toast.LENGTH_LONG).show()
                                    }.onFailure { e ->
                                        isOrderPlacing = false
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isOrderPlacing
                    ) {
                        if (isOrderPlacing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Order", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
