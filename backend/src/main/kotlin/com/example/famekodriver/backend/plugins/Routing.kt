package com.example.famekodriver.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import io.ktor.http.content.*
import com.example.famekodriver.core.domain.model.*
import com.example.famekodriver.backend.db.DatabaseInitializer
import java.sql.Connection
import java.sql.ResultSet
import java.net.URL
import java.net.HttpURLConnection
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondRedirect("/admin/login")
        }

        route("/admin") {
            get("/login") {
                call.respond(ThymeleafContent("login", emptyMap()))
            }

            authenticate("auth-form") {
                post("/login") {
                    call.respondRedirect("/admin/dashboard")
                }
            }

            get("/dashboard") {
                val drivers = getAllDrivers()
                val deliveries = getAllDeliveries()
                val pendingCount = drivers.count { it["status"] == "PENDING" || it["status"] == "PENDING_DOCS" }
                val onlineCount = getOnlineDriverLocations().size
                call.respond(ThymeleafContent("admin_dashboard", mapOf(
                    "drivers" to drivers,
                    "deliveries" to deliveries,
                    "pendingCount" to pendingCount,
                    "onlineCount" to onlineCount,
                    "activePage" to "dashboard"
                )))
            }

            get("/map") {
                call.respond(ThymeleafContent("admin_map", mapOf(
                    "activePage" to "map"
                )))
            }

            get("/drivers") {
                val drivers = getAllDrivers()
                call.respond(ThymeleafContent("admin_drivers", mapOf(
                    "drivers" to drivers,
                    "activePage" to "drivers"
                )))
            }

            get("/driver/{id}") {
                val id = call.parameters["id"]
                val driver = if (id != null) getDriverById(id) else null
                if (driver != null) {
                    println("DEBUG: Rendering driver details for ID $id. Status: ${driver["status"]}")
                    call.respond(ThymeleafContent("admin_driver_details", mapOf(
                        "driver" to driver,
                        "activePage" to "drivers"
                    )))
                } else {
                    call.respondRedirect("/admin/drivers")
                }
            }

            get("/customers") {
                val customers = getAllCustomers()
                call.respond(ThymeleafContent("admin_customers", mapOf(
                    "customers" to customers,
                    "activePage" to "customers"
                )))
            }

            get("/deliveries") {
                val deliveries = getAllDeliveries()
                call.respond(ThymeleafContent("admin_deliveries", mapOf(
                    "deliveries" to deliveries,
                    "activePage" to "deliveries"
                )))
            }

            get("/settings") {
                val config = getPricingConfig()
                call.respond(ThymeleafContent("admin_settings", mapOf(
                    "config" to config,
                    "activePage" to "settings"
                )))
            }

            post("/settings/update") {
                // In a real app, we would update the DB here
                call.respondRedirect("/admin/settings")
            }

            post("/approve/{id}") {
                val id = call.parameters["id"]
                if (id != null) {
                    updateDriverStatus(id, "APPROVED")
                }
                call.respondRedirect("/admin/driver/$id")
            }
            
            post("/reject/{id}") {
                val id = call.parameters["id"]
                if (id != null) {
                    updateDriverStatus(id, "REJECTED")
                }
                call.respondRedirect("/admin/driver/$id")
            }

            post("/suspend/{id}") {
                val id = call.parameters["id"]
                if (id != null) {
                    updateDriverStatus(id, "SUSPENDED")
                }
                call.respondRedirect("/admin/driver/$id")
            }

            post("/release/{id}") {
                val id = call.parameters["id"]
                if (id != null) {
                    updateDriverStatus(id, "APPROVED")
                }
                call.respondRedirect("/admin/driver/$id")
            }

            get("/live-locations") {
                val locations = getOnlineDriverLocations()
                call.respond(locations)
            }

            get("/active-sos") {
                val alerts = getActiveSOSAlerts()
                call.respond(alerts)
            }

            post("/resolve-sos/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id != null) {
                    resolveSOSAlert(id)
                    call.respond(mapOf("success" to true))
                } else {
                    call.respond(mapOf("success" to false))
                }
            }
        }

        // Native API for mobile apps (matching Retrofit paths)
        post("/customer/register") {
            println("RECEIVED: Customer registration request for ${call.request.local.remoteAddress}")
            try {
                val request = call.receive<CustomerRegisterRequest>()
                println("API: Registering customer: ${request.name} (${request.email})")
                val userId = registerCustomerInDb(request)
                if (userId != null) {
                    println("API: Customer registered with ID $userId")
                    call.respond(AuthResponse(true, "Registration successful", userId.toString(), request.name))
                } else {
                    println("API: Registration failed - userId is null")
                    call.respond(AuthResponse(false, "Registration failed", null, null))
                }
            } catch (e: Exception) {
                println("API: Error during customer registration: ${e.message}")
                e.printStackTrace()
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        post("/customer/login") {
            println("RECEIVED: Customer login request for ${call.request.local.remoteAddress}")
            try {
                val request = call.receive<LoginRequest>()
                println("API: Login attempt for ${request.email}")
                val user = loginCustomerInDb(request.email, request.password)
                if (user != null) {
                    println("API: Login successful for ${request.email}")
                    call.respond(AuthResponse(true, "Login successful", user["id"].toString(), user["name"].toString()))
                } else {
                    println("API: Login failed - invalid credentials for ${request.email}")
                    call.respond(AuthResponse(false, "Invalid email or password", null, null))
                }
            } catch (e: Exception) {
                println("API: Login error: ${e.message}")
                e.printStackTrace()
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        post("/driver/login") {
            println("RECEIVED: Driver login request for ${call.request.local.remoteAddress}")
            try {
                val request = call.receive<LoginRequest>()
                println("API: Driver login attempt for ${request.email}")
                val driver = loginDriverInDb(request.email, request.password)
                if (driver != null) {
                    val id = driver["id"]?.toString() ?: ""
                    val name = driver["name"]?.toString() ?: "Driver"
                    val status = driver["status"]?.toString() ?: "PENDING"
                    
                    println("API: Driver login successful for ${request.email}, status: $status")
                    call.respond(AuthResponse(true, "Login successful", id, name, status))
                } else {
                    println("API: Driver login failed - invalid credentials for ${request.email}")
                    call.respond(AuthResponse(false, "Invalid email or password", null, null))
                }
            } catch (e: Exception) {
                println("API: Driver login error: ${e.message}")
                e.printStackTrace()
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        get("/driver/nearby") {
            val lat = call.parameters["lat"]?.toDoubleOrNull() ?: 0.0
            val lng = call.parameters["lng"]?.toDoubleOrNull() ?: 0.0
            val radius = call.parameters["radius"]?.toDoubleOrNull() ?: 5.0
            val drivers = getNearbyDriverLocations(lat, lng, radius)
            call.respond(drivers)
        }

        post("/driver/accept-delivery") {
            try {
                val params = call.receiveParameters()
                val driverId = params["driver_id"]
                val deliveryId = params["delivery_id"]
                
                if (driverId != null && deliveryId != null) {
                    val success = acceptDeliveryInDb(driverId, deliveryId)
                    if (success) {
                        call.respond(AuthResponse(true, "Delivery accepted successfully", driverId, null))
                    } else {
                        call.respond(AuthResponse(false, "Failed to accept delivery", null, null))
                    }
                } else {
                    call.respond(AuthResponse(false, "Missing driver_id or delivery_id", null, null))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Accept failed", null, null))
            }
        }

        get("/orders/status/{orderId}") {
            val orderId = call.parameters["orderId"]?.toIntOrNull()
            if (orderId == null) {
                call.respond(mapOf("success" to false, "message" to "Invalid Order ID"))
                return@get
            }
            
            val status = getDetailedOrderStatus(orderId)
            call.respond(status)
        }

        get("/customer/geocode") {
            val query = call.parameters["q"] ?: ""
            if (query.length < 3) {
                call.respond(emptyList<LocationSuggestion>())
                return@get
            }

            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&countrycodes=gh&addressdetails=1")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "FamekoBackend/1.0")
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val results: List<Map<String, Any>> = gson.fromJson(response, listType)
                
                val suggestions = results.map {
                    LocationSuggestion(
                        displayName = it["display_name"] as String,
                        latitude = it["lat"] as String,
                        longitude = it["lon"] as String,
                        name = (it["display_name"] as String).split(",")[0],
                        type = "address"
                    )
                }
                call.respond(suggestions)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(emptyList<LocationSuggestion>())
            }
        }

        post("/driver/register") {
            println("RECEIVED: Driver registration request started...")
            try {
                val multipartData = call.receiveMultipart()
                val driverData = mutableMapOf<String, String>()
                
                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            driverData[part.name ?: ""] = part.value
                        }
                        is PartData.FileItem -> {
                            // Only store filename if it actually exists
                            if (part.originalFileName != null && part.originalFileName!!.isNotEmpty()) {
                                driverData[part.name ?: ""] = part.originalFileName!!
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val driverId = registerDriverInDb(driverData)
                if (driverId != null) {
                    call.respond(AuthResponse(true, "Registration successful", driverId.toString(), driverData["full_name"]))
                } else {
                    call.respond(AuthResponse(false, "Registration failed", null, null))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        post("/driver/upload-document") {
            println("RECEIVED: Request to save document URL")
            try {
                val parameters = call.receiveParameters()
                val driverId = parameters["driver_id"]
                val docType = parameters["doc_type"]
                val fileUrl = parameters["file_url"]

                if (driverId != null && docType != null && fileUrl != null) {
                    println("SAVING CLOUDINARY URL to DB for driver $driverId ($docType)")
                    updateDriverDocument(driverId, docType, fileUrl)
                    call.respond(AuthResponse(true, "Document link saved successfully", driverId, null))
                } else {
                    val missing = mutableListOf<String>()
                    if (driverId == null) missing.add("driver_id")
                    if (docType == null) missing.add("doc_type")
                    if (fileUrl == null) missing.add("file_url")
                    println("ERROR: Missing data: ${missing.joinToString(", ")}")
                    call.respond(AuthResponse(false, "Missing data: ${missing.joinToString(", ")}", null, null))
                }
            } catch (e: Exception) {
                println("EXCEPTION during link save: ${e.message}")
                e.printStackTrace()
                call.respond(AuthResponse(false, "Failed to save link: ${e.message}", null, null))
            }
        }

        get("/driver/status/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(DriverStatusResponse(false, "UNKNOWN", emptyList()))
                return@get
            }
            try {
                val statusData = getDriverStatusFromDb(id)
                val success = statusData["success"] as? Boolean ?: false
                val statusStr = statusData["status"]?.toString() ?: "UNKNOWN"
                val missingDocs = statusData["missingDocs"] as? List<String> ?: emptyList()
                
                call.respond(DriverStatusResponse(success, statusStr, missingDocs))
            } catch (e: Exception) {
                call.respond(DriverStatusResponse(false, "UNKNOWN", emptyList()))
            }
        }

        get("/driver/stats/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(com.example.famekodriver.core.domain.model.DriverStats()) // return default empty stats
                return@get
            }
            try {
                val stats = getDriverStatsFromDb(id)
                if (stats != null) {
                    call.respond(stats)
                } else {
                    call.respond(com.example.famekodriver.core.domain.model.DriverStats())
                }
            } catch (e: Exception) {
                call.respond(com.example.famekodriver.core.domain.model.DriverStats())
            }
        }

        post("/driver/update-online-status") {
            try {
                val params = call.receiveParameters()
                val driverId = params["driver_id"] ?: ""
                val isOnline = params["is_online"]?.toBoolean() ?: false
                
                updateDriverOnlineStatusInDb(driverId, isOnline)
                call.respond(AuthResponse(true, "Status updated", driverId, null))
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Update failed", null, null))
            }
        }

        post("/driver/update-location") {
            try {
                val params = call.receiveParameters()
                val driverId = params["driver_id"] ?: ""
                val lat = params["latitude"]?.toDoubleOrNull() ?: 0.0
                val lng = params["longitude"]?.toDoubleOrNull() ?: 0.0
                val bearing = params["bearing"]?.toFloatOrNull() ?: 0f
                
                updateDriverLocationInDb(driverId, lat, lng, bearing)
                call.respond(AuthResponse(true, "Location updated", driverId, null))
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Update failed", null, null))
            }
        }

        route("/chat") {
            post("/send") {
                val message = call.receive<Message>()
                val id = saveMessage(message)
                if (id != null) {
                    val savedMessage = message.copy(id = id)
                    // Broadcast to recipient
                    val recipientId = if (message.senderType == "driver") "cust_${message.conversationId}" else "driver_${message.conversationId}" 
                    sendToUser(recipientId, "NEW_MESSAGE", savedMessage)
                    call.respond(savedMessage)
                } else {
                    call.respond(io.ktor.http.HttpStatusCode.InternalServerError)
                }
            }

            get("/history/{convId}") {
                val convId = call.parameters["convId"]?.toIntOrNull() ?: return@get call.respond(emptyList<Message>())
                call.respond(getMessageHistory(convId))
            }
        }

        route("/wallet") {
            get("/balance/{driverId}") {
                val driverId = call.parameters["driverId"]?.toIntOrNull() ?: return@get call.respond(AuthResponse(false, "Invalid ID", null, null))
                val balance = getWalletBalance(driverId)
                call.respond(mapOf("balance" to balance))
            }

            get("/transactions/{driverId}") {
                val driverId = call.parameters["driverId"]?.toIntOrNull() ?: return@get call.respond(emptyList<Map<String, Any>>())
                call.respond(getWalletTransactions(driverId))
            }
        }

        post("/route/calculate") {
            val request = call.receive<RouteRequest>()
            // Native straight-line routing simulation
            val distance = calculateDistance(request.start.lat, request.start.lng, request.end.lat, request.end.lng)
            val duration = (distance / 40.0) * 60.0 // 40 km/h average
            
            val response = RouteResponse(
                fromCache = false,
                routeCoords = listOf(
                    listOf(request.start.lng, request.start.lat),
                    listOf(request.end.lng, request.end.lat)
                ),
                distanceM = (distance * 1000).toInt(),
                etaMin = duration,
                vehicleType = request.vehicleType,
                routeType = request.routeType,
                waypoints = 2,
                computedAt = java.time.Instant.now().toString()
            )
            call.respond(response)
        }

        route("/safety") {
            post("/sos") {
                val req = call.receive<SOSRequest>()
                saveSOSAlert(req)
                // In a production app, trigger real-time alerts to dispatchers here
                call.respond(AuthResponse(true, "SOS Alert received. Help is on the way.", req.driverId, null))
            }

            get("/share-trip/{driverId}/{deliveryId}") {
                val driverId = call.parameters["driverId"]
                val deliveryId = call.parameters["deliveryId"]
                // In a real app, generate a unique tracking token and store it
                val shareUrl = "https://fameko-tracking.web.app/trip/$deliveryId"
                call.respond(ShareTripResponse(shareUrl, deliveryId ?: "", System.currentTimeMillis() + 3600000))
            }
        }

        route("/demand") {
            get("/heatmap") {
                // In a real app, query the DB for recent orders pickup locations
                val heatmap = listOf(
                    HeatmapPoint(5.6177, -0.1733, 0.9), // Accra Mall
                    HeatmapPoint(5.5500, -0.1900, 0.7), // James Town
                    HeatmapPoint(5.6000, -0.2200, 0.5), // Kaneshie
                    HeatmapPoint(5.6500, -0.1800, 0.8)  // Legon
                )
                call.respond(heatmap)
            }

            get("/surge") {
                val surge = calculateCurrentSurge()
                call.respond(surge)
            }
        }

        route("/orders") {
            post("/create") {
                println("API: Received order creation request")
                try {
                    val req = call.receive<OrderCreateRequest>()
                    println("API: Order for customer ${req.customerId} to ${req.dropoffLocation}")
                    
                    val orderId = createOrderAndDelivery(req)
                    if (orderId != null) {
                        println("API: Order created with ID $orderId. Finding drivers...")
                        // DISPATCH INTELLIGENCE: Find closest drivers
                        val nearbyDrivers = findNearbyDrivers(req.pickupLat, req.pickupLng, radiusKm = 5.0, limit = 5)
                        println("API: Found ${nearbyDrivers.size} nearby drivers")
                        
                        val delivery = getDeliveryByOrderId(orderId)
                        if (delivery != null) {
                            // Push to nearby drivers only
                            nearbyDrivers.forEach { driverId ->
                                sendToUser(driverId.toString(), "NEW_DELIVERY", delivery)
                            }
                        }
                        
                        call.respond(mapOf("success" to true, "orderId" to orderId))
                    } else {
                        println("API: Failed to create order in DB")
                        call.respond(mapOf("success" to false, "message" to "Failed to create order"))
                    }
                } catch (e: Exception) {
                    println("API: Error creating order: ${e.message}")
                    e.printStackTrace()
                    call.respond(mapOf("success" to false, "message" to e.message))
                }
            }

            post("/cancel/{orderId}") {
                val orderId = call.parameters["orderId"]?.toIntOrNull()
                if (orderId != null) {
                    try {
                        cancelOrderInDb(orderId)
                        call.respond(mapOf("success" to true, "message" to "Order cancelled successfully"))
                    } catch (e: Exception) {
                        call.respond(mapOf("success" to false, "message" to (e.message ?: "Failed to cancel order")))
                    }
                } else {
                    call.respond(mapOf("success" to false, "message" to "Invalid order ID"))
                }
            }
        }
    }
}

private fun cancelOrderInDb(orderId: Int) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        conn.autoCommit = false
        try {
            // Update order status
            val orderStmt = conn.prepareStatement("UPDATE orders SET status = 'Cancelled' WHERE id = ?")
            orderStmt.setInt(1, orderId)
            orderStmt.executeUpdate()

            // Update delivery status if exists
            val deliveryStmt = conn.prepareStatement("UPDATE deliveries SET status = 'CANCELLED' WHERE order_id = ?")
            deliveryStmt.setInt(1, orderId)
            deliveryStmt.executeUpdate()

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        }
    }
}

data class OrderCreateRequest(
    val customerId: String,
    val pickupLocation: String,
    val dropoffLocation: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropoffLat: Double,
    val dropoffLng: Double,
    val distanceKm: Double,
    val estimatedFare: Double,
    val durationMin: Double
)

private fun findNearbyDrivers(lat: Double, lng: Double, radiusKm: Double, limit: Int): List<Int> {
    val drivers = mutableListOf<Int>()
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            // Try PostGIS first
            val spatialSql = """
                SELECT driver_id 
                FROM driver_stats 
                WHERE is_online = true 
                AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                LIMIT ?
            """.trimIndent()
            
            try {
                val stmt = conn.prepareStatement(spatialSql)
                stmt.setDouble(1, lng)
                stmt.setDouble(2, lat)
                stmt.setDouble(3, radiusKm * 1000)
                stmt.setDouble(4, lng)
                stmt.setDouble(5, lat)
                stmt.setInt(6, limit)
                
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    drivers.add(rs.getInt("driver_id"))
                }
            } catch (spatialError: Exception) {
                // Fallback to simple Haversine math if PostGIS is not available
                println("PostGIS not available, falling back to simple math search: ${spatialError.message}")
                val fallbackSql = """
                    SELECT driver_id, 
                    (6371 * acos(least(1.0, cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?)) + sin(radians(?)) * sin(radians(latitude))))) AS distance
                    FROM driver_stats
                    WHERE is_online = true 
                    AND latitude IS NOT NULL AND longitude IS NOT NULL
                    AND (6371 * acos(least(1.0, cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?)) + sin(radians(?)) * sin(radians(latitude))))) < ?
                    ORDER BY distance
                    LIMIT ?
                """.trimIndent()
                
                val stmt = conn.prepareStatement(fallbackSql)
                stmt.setDouble(1, lat)
                stmt.setDouble(2, lng)
                stmt.setDouble(3, lat)
                stmt.setDouble(4, lat)
                stmt.setDouble(5, lng)
                stmt.setDouble(6, lat)
                stmt.setDouble(7, radiusKm)
                stmt.setInt(8, limit)
                
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    drivers.add(rs.getInt("driver_id"))
                }
            }
        }
    } catch (e: Exception) {
        println("Error finding nearby drivers: ${e.message}")
        e.printStackTrace()
    }
    return drivers
}

