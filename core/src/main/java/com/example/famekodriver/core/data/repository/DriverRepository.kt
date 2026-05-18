package com.example.famekodriver.core.data.repository

import com.example.famekodriver.core.domain.model.*
import com.example.famekodriver.core.network.DatabaseConfig
import com.example.famekodriver.core.network.NetworkClient
import com.example.famekodriver.core.network.DriverStatusResponse
import com.example.famekodriver.core.network.OrderCreateRequest
import com.example.famekodriver.core.network.OrderStatusResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.sql.DriverManager
import java.sql.ResultSet
import com.google.gson.Gson

/**
 * Repository for driver-related database operations.
 */
class DriverRepository {
    private val gson = Gson()
    private val _events = MutableSharedFlow<FamekoEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<FamekoEvent> = _events

    private var webSocket: WebSocket? = null

    fun startWebSocket(userId: String) {
        val request = Request.Builder()
            .url(NetworkClient.getWebSocketUrl(userId))
            .build()
        
        webSocket = NetworkClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val wsMessage = gson.fromJson(text, WebSocketMessage::class.java)
                    when (wsMessage.type) {
                        "NEW_DELIVERY" -> {
                            val delivery = gson.fromJson(wsMessage.payload, Delivery::class.java)
                            _events.tryEmit(FamekoEvent.NewDeliveryRequest(delivery))
                        }
                        "CALL_INCOMING", "call_incoming", "driver_call_incoming" -> {
                            val data = gson.fromJson(wsMessage.payload, Map::class.java)
                            val callId = data["call_id"]?.toString() ?: ""
                            val name = data["customer_name"]?.toString() ?: data["driver_name"]?.toString() ?: "Someone"
                            _events.tryEmit(FamekoEvent.IncomingCall(callId, name))
                        }
                        "CALL_ACCEPTED", "call_accepted" -> {
                            val data = gson.fromJson(wsMessage.payload, Map::class.java)
                            val callId = data["call_id"]?.toString() ?: ""
                            _events.tryEmit(FamekoEvent.CallAccepted(callId))
                        }
                        "CALL_REJECTED", "call_rejected" -> {
                            val data = gson.fromJson(wsMessage.payload, Map::class.java)
                            val callId = data["call_id"]?.toString() ?: ""
                            val reason = data["reason"]?.toString()
                            _events.tryEmit(FamekoEvent.CallRejected(callId, reason))
                        }
                        "CALL_ENDED", "call_ended" -> {
                            val data = gson.fromJson(wsMessage.payload, Map::class.java)
                            val callId = data["call_id"]?.toString() ?: ""
                            _events.tryEmit(FamekoEvent.CallEnded(callId))
                        }
                        "STATUS_CHANGED" -> {
                            // Handle status changes
                        }
                        "NEW_MESSAGE" -> {
                            val message = gson.fromJson(wsMessage.payload, Message::class.java)
                            _events.tryEmit(FamekoEvent.NewMessage(message))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Reconnect logic could go here
            }
        })
    }

    fun stopWebSocket() {
        webSocket?.close(1000, "User logged out")
        webSocket = null
    }

    private fun ensureDriverLoaded() {
        try {
            Class.forName(DatabaseConfig.getDriverClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Authenticates a driver
     */
    suspend fun login(email: String, pass: String): Result<Driver?> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.loginDriver(LoginRequest(email, pass))
            val userId = response.user_id
            if (response.success && userId != null) {
                // In a real app, we'd fetch the full driver object here
                // For now, construct a minimal one from the auth response
                Result.success(Driver(
                    id = userId.toIntOrNull() ?: 0,
                    fullName = response.name ?: "Driver",
                    email = email,
                    phone = "",
                    region = "",
                    licenseNumber = "",
                    vehicleType = "Car",
                    vehicleNumber = "",
                    status = response.status ?: "PENDING",
                    isOnline = false,
                    rating = 5.0
                ))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "API Login failed, falling back to JDBC", e)
            try {
                ensureDriverLoaded()
                val url = DatabaseConfig.getJdbcUrl()
                if (url.isEmpty()) return@withContext Result.failure(e)
                
                DriverManager.getConnection(
                    url,
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    val query = "SELECT * FROM drivers WHERE email = ? AND password = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, email)
                        stmt.setString(2, pass)
                        val rs = stmt.executeQuery()
                        if (rs.next()) {
                            Result.success(Driver(
                                id = rs.getInt("id"),
                                fullName = rs.getString("full_name"),
                                email = rs.getString("email"),
                                phone = rs.getString("phone") ?: "",
                                region = rs.getString("region") ?: "",
                                licenseNumber = rs.getString("license_number") ?: "",
                                vehicleType = rs.getString("vehicle_type"),
                                vehicleNumber = rs.getString("vehicle_number") ?: "",
                                status = rs.getString("status"),
                                isOnline = false,
                                rating = 5.0
                            ))
                        } else {
                            Result.success(null)
                        }
                    }
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    /**
     * Authenticates a customer
     */
    suspend fun customerLogin(email: String, pass: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.loginCustomer(LoginRequest(email, pass))
            val userId = response.user_id
            if (response.success && userId != null) {
                Result.success(Pair(userId, response.name ?: "Customer"))
            } else {
                Result.failure(Exception(response.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Customer API Login failed, falling back to JDBC", e)
            try {
                ensureDriverLoaded()
                val url = DatabaseConfig.getJdbcUrl()
                if (url.isEmpty()) return@withContext Result.failure(e)

                DriverManager.getConnection(
                    url,
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    val query = "SELECT id, name FROM customers WHERE email = ? AND password = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, email)
                        stmt.setString(2, pass)
                        val rs = stmt.executeQuery()
                        if (rs.next()) {
                            val id = rs.getInt("id").toString()
                            val name = rs.getString("name") ?: "Customer"
                            Result.success(Pair(id, name))
                        } else {
                            Result.failure(Exception("Invalid email or password"))
                        }
                    }
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    /**
     * Fetches driver statistics
     */
    suspend fun getDriverStats(driverId: String): Result<DriverStats> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(),
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASS,
            ).use { connection ->
                val query = "SELECT * FROM driver_stats WHERE driver_id = ?"
                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, driverId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        Result.success(rs.toDriverStats())
                    } else {
                        Result.failure(Exception("Stats not found"))
                    }
                }
            }
        } catch (_: Exception) {
            Result.failure(Exception("Failed to fetch driver stats"))
        }
    }

    /**
     * Fetches recent deliveries for a driver
     */
    suspend fun getRecentDeliveries(driverId: String): Result<List<Delivery>> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(),
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASS,
            ).use { connection ->
                val query = "SELECT * FROM deliveries WHERE driver_id = ? ORDER BY id DESC LIMIT 10"
                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, driverId)
                    val rs = stmt.executeQuery()
                    val list = mutableListOf<Delivery>()
                    while (rs.next()) {
                        list.add(rs.toDelivery())
                    }
                    Result.success(list)
                }
            }
        } catch (_: Exception) {
            Result.failure(Exception("Failed to fetch recent deliveries"))
        }
    }

    /**
     * Updates driver's online status
     */
    suspend fun updateOnlineStatus(driverId: String, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(),
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASS,
            ).use { connection ->
                val query = "UPDATE driver_stats SET is_online = ? WHERE driver_id = ?"
                connection.prepareStatement(query).use { stmt ->
                    stmt.setBoolean(1, isOnline)
                    stmt.setString(2, driverId)
                    stmt.executeUpdate()
                    Result.success(Unit)
                }
            }
        } catch (_: Exception) {
            Result.failure(Exception("Failed to update status"))
        }
    }

    /**
     * Register a new customer
     */
    suspend fun customerRegister(name: String, email: String, phone: String, address: String, password: String, region: String? = null, profilePicture: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = CustomerRegisterRequest(name, email, phone, address, password, region, profilePicture)
            val response = NetworkClient.famekoApi.registerCustomer(request)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Registration failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "API Registration failed, falling back to JDBC", e)
            // Fallback to JDBC if API is not available
            try {
                ensureDriverLoaded()
                DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    val query = "INSERT INTO customers (name, email, phone, default_address, password, region, profile_picture) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, name)
                        stmt.setString(2, email)
                        stmt.setString(3, phone)
                        stmt.setString(4, address)
                        stmt.setString(5, password)
                        stmt.setString(6, region)
                        stmt.setString(7, profilePicture)
                        stmt.executeUpdate()
                    }
                    Result.success(Unit)
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    /**
     * Register a new driver with documents using the backend API
     */
    suspend fun driverRegister(
        name: String,
        email: String,
        phone: String,
        password: String,
        licenseNumber: String,
        region: String,
        vehicleType: String,
        serviceType: String,
        vehicleNumber: String,
        docs: Map<String, java.io.File> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nameBody = name.toRequestBody(MultipartBody.FORM)
            val emailBody = email.toRequestBody(MultipartBody.FORM)
            val phoneBody = phone.toRequestBody(MultipartBody.FORM)
            val passBody = password.toRequestBody(MultipartBody.FORM)
            val licenseBody = licenseNumber.toRequestBody(MultipartBody.FORM)
            val regionBody = region.toRequestBody(MultipartBody.FORM)
            val vTypeBody = vehicleType.toRequestBody(MultipartBody.FORM)
            val sTypeBody = serviceType.toRequestBody(MultipartBody.FORM)
            val vNumBody = vehicleNumber.toRequestBody(MultipartBody.FORM)

            fun fileToPart(key: String): MultipartBody.Part? {
                val file = docs[key] ?: return null
                val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                return MultipartBody.Part.createFormData(key, file.name, reqFile)
            }

            val response = NetworkClient.famekoApi.registerDriver(
                nameBody, emailBody, phoneBody, passBody, licenseBody, regionBody, vTypeBody, sTypeBody, vNumBody,
                fileToPart("profile_pic"),
                fileToPart("drivers_license"),
                fileToPart("insurance_cert"),
                fileToPart("roadworthy_cert"),
                fileToPart("ghana_card")
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Registration failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Driver API Registration failed", e)
            Result.failure(Exception("Registration failed: ${e.localizedMessage}"))
        }
    }

    suspend fun uploadDocument(driverId: String, docType: String, file: java.io.File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("FamekoUpload", "Starting Cloudinary upload for driver $driverId, type: $docType")
            
            // Use raw OkHttp with the configured client for speed and timeouts
            val cloudinaryUrl = "https://api.cloudinary.com/v1_1/df3jnubvy/image/upload"
            val uploadPreset = "fameko_docs"
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", uploadPreset)
                .build()

            val request = okhttp3.Request.Builder()
                .url(cloudinaryUrl)
                .post(requestBody)
                .build()

            val client = NetworkClient.okHttpClient // 5-minute timeout shared client
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: response.message
                android.util.Log.e("FamekoUpload", "Cloudinary HTTP error: $errorMsg")
                throw Exception("Cloudinary upload failed: $errorMsg")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response from Cloudinary")
            val jsonResponse = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
            val fileUrl = jsonResponse.get("secure_url").asString

            android.util.Log.d("FamekoUpload", "Cloudinary success! URL: $fileUrl")

            // 2. Send the URL to your Backend
            val backendResponse = NetworkClient.famekoApi.uploadDriverDocument(driverId, docType, fileUrl)
            
            if (backendResponse.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(backendResponse.message ?: "Backend rejected the link"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoUpload", "Cloudinary Flow Exception", e)
            Result.failure(Exception("Upload failed: ${e.localizedMessage}"))
        }
    }

    suspend fun getDriverStatus(driverId: String): Result<DriverStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getDriverStatus(driverId)
            Result.success(response)
        } catch (_: Exception) {
            Result.failure(Exception("Failed to get driver status"))
        }
    }

    suspend fun getAvailableDeliveries(): Result<List<Delivery>> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.DB_USER, DatabaseConfig.DB_PASS).use { conn ->
                val rs = conn.createStatement().executeQuery("SELECT * FROM deliveries WHERE status = 'PENDING'")
                val list = mutableListOf<Delivery>()
                while (rs.next()) list.add(rs.toDelivery())
                Result.success(list)
            }
        } catch (_: Exception) { Result.failure(Exception("Failed to fetch available deliveries")) }
    }

    suspend fun getMyDeliveries(driverId: String): Result<List<Delivery>> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.DB_USER, DatabaseConfig.DB_PASS).use { conn ->
                val stmt = conn.prepareStatement("SELECT * FROM deliveries WHERE driver_id = ?")
                stmt.setString(1, driverId)
                val rs = stmt.executeQuery()
                val list = mutableListOf<Delivery>()
                while (rs.next()) list.add(rs.toDelivery())
                Result.success(list)
            }
        } catch (_: Exception) { Result.failure(Exception("Failed to fetch your deliveries")) }
    }

    suspend fun acceptDelivery(driverId: String, deliveryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.DB_USER, DatabaseConfig.DB_PASS).use { conn ->
                val stmt = conn.prepareStatement("UPDATE deliveries SET driver_id = ?, status = 'ASSIGNED' WHERE id = ?")
                stmt.setString(1, driverId)
                stmt.setString(2, deliveryId)
                stmt.executeUpdate()
                Result.success(Unit)
            }
        } catch (_: Exception) { Result.failure(Exception("Failed to accept delivery")) }
    }

    suspend fun updateDeliveryStatus(deliveryId: String, status: DeliveryStatus): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.DB_USER, DatabaseConfig.DB_PASS).use { conn ->
                val stmt = conn.prepareStatement("UPDATE deliveries SET status = ? WHERE id = ?")
                stmt.setString(1, status.name)
                stmt.setString(2, deliveryId)
                stmt.executeUpdate()
                Result.success(Unit)
            }
        } catch (_: Exception) { Result.failure(Exception("Failed to update delivery status")) }
    }

    suspend fun updateLocation(driverId: String, lat: Double, lng: Double, bearing: Float): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(),
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASS,
            ).use { connection ->
                // Try updating with PostGIS point first
                val spatialQuery = """
                    UPDATE driver_stats 
                    SET latitude = ?, longitude = ?, bearing = ?, 
                        location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography 
                    WHERE driver_id = ?
                """.trimIndent()
                
                try {
                    connection.prepareStatement(spatialQuery).use { stmt ->
                        stmt.setDouble(1, lat)
                        stmt.setDouble(2, lng)
                        stmt.setFloat(3, bearing)
                        stmt.setDouble(4, lng)
                        stmt.setDouble(5, lat)
                        stmt.setString(6, driverId)
                        stmt.executeUpdate()
                    }
                } catch (spatialError: Exception) {
                    // Fallback to simple latitude/longitude update if PostGIS extension is missing
                    val basicQuery = "UPDATE driver_stats SET latitude = ?, longitude = ?, bearing = ? WHERE driver_id = ?"
                    connection.prepareStatement(basicQuery).use { stmt ->
                        stmt.setDouble(1, lat)
                        stmt.setDouble(2, lng)
                        stmt.setFloat(3, bearing)
                        stmt.setString(4, driverId)
                        stmt.executeUpdate()
                    }
                }
                Result.success(Unit)
            }
        } catch (_: Exception) {
            Result.failure(Exception("Failed to update location"))
        }
    }

    suspend fun calculateRoute(request: RouteRequest): Result<RouteResponse> = withContext(Dispatchers.IO) {
        try {
            // EXCLUSIVELY use OSRM directly for real-world road routing
            // and completely bypass any backend mock routing logic
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${request.start.lng},${request.start.lat};" +
                    "${request.end.lng},${request.end.lat}" +
                    "?overview=full&geometries=geojson"
            
            val osrmResponse = NetworkClient.osmService.getRoute(url)
            
            if (osrmResponse.code == "Ok" && osrmResponse.routes.isNotEmpty()) {
                val route = osrmResponse.routes[0]
                val response = RouteResponse(
                    fromCache = false,
                    routeCoords = route.geometry.coordinates,
                    distanceM = route.distance.toInt(),
                    etaMin = route.duration / 60.0,
                    vehicleType = request.vehicleType,
                    routeType = request.routeType,
                    waypoints = route.geometry.coordinates.size,
                    computedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                )
                Result.success(response)
            } else {
                Result.failure(Exception("No valid route found from OSRM: ${osrmResponse.code}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Direct OSRM Routing failed, falling back to backend", e)
            try {
                // Fallback to backend as a last resort
                val response = NetworkClient.routingApi.calculateRoute(request)
                Result.success(response)
            } catch (e2: Exception) {
                Result.failure(Exception("Routing failed: ${e2.localizedMessage}"))
            }
        }
    }

    suspend fun sendMessage(message: Message): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.sendMessage(message)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatHistory(convId: Int): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getChatHistory(convId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWalletBalance(driverId: String): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getWalletBalance(driverId)
            Result.success(response["balance"] ?: 0.0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWalletTransactions(driverId: String): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getWalletTransactions(driverId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerSOS(driverId: String, lat: Double, lng: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = SOSRequest(driverId, lat, lng)
            val response = NetworkClient.famekoApi.triggerSOS(request)
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message ?: "Failed to trigger SOS"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShareableTripLink(driverId: String, deliveryId: String): Result<ShareTripResponse> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getShareableTripLink(driverId, deliveryId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHeatmapData(): Result<List<HeatmapPoint>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getHeatmapData()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentSurge(): Result<SurgeInfo> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getCurrentSurge()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNearbyDrivers(lat: Double, lng: Double): Result<List<DriverLocation>> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getNearbyDrivers(lat, lng)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrderStatus(orderId: Int): Result<OrderStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getOrderStatus(orderId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(orderId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.cancelOrder(orderId)
            if (response["success"] == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response["message"]?.toString() ?: "Cancellation failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Order cancellation via API failed, falling back to JDBC", e)
            try {
                ensureDriverLoaded()
                DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    val query = "UPDATE orders SET status = 'Cancelled' WHERE id = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setInt(1, orderId)
                        stmt.executeUpdate()
                    }
                    val deliveryQuery = "UPDATE deliveries SET status = 'CANCELLED' WHERE order_id = ?"
                    connection.prepareStatement(deliveryQuery).use { stmt ->
                        stmt.setInt(1, orderId)
                        stmt.executeUpdate()
                    }
                    Result.success(Unit)
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    suspend fun getGeocodeSuggestions(query: String): Result<List<LocationSuggestion>> = withContext(Dispatchers.IO) {
        try {
            // EXCLUSIVELY use OSM Nominatim directly to ensure real-world locations 
            // and completely bypass any backend mocks (local or production)
            val osmResponse = NetworkClient.osmService.search(query)
            
            // Map and filter results to ensure they are valid
            val filteredResults = osmResponse.map { suggestion ->
                suggestion.copy(
                    name = suggestion.displayName.split(",")[0],
                    type = "address"
                )
            }
            
            Result.success(filteredResults)
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Direct OSM Geocode failed", e)
            Result.failure(e)
        }
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): Result<LocationSuggestion> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.osmService.reverse(lat, lng)
            Result.success(response.copy(
                name = response.displayName.split(",")[0],
                type = "address"
            ))
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Reverse geocode failed", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new order and delivery request in the database
     */
    suspend fun createOrder(
        customerId: String,
        pickupLocation: String,
        dropoffLocation: String,
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double,
        distanceKm: Double,
        estimatedFare: Double,
        durationMin: Double
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = OrderCreateRequest(
                customerId, pickupLocation, dropoffLocation,
                pickupLat, pickupLng, dropoffLat, dropoffLng,
                distanceKm, estimatedFare, durationMin
            )
            val response = NetworkClient.famekoApi.createOrder(request)
            if (response["success"] == true) {
                Result.success(response["orderId"].toString())
            } else {
                Result.failure(Exception(response["message"]?.toString() ?: "Unknown error"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Order creation via API failed, falling back to JDBC", e)
            try {
                ensureDriverLoaded()
                DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    connection.autoCommit = false
                    try {
                        // 1. Create Order
                        val orderQuery = """
                            INSERT INTO orders (customer_id, total_amount, status, latitude, longitude, payment_method, shipping_name, shipping_address, shipping_phone) 
                            VALUES (?, ?, 'Pending', ?, ?, 'Cash on Delivery', 'Customer', ?, '0000000000')
                        """.trimIndent()
                        
                        val orderId = connection.prepareStatement(orderQuery, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                            stmt.setInt(1, customerId.toInt())
                            stmt.setDouble(2, estimatedFare)
                            stmt.setDouble(3, dropoffLat)
                            stmt.setDouble(4, dropoffLng)
                            stmt.setString(5, dropoffLocation)
                            stmt.executeUpdate()
                            val rsKeys = stmt.generatedKeys
                            if (rsKeys.next()) rsKeys.getInt(1) else throw Exception("Failed to create order")
                        }

                        // 2. Create Delivery
                        val deliveryQuery = """
                            INSERT INTO deliveries (order_id, pickup_location, dropoff_location, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km, estimated_duration_minutes, status, service_type, estimated_earnings)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'package_delivery', ?)
                        """.trimIndent()

                        connection.prepareStatement(deliveryQuery).use { stmt ->
                            stmt.setInt(1, orderId)
                            stmt.setString(2, pickupLocation)
                            stmt.setString(3, dropoffLocation)
                            stmt.setDouble(4, pickupLat)
                            stmt.setDouble(5, pickupLng)
                            stmt.setDouble(6, dropoffLat)
                            stmt.setDouble(7, dropoffLng)
                            stmt.setDouble(8, distanceKm)
                            stmt.setDouble(9, durationMin)
                            stmt.setDouble(10, estimatedFare * 0.8) // 80% to driver
                            stmt.executeUpdate()
                        }
                        
                        connection.commit()
                        Result.success(orderId.toString())
                    } catch (e: Exception) {
                        connection.rollback()
                        Result.failure(e)
                    }
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    private fun ResultSet.toDriverStats(): DriverStats {
        return DriverStats(
            isOnline = getBoolean("is_online"),
            activeDeliveries = getInt("active_deliveries"),
            completedToday = getInt("completed_today"),
            earningsToday = getDouble("earnings_today"),
            rating = getDouble("rating"),
            ratingCount = getInt("rating_count"),
            totalDeliveries = getInt("total_deliveries"),
            completionRate = getInt("completion_rate"),
            totalEarnings = getDouble("total_earnings")
        )
    }

    private fun ResultSet.toDelivery(): Delivery {
        return Delivery(
            id = getString("id"),
            orderId = getInt("order_id"),
            driverId = getString("driver_id"),
            pickupLocation = getString("pickup_location"),
            dropoffLocation = getString("dropoff_location"),
            pickupLat = getDouble("pickup_lat"),
            pickupLng = getDouble("pickup_lng"),
            dropoffLat = getDouble("dropoff_lat"),
            dropoffLng = getDouble("dropoff_lng"),
            status = try { 
                DeliveryStatus.valueOf(getString("status").uppercase()) 
            } catch (_: Exception) {
                DeliveryStatus.PENDING 
            },
            distanceKm = getDouble("distance_km"),
            estimatedEarnings = getDouble("estimated_earnings"),
            customerName = getString("customer_name"),
            customerPhone = try { getString("customer_phone") } catch(_: Exception) { null },
            customerAddress = getString("customer_address")
        )
    }
}
