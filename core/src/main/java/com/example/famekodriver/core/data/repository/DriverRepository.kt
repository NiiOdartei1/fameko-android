package com.example.famekodriver.core.data.repository

import com.example.famekodriver.core.domain.model.*
import com.example.famekodriver.core.network.DatabaseConfig
import com.example.famekodriver.core.network.NetworkClient
import com.example.famekodriver.core.network.DriverStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Repository for driver-related database operations.
 */
class DriverRepository {

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
            if (response.success && response.userId != null) {
                // In a real app, we'd fetch the full driver object here
                // For now, construct a minimal one from the auth response
                Result.success(Driver(
                    id = response.userId.toInt(),
                    fullName = response.userName ?: "Driver",
                    email = email,
                    phone = "",
                    region = "",
                    licenseNumber = "",
                    vehicleType = "Car",
                    vehicleNumber = "",
                    status = "APPROVED", // Default to approved for test login
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
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register a new customer
     */
    suspend fun customerRegister(name: String, email: String, phone: String, address: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = CustomerRegisterRequest(name, email, phone, address, password)
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
                    val query = "INSERT INTO customers (name, email, phone, default_address, password) VALUES (?, ?, ?, ?, ?)"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, name)
                        stmt.setString(2, email)
                        stmt.setString(3, phone)
                        stmt.setString(4, address)
                        stmt.setString(5, password)
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
            android.util.Log.e("FamekoRepo", "Driver API Registration failed, falling back to JDBC", e)
            // Fallback to JDBC if API is not available
            try {
                ensureDriverLoaded()
                DriverManager.getConnection(
                    DatabaseConfig.getJdbcUrl(),
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASS,
                ).use { connection ->
                    connection.autoCommit = false
                    try {
                        val query = """
                            INSERT INTO drivers (
                                full_name, email, phone, password, license_number, 
                                region, vehicle_type, service_types, vehicle_number, status
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_DOCS') RETURNING id
                        """.trimIndent()
                        
                        val driverId = connection.prepareStatement(query).use { stmt ->
                            stmt.setString(1, name)
                            stmt.setString(2, email)
                            stmt.setString(3, phone)
                            stmt.setString(4, password)
                            stmt.setString(5, licenseNumber)
                            stmt.setString(6, region)
                            stmt.setString(7, vehicleType)
                            stmt.setString(8, serviceType)
                            stmt.setString(9, vehicleNumber)
                            val rs = stmt.executeQuery()
                            if (rs.next()) rs.getInt(1) else throw Exception("Failed to create driver record")
                        }

                        // 2. Initialize driver stats
                        val statsQuery = "INSERT INTO driver_stats (driver_id, is_online, active_deliveries, completed_today, earnings_today, rating) VALUES (?, false, 0, 0, 0.0, 5.0)"
                        connection.prepareStatement(statsQuery).use { stmt ->
                            stmt.setString(1, driverId.toString())
                            stmt.executeUpdate()
                        }
                        
                        connection.commit()
                        Result.success(Unit)
                    } catch (dbEx: Exception) {
                        connection.rollback()
                        android.util.Log.e("FamekoRepo", "JDBC Fallback failed", dbEx)
                        Result.failure(dbEx)
                    }
                }
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    suspend fun uploadDocument(driverId: String, docType: String, file: java.io.File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val idBody = driverId.toRequestBody(MultipartBody.FORM)
            val typeBody = docType.toRequestBody(MultipartBody.FORM)
            val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("document", file.name, reqFile)

            val response = NetworkClient.famekoApi.uploadDriverDocument(idBody, typeBody, part)
            if (response.success) Result.success(Unit)
            else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDriverStatus(driverId: String): Result<DriverStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.famekoApi.getDriverStatus(driverId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) { Result.failure(e) }
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
        } catch (e: Exception) { Result.failure(e) }
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
        } catch (e: Exception) { Result.failure(e) }
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
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateLocation(driverId: String, lat: Double, lng: Double, bearing: Float): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDriverLoaded()
            DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(),
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASS,
            ).use { connection ->
                val query = "UPDATE driver_stats SET latitude = ?, longitude = ?, bearing = ? WHERE driver_id = ?"
                connection.prepareStatement(query).use { stmt ->
                    stmt.setDouble(1, lat)
                    stmt.setDouble(2, lng)
                    stmt.setFloat(3, bearing)
                    stmt.setString(4, driverId)
                    stmt.executeUpdate()
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun calculateRoute(request: RouteRequest): Result<RouteResponse> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkClient.routingApi.calculateRoute(request)
            Result.success(response)
        } catch (e: Exception) {
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
                        INSERT INTO orders (customer_id, total_amount, status, latitude, longitude, payment_method) 
                        VALUES (?, ?, 'Pending', ?, ?, 'Cash on Delivery') RETURNING id
                    """.trimIndent()
                    
                    val orderId = connection.prepareStatement(orderQuery).use { stmt ->
                        stmt.setInt(1, customerId.toInt())
                        stmt.setDouble(2, estimatedFare)
                        stmt.setDouble(3, dropoffLat)
                        stmt.setDouble(4, dropoffLng)
                        val rs = stmt.executeQuery()
                        if (rs.next()) rs.getInt(1) else throw Exception("Failed to create order")
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
                    throw e
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FamekoRepo", "Order creation failed", e)
            Result.failure(e)
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
            } catch (e: Exception) { 
                DeliveryStatus.PENDING 
            },
            distanceKm = getDouble("distance_km"),
            estimatedEarnings = getDouble("estimated_earnings"),
            customerName = getString("customer_name"),
            customerAddress = getString("customer_address")
        )
    }
}