private fun createOrderAndDelivery(req: OrderCreateRequest): Int? {
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            conn.autoCommit = false
            try {
                val orderSql = "INSERT INTO orders (customer_id, total_amount, shipping_name, shipping_address, shipping_phone, latitude, longitude, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'Pending')"
                val oStmt = conn.prepareStatement(orderSql, java.sql.Statement.RETURN_GENERATED_KEYS)
                oStmt.setInt(1, req.customerId.toInt())
                oStmt.setDouble(2, req.estimatedFare)
                oStmt.setString(3, "Customer")
                oStmt.setString(4, req.pickupLocation)
                oStmt.setString(5, "0000000000")
                oStmt.setDouble(6, req.pickupLat)
                oStmt.setDouble(7, req.pickupLng)
                
                oStmt.executeUpdate()
                val oRs = oStmt.generatedKeys
                if (!oRs.next()) return null
                val orderId = oRs.getInt(1)

                val deliverySql = """
                    INSERT INTO deliveries (order_id, pickup_location, dropoff_location, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km, estimated_duration_minutes, estimated_earnings, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """.trimIndent()
                val dStmt = conn.prepareStatement(deliverySql)
                dStmt.setInt(1, orderId)
                dStmt.setString(2, req.pickupLocation)
                dStmt.setString(3, req.dropoffLocation)
                dStmt.setDouble(4, req.pickupLat)
                dStmt.setDouble(5, req.pickupLng)
                dStmt.setDouble(6, req.dropoffLat)
                dStmt.setDouble(7, req.dropoffLng)
                dStmt.setDouble(8, req.distanceKm)
                dStmt.setInt(9, req.durationMin.toInt())
                dStmt.setDouble(10, req.estimatedFare * 0.8)
                dStmt.executeUpdate()

                conn.commit()
                return orderId
            } catch (e: Exception) {
                conn.rollback()
                println("Database error in createOrderAndDelivery: ${e.message}")
                throw e
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun getDeliveryByOrderId(orderId: Int): Delivery? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT * FROM deliveries WHERE order_id = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, orderId)
        val rs = stmt.executeQuery()
        if (rs.next()) {
            val dbStatus = rs.getString("status") ?: "PENDING"
            val deliveryStatus = try {
                DeliveryStatus.valueOf(dbStatus.uppercase())
            } catch (e: Exception) {
                DeliveryStatus.PENDING
            }
            
            return Delivery(
                id = rs.getInt("id").toString(),
                orderId = rs.getInt("order_id"),
                driverId = rs.getString("driver_id"),
                pickupLocation = rs.getString("pickup_location"),
                dropoffLocation = rs.getString("dropoff_location"),
                pickupLat = rs.getDouble("pickup_lat"),
                pickupLng = rs.getDouble("pickup_lng"),
                dropoffLat = rs.getDouble("dropoff_lat"),
                dropoffLng = rs.getDouble("dropoff_lng"),
                status = deliveryStatus,
                distanceKm = rs.getDouble("distance_km"),
                estimatedEarnings = rs.getDouble("estimated_earnings")
            )
        }
    }
    return null
}

