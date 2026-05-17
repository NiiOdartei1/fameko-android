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
                val pendingCount = drivers.count { it["status"] == "PENDING" }
                call.respond(ThymeleafContent("admin_dashboard", mapOf(
                    "drivers" to drivers,
                    "deliveries" to deliveries,
                    "pendingCount" to pendingCount,
                    "activePage" to "dashboard"
                )))
            }

            get("/drivers") {
                val drivers = getAllDrivers()
                call.respond(ThymeleafContent("admin_drivers", mapOf(
                    "drivers" to drivers,
                    "activePage" to "drivers"
                )))
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
                call.respondRedirect("/admin/dashboard")
            }
            
            post("/reject/{id}") {
                val id = call.parameters["id"]
                if (id != null) {
                    updateDriverStatus(id, "REJECTED")
                }
                call.respondRedirect("/admin/dashboard")
            }
        }

        // Native API for mobile apps (matching Retrofit paths)
        post("/customer/register") {
            try {
                val request = call.receive<CustomerRegisterRequest>()
                val userId = registerCustomerInDb(request)
                if (userId != null) {
                    call.respond(AuthResponse(true, "Registration successful", userId.toString(), request.name))
                } else {
                    call.respond(AuthResponse(false, "Registration failed", null, null))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        post("/customer/login") {
            try {
                val request = call.receive<LoginRequest>()
                val user = loginCustomerInDb(request.email, request.password)
                if (user != null) {
                    call.respond(AuthResponse(true, "Login successful", user["id"].toString(), user["name"].toString()))
                } else {
                    call.respond(AuthResponse(false, "Invalid email or password", null, null))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        post("/driver/login") {
            try {
                val request = call.receive<LoginRequest>()
                val driver = loginDriverInDb(request.email, request.password)
                if (driver != null) {
                    call.respond(AuthResponse(true, "Login successful", driver["id"].toString(), driver["name"].toString(), driver["status"].toString()))
                } else {
                    call.respond(AuthResponse(false, "Invalid email or password", null, null))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Unknown error", null, null))
            }
        }

        get("/customer/geocode") {
            // Native geocoding placeholder
            val query = call.parameters["q"] ?: ""
            // Return some mock suggestions for Ghana
            val suggestions = listOf(
                LocationSuggestion("Mock: $query", "5.6037", "-0.1870"),
                LocationSuggestion("Accra Mall", "5.6177", "-0.1733")
            )
            call.respond(suggestions)
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
            try {
                val multipartData = call.receiveMultipart()
                var driverId: String? = null
                var docType: String? = null
                var fileName: String? = null

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "driver_id") driverId = part.value
                            if (part.name == "doc_type") docType = part.value
                        }
                        is PartData.FileItem -> {
                            fileName = part.originalFileName
                            // Save file bytes in real app
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (driverId != null && docType != null && fileName != null) {
                    updateDriverDocument(driverId!!, docType!!, fileName!!)
                    call.respond(AuthResponse(true, "Document uploaded successfully", driverId, null))
                } else {
                    call.respond(AuthResponse(false, "Missing upload data", null, null))
                }
            } catch (e: Exception) {
                call.respond(AuthResponse(false, e.message ?: "Upload failed", null, null))
            }
        }

        get("/driver/status/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(AuthResponse(false, "Missing ID", null, null))
            val statusData = getDriverStatusFromDb(id)
            call.respond(statusData)
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
    }
}

// Database implementation functions
private fun registerCustomerInDb(req: CustomerRegisterRequest): Int? {
    var userId: Int? = null
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "INSERT INTO customers (name, email, phone, password, default_address) VALUES (?, ?, ?, ?, ?)"
        val stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
        stmt.setString(1, req.name)
        stmt.setString(2, req.email)
        stmt.setString(3, req.phone)
        stmt.setString(4, req.password) // In real app, hash this
        stmt.setString(5, req.address)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        if (rs.next()) {
            userId = rs.getInt(1)
        }
    }
    return userId
}

private fun loginCustomerInDb(email: String, pass: String): Map<String, Any>? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT id, name FROM customers WHERE email = ? AND password = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setString(1, email)
        stmt.setString(2, pass)
        val rs = stmt.executeQuery()
        if (rs.next()) {
            return mapOf("id" to rs.getInt("id"), "name" to rs.getString("name"))
        }
    }
    return null
}

private fun loginDriverInDb(email: String, pass: String): Map<String, Any>? {
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = "SELECT id, full_name, status FROM drivers WHERE email = ? AND password = ?"
        val stmt = conn.prepareStatement(sql)
        stmt.setString(1, email)
        stmt.setString(2, pass)
        val rs = stmt.executeQuery()
        if (rs.next()) {
            return mapOf(
                "id" to rs.getInt("id"), 
                "name" to rs.getString("full_name"),
                "status" to rs.getString("status")
            )
        }
    }
    return null
}

private fun registerDriverInDb(data: Map<String, String>): Int? {
    var driverId: Int? = null
    DatabaseInitializer.getDataSource().connection.use { conn ->
        val sql = """
            INSERT INTO drivers (
                full_name, email, phone, region, password,
                license_number, vehicle_type, vehicle_number, service_types,
                profile_picture, license_image, id_front_image, id_back_image, vehicle_image, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        val stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
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
        stmt.setString(12, data["ghana_card"] ?: "") // id_front_image
        stmt.setString(13, "") // id_back_image placeholder
        stmt.setString(14, "") // vehicle_image placeholder
        
        // If all docs are missing, mark as PENDING_DOCS
        val hasDocs = data.containsKey("profile_pic") || data.containsKey("drivers_license")
        stmt.setString(15, if (hasDocs) "PENDING" else "PENDING_DOCS")
        
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        if (rs.next()) {
            driverId = rs.getInt(1)
        }
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
        conn.prepareStatement("SELECT status, profile_picture, license_image FROM drivers WHERE id = ?").use { stmt ->
            stmt.setInt(1, id.toInt())
            val rs = stmt.executeQuery()
            if (rs.next()) {
                val status = rs.getString("status")
                val missingDocs = mutableListOf<String>()
                if (rs.getString("profile_picture").isNullOrEmpty()) missingDocs.add("profile_pic")
                if (rs.getString("license_image").isNullOrEmpty()) missingDocs.add("drivers_license")
                
                return mapOf("success" to true, "status" to status, "missingDocs" to missingDocs)
            }
        }
    }
    return mapOf("success" to false, "status" to "UNKNOWN", "missingDocs" to emptyList<String>())
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