private fun calculateCurrentSurge(): SurgeInfo {
    return try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            // Count pending deliveries
            val pendingOrders = conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM deliveries WHERE status = 'PENDING'")
                if (rs.next()) rs.getInt(1) else 0
            }

            // Count online drivers
            val onlineDrivers = conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM drivers WHERE is_online = true")
                if (rs.next()) rs.getInt(1) else 0
            }

            // Simple Logic: If pending orders > online drivers, trigger surge
            val multiplier = when {
                onlineDrivers == 0 && pendingOrders > 0 -> 2.0
                pendingOrders > onlineDrivers * 2 -> 1.8
                pendingOrders > onlineDrivers -> 1.4
                else -> 1.0
            }

            SurgeInfo(
                multiplier = multiplier,
                isActive = multiplier > 1.0,
                reason = if (multiplier > 1.0) "High Demand in your area" else null
            )
        }
    } catch (e: Exception) {
        SurgeInfo(1.0, false, null)
    }
}

private fun saveSOSAlert(req: SOSRequest) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "INSERT INTO sos_alerts (driver_id, latitude, longitude) VALUES (?, ?, ?)"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, req.driverId.toInt())
        stmt.setDouble(2, req.latitude)
        stmt.setDouble(3, req.longitude)
        stmt.executeUpdate()
    }
}

private fun saveMessage(msg: Message): Int? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "INSERT INTO messages (conversation_id, sender_type, sender_id, body) VALUES (?, ?, ?, ?)"
        val stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        stmt.setInt(1, msg.conversationId)
        stmt.setString(2, msg.senderType)
        stmt.setInt(3, msg.senderId)
        stmt.setString(4, msg.body)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        return if (rs.next()) rs.getInt(1) else null
    }
}

private fun getMessageHistory(convId: Int): List<Message> {
    val messages = mutableListOf<Message>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, convId)
        val rs = stmt.executeQuery()
        while (rs.next()) {
            messages.add(Message(
                id = rs.getInt("id"),
                conversationId = rs.getInt("conversation_id"),
                senderType = rs.getString("sender_type"),
                senderId = rs.getInt("sender_id"),
                body = rs.getString("body"),
                createdAt = rs.getTimestamp("created_at").toString(),
                read = rs.getBoolean("read")
            ))
        }
    }
    return messages
}

private fun getWalletBalance(driverId: Int): Double {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT balance FROM wallets WHERE driver_id = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, driverId)
        val rs = stmt.executeQuery()
        return if (rs.next()) rs.getDouble("balance") else 0.0
    }
}

private fun getDetailedOrderStatus(orderId: Int): Map<String, Any> {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = """
            SELECT o.status as order_status, d.status as delivery_status, 
                   dr.full_name, dr.phone, dr.vehicle_type, dr.rating,
                   ds.latitude, ds.longitude
            FROM orders o
            LEFT JOIN deliveries d ON o.id = d.order_id
            LEFT JOIN drivers dr ON d.driver_id = dr.id
            LEFT JOIN driver_stats ds ON dr.id = ds.driver_id
            WHERE o.id = ?
        """.trimIndent()
        
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, orderId)
        val rs = stmt.executeQuery()
        if (rs.next()) {
            val status = rs.getString("delivery_status") ?: rs.getString("order_status")
            return mapOf(
                "success" to true,
                "status" to status,
                "driverName" to (rs.getString("full_name") ?: ""),
                "driverPhone" to (rs.getString("phone") ?: ""),
                "driverVehicle" to (rs.getString("vehicle_type") ?: ""),
                "driverLat" to rs.getDouble("latitude"),
                "driverLng" to rs.getDouble("longitude"),
                "driverRating" to rs.getDouble("rating")
            )
        }
    }
    return mapOf("success" to false, "status" to "NOT_FOUND")
}

private fun acceptDeliveryInDb(driverId: String, deliveryId: String): Boolean {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        conn.autoCommit = false
        try {
            // 1. Get the order_id associated with this delivery
            val getOrderStmt = conn.prepareStatement("SELECT order_id FROM deliveries WHERE id = ?")
            getOrderStmt.setInt(1, deliveryId.toInt())
            val rs = getOrderStmt.executeQuery()
            val orderId = if (rs.next()) rs.getInt("order_id") else -1

            // 2. Update delivery status and assign driver
            val stmt = conn.prepareStatement("UPDATE deliveries SET driver_id = ?, status = 'ASSIGNED' WHERE id = ?")
            stmt.setInt(1, driverId.toInt())
            stmt.setInt(2, deliveryId.toInt())
            stmt.executeUpdate()

            // 3. Update order status to match
            if (orderId != -1) {
                val orderStmt = conn.prepareStatement("UPDATE orders SET status = 'Assigned' WHERE id = ?")
                orderStmt.setInt(1, orderId)
                orderStmt.executeUpdate()
            }

            conn.commit()
            return true
        } catch (e: Exception) {
            conn.rollback()
            println("Error in acceptDeliveryInDb: ${e.message}")
            return false
        }
    }
}

private fun getNearbyDriverLocations(lat: Double, lng: Double, radiusKm: Double): List<DriverLocation> {
    val drivers = mutableListOf<DriverLocation>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        try {
            // Try PostGIS spatial query first
            val sql = """
                SELECT driver_id, latitude, longitude, bearing 
                FROM driver_stats 
                WHERE is_online = true 
                AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            """.trimIndent()
            val stmt = conn.prepareStatement(sql)
            stmt.setDouble(1, lng) // Longitude first for ST_MakePoint
            stmt.setDouble(2, lat)
            stmt.setDouble(3, radiusKm * 1000)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                drivers.add(DriverLocation(
                    id = rs.getInt("driver_id").toString(),
                    latitude = rs.getDouble("latitude"),
                    longitude = rs.getDouble("longitude"),
                    bearing = rs.getFloat("bearing")
                ))
            }
        } catch (e: Exception) {
            println("Spatial query failed, falling back to math for nearby drivers: ${e.message}")
            // Fallback to Haversine formula if PostGIS/location column is missing
            val fallbackSql = """
                SELECT driver_id, latitude, longitude, bearing,
                (6371 * acos(least(1.0, cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?)) + sin(radians(?)) * sin(radians(latitude))))) AS distance
                FROM driver_stats
                WHERE is_online = true 
                AND latitude IS NOT NULL AND longitude IS NOT NULL
                AND (6371 * acos(least(1.0, cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?)) + sin(radians(?)) * sin(radians(latitude))))) < ?
                ORDER BY distance
            """.trimIndent()
            val stmt = conn.prepareStatement(fallbackSql)
            stmt.setDouble(1, lat)
            stmt.setDouble(2, lng)
            stmt.setDouble(3, lat)
            stmt.setDouble(4, lat)
            stmt.setDouble(5, lng)
            stmt.setDouble(6, lat)
            stmt.setDouble(7, radiusKm)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                drivers.add(DriverLocation(
                    id = rs.getInt("driver_id").toString(),
                    latitude = rs.getDouble("latitude"),
                    longitude = rs.getDouble("longitude"),
                    bearing = rs.getFloat("bearing")
                ))
            }
        }
    }
    return drivers
}

private fun getWalletTransactions(driverId: Int): List<Map<String, Any>> {
    val txs = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = """
            SELECT t.* FROM wallet_transactions t
            JOIN wallets w ON t.wallet_id = w.id
            WHERE w.driver_id = ? ORDER BY t.created_at DESC
        """.trimIndent()
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, driverId)
        val rs = stmt.executeQuery()
        while (rs.next()) {
            txs.add(mapOf(
                "id" to rs.getInt("id"),
                "amount" to rs.getDouble("amount"),
                "type" to rs.getString("transaction_type"),
                "description" to (rs.getString("description") ?: ""),
                "date" to rs.getTimestamp("created_at").toString()
            ))
        }
    }
    return txs
}

// Database implementation functions
private fun registerCustomerInDb(req: CustomerRegisterRequest): Int? {
    var userId: Int? = null
    println("DB: Registering customer ${req.email}")
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            val sql = "INSERT INTO customers (name, email, phone, password, default_address, profile_picture, region) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, req.name)
            stmt.setString(2, req.email)
            stmt.setString(3, req.phone)
            stmt.setString(4, req.password)
            stmt.setString(5, req.address)
            stmt.setString(6, req.profilePicture ?: "")
            stmt.setString(7, req.region ?: "")
            
            val rs = stmt.executeQuery()
            if (rs.next()) {
                userId = rs.getInt(1)
                println("DB: Customer registered with ID: $userId")
            } else {
                println("DB: No ID returned after customer insertion")
            }
        }
    } catch (e: Exception) {
        println("DB: Customer registration error: ${e.message}")
        e.printStackTrace()
        throw e
    }
    return userId
}

private fun loginCustomerInDb(email: String, pass: String): Map<String, Any>? {
    println("DB: Attempting login for $email")
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            val sql = "SELECT id, name FROM customers WHERE email = ? AND password = ?"
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, email)
            stmt.setString(2, pass)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                println("DB: Login successful for $email")
                return mapOf("id" to rs.getInt("id"), "name" to rs.getString("name"))
            } else {
                println("DB: Login failed for $email - no match")
            }
        }
    } catch (e: Exception) {
        println("DB: Login error: ${e.message}")
        e.printStackTrace()
    }
    return null
}

private fun loginDriverInDb(email: String, pass: String): Map<String, Any>? {
    println("DB: Attempting driver login for $email")
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            val sql = "SELECT id, full_name, status FROM drivers WHERE email = ? AND password = ?"
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, email)
            stmt.setString(2, pass)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val driverId = rs.getInt("id")
                val fullName = rs.getString("full_name") ?: "Driver"
                val status = rs.getString("status") ?: "PENDING"
                
                println("DB: Driver login successful for $email, ID: $driverId")
                
                // Ensure driver_stats entry exists
                try {
                    val statsSql = "INSERT INTO driver_stats (driver_id) VALUES (?) ON CONFLICT (driver_id) DO NOTHING"
                    conn.prepareStatement(statsSql).use { statsStmt ->
                        statsStmt.setInt(1, driverId)
                        statsStmt.executeUpdate()
                    }
                } catch (statsErr: Exception) {
                    println("DB: Warning - could not ensure driver_stats for $driverId: ${statsErr.message}")
                }
                
                return mapOf(
                    "id" to driverId,
                    "name" to fullName,
                    "status" to status
                )
            } else {
                println("DB: Driver login failed for $email - no match found")
            }
        }
    } catch (e: Exception) {
        println("DB: Driver login error for $email: ${e.message}")
        e.printStackTrace()
    }
    return null
}

private fun registerDriverInDb(data: Map<String, String>): Int? {
    var driverId: Int? = null
    println("DB: Registering driver ${data["email"]}")
    try {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            val sql = """
                INSERT INTO drivers (
                    full_name, email, phone, region, password,
                    license_number, vehicle_type, vehicle_number, service_types,
                    profile_picture, license_image, id_front_image, id_back_image, vehicle_image, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """.trimIndent()
            
            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, data["full_name"])
            stmt.setString(2, data["email"])
            stmt.setString(3, data["phone"])
            stmt.setString(4, data["region"])
            stmt.setString(5, data["password"])
            stmt.setString(6, data["license_number"])
            stmt.setString(7, data["vehicle_type"])
            stmt.setString(8, data["vehicle_number"])
            stmt.setString(9, data["service_type"] ?: "both")
            stmt.setString(10, data["profile_pic"] ?: "")
            stmt.setString(11, data["drivers_license"] ?: "")
            stmt.setString(12, data["insurance_cert"] ?: "")
            stmt.setString(13, data["roadworthy_cert"] ?: "")
            stmt.setString(14, data["ghana_card"] ?: "")
            
            // If all docs are missing, mark as PENDING_DOCS
            val hasDocs = data.containsKey("profile_pic") || data.containsKey("drivers_license") || 
                         data.containsKey("insurance_cert") || data.containsKey("roadworthy_cert") || 
                         data.containsKey("ghana_card")
            stmt.setString(15, if (hasDocs) "PENDING" else "PENDING_DOCS")
            
            val rs = stmt.executeQuery()
            if (rs.next()) {
                driverId = rs.getInt(1)
                println("DB: Driver registered with ID: $driverId")
            } else {
                println("DB: No ID returned after driver insertion")
            }
        }
    } catch (e: Exception) {
        println("DB: Driver registration error: ${e.message}")
        e.printStackTrace()
    }
    return driverId
}

private fun updateDriverDocument(id: String, docType: String, fileName: String) {
    val column = when(docType) {
        "profile_pic" -> "profile_picture"
        "drivers_license" -> "license_image"
        "insurance_cert" -> "id_front_image" // reusing for now
        "roadworthy_cert" -> "id_back_image" // reusing for now
        "ghana_card" -> "vehicle_image" // reusing for now
        else -> null
    }
    
    if (column != null) {
        DatabaseInitializer.getDataSource().connection.use { conn ->
            conn.prepareStatement("UPDATE drivers SET $column = ? WHERE id = ?").use { stmt ->
                stmt.setString(1, fileName)
                stmt.setInt(2, id.toInt())
                stmt.executeUpdate()
            }
        }
    }
}

private fun getDriverStatusFromDb(id: String): Map<String, Any> {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT status, profile_picture, license_image, id_front_image, id_back_image, vehicle_image FROM drivers WHERE id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, id.toInt())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val status = rs.getString("status")
                val missingDocs = mutableListOf<String>()
                
                if (rs.getString("profile_picture").isNullOrEmpty()) missingDocs.add("profile_pic")
                if (rs.getString("license_image").isNullOrEmpty()) missingDocs.add("drivers_license")
                if (rs.getString("id_front_image").isNullOrEmpty()) missingDocs.add("insurance_cert")
                if (rs.getString("id_back_image").isNullOrEmpty()) missingDocs.add("roadworthy_cert")
                if (rs.getString("vehicle_image").isNullOrEmpty()) missingDocs.add("ghana_card")
                
                return mapOf("success" to true, "status" to status, "missingDocs" to missingDocs)
            }
        }
    }
    return mapOf("success" to false, "status" to "UNKNOWN", "missingDocs" to emptyList<String>())
}

private fun getDriverStatsFromDb(id: String): DriverStats? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT * FROM driver_stats WHERE driver_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, id.toInt())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return DriverStats(
                    isOnline = rs.getBoolean("is_online"),
                    activeDeliveries = rs.getInt("active_deliveries"),
                    completedToday = rs.getInt("completed_today"),
                    earningsToday = rs.getDouble("earnings_today"),
                    rating = rs.getDouble("rating"),
                    ratingCount = rs.getInt("rating_count"),
                    totalDeliveries = rs.getInt("total_deliveries"),
                    completionRate = rs.getInt("completion_rate"),
                    totalEarnings = rs.getDouble("total_earnings")
                )
            }
        }
    }
    return null
}

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371 // radius of earth in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

// Helper functions for Database access
private fun getAllDrivers(): List<Map<String, Any>> {
    val drivers = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val stmt = conn.prepareStatement("SELECT * FROM drivers ORDER BY id DESC")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            drivers.add(mapOf(
                "id" to rs.getInt("id"),
                "name" to rs.getString("full_name"),
                "email" to rs.getString("email"),
                "phone" to (rs.getString("phone") ?: ""),
                "status" to rs.getString("status"),
                "vehicle" to rs.getString("vehicle_type")
            ))
        }
    }
    return drivers
}

private fun getDriverById(id: String): Map<String, Any>? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT * FROM drivers WHERE id = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, id.toInt())
        val rs = stmt.executeQuery()
        if (rs.next()) {
            val meta = rs.metaData
            val map = mutableMapOf<String, Any>()
            for (i in 1..meta.columnCount) {
                val name = meta.getColumnName(i).lowercase()
                val value = rs.getObject(i)
                if (value != null) map[name] = value
            }
            // Force clean string values for status and ID
            map["status"] = rs.getString("status")?.uppercase()?.trim() ?: "PENDING"
            map["id"] = rs.getInt("id")
            map["full_name"] = rs.getString("full_name") ?: "Driver"
            map["name"] = map["full_name"]!!
            map["vehicle"] = rs.getString("vehicle_type") ?: ""
            return map
        }
    }
    return null
}

private fun getAllDeliveries(): List<Map<String, Any>> {
    val deliveries = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val stmt = conn.prepareStatement("""
            SELECT d.*, dr.full_name as driver_name 
            FROM deliveries d 
            LEFT JOIN drivers dr ON d.driver_id = dr.id
            ORDER BY d.id DESC
        """.trimIndent())
        val rs = stmt.executeQuery()
        while (rs.next()) {
            deliveries.add(mapOf(
                "id" to rs.getInt("id"),
                "pickup" to rs.getString("pickup_location"),
                "dropoff" to rs.getString("dropoff_location"),
                "status" to rs.getString("status"),
                "driver_name" to (rs.getString("driver_name") ?: "Unassigned")
            ))
        }
    }
    return deliveries
}

private fun getAllCustomers(): List<Map<String, Any>> {
    val customers = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val stmt = conn.prepareStatement("SELECT * FROM customers ORDER BY id DESC")
        val rs = stmt.executeQuery()
        while (rs.next()) {
            customers.add(mapOf(
                "id" to rs.getInt("id"),
                "name" to rs.getString("name"),
                "email" to rs.getString("email"),
                "phone" to (rs.getString("phone") ?: ""),
                "region" to (rs.getString("region") ?: ""),
                "profile_pic" to (rs.getString("profile_picture") ?: ""),
                "is_active" to rs.getBoolean("is_active"),
                "date_joined" to rs.getTimestamp("date_joined").toString()
            ))
        }
    }
    return customers
}

private fun getPricingConfig(): Map<String, Any> {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val stmt = conn.prepareStatement("SELECT * FROM pricing_config ORDER BY id DESC LIMIT 1")
        val rs = stmt.executeQuery()
        if (rs.next()) {
            return mapOf(
                "commission" to rs.getDouble("driver_commission_percent"),
                "peak_multiplier" to rs.getDouble("peak_multiplier"),
                "cancellation_fee" to rs.getDouble("cancellation_fee"),
                "wait_time_fee" to rs.getDouble("wait_time_fee"),
                "bonus" to rs.getDouble("bonus_per_delivery")
            )
        }
    }
    return mapOf(
        "commission" to 75.0,
        "peak_multiplier" to 1.5,
        "cancellation_fee" to 1.5,
        "wait_time_fee" to 0.15,
        "bonus" to 0.5
    )
}

private fun updateDriverStatus(id: String, status: String) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val stmt = conn.prepareStatement("UPDATE drivers SET status = ? WHERE id = ?")
        stmt.setString(1, status)
        stmt.setInt(2, id.toInt())
        stmt.executeUpdate()
    }
}

private fun updateDriverOnlineStatusInDb(driverId: String, isOnline: Boolean) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "UPDATE driver_stats SET is_online = ? WHERE driver_id = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setBoolean(1, isOnline)
        stmt.setInt(2, driverId.toInt())
        stmt.executeUpdate()
    }
}

private fun updateDriverLocationInDb(driverId: String, lat: Double, lng: Double, bearing: Float) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val spatialQuery = """
            UPDATE driver_stats 
            SET latitude = ?, longitude = ?, bearing = ?, 
                location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography 
            WHERE driver_id = ?
        """.trimIndent()
        
        try {
            conn.prepareStatement(spatialQuery).use { stmt ->
                stmt.setDouble(1, lat)
                stmt.setDouble(2, lng)
                stmt.setFloat(3, bearing)
                stmt.setDouble(4, lng)
                stmt.setDouble(5, lat)
                stmt.setInt(6, driverId.toInt())
                stmt.executeUpdate()
            }
        } catch (e: Exception) {
            val basicQuery = "UPDATE driver_stats SET latitude = ?, longitude = ?, bearing = ? WHERE driver_id = ?"
            conn.prepareStatement(basicQuery).use { stmt ->
                stmt.setDouble(1, lat)
                stmt.setDouble(2, lng)
                stmt.setFloat(3, bearing)
                stmt.setInt(4, driverId.toInt())
                stmt.executeUpdate()
            }
        }
    }
}

private fun getOnlineDriverLocations(): List<Map<String, Any>> {
    val locations = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = """
            SELECT ds.driver_id, ds.latitude, ds.longitude, d.full_name, d.vehicle_type, ds.is_online
            FROM driver_stats ds
            JOIN drivers d ON ds.driver_id = d.id
            WHERE ds.is_online = true
        """.trimIndent()
        val stmt = conn.prepareStatement(sql)
        val rs = stmt.executeQuery()
        while (rs.next()) {
            locations.add(mapOf(
                "id" to rs.getInt("driver_id"),
                "lat" to rs.getDouble("latitude"),
                "lng" to rs.getDouble("longitude"),
                "name" to rs.getString("full_name"),
                "vehicle" to (rs.getString("vehicle_type") ?: "Car")
            ))
        }
    }
    return locations
}

private fun getActiveSOSAlerts(): List<Map<String, Any>> {
    val alerts = mutableListOf<Map<String, Any>>()
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = """
            SELECT s.*, d.full_name, d.phone 
            FROM sos_alerts s
            JOIN drivers d ON s.driver_id = d.id
            WHERE s.status = 'ACTIVE'
            ORDER BY s.created_at DESC
        """.trimIndent()
        val stmt = conn.prepareStatement(sql)
        val rs = stmt.executeQuery()
        while (rs.next()) {
            alerts.add(mapOf(
                "id" to rs.getInt("id"),
                "driver_name" to rs.getString("full_name"),
                "driver_phone" to rs.getString("phone"),
                "lat" to rs.getDouble("latitude"),
                "lng" to rs.getDouble("longitude"),
                "time" to rs.getTimestamp("created_at").toString()
            ))
        }
    }
    return alerts
}

private fun resolveSOSAlert(id: Int) {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "UPDATE sos_alerts SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP WHERE id = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setInt(1, id)
        stmt.executeUpdate()
    }
}
