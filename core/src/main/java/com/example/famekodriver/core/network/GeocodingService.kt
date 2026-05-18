package com.example.famekodriver.core.network

import com.example.famekodriver.core.domain.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Retrofit service for fetching location suggestions and routes from the Python backend
 */
interface FamekoApiService {
    @GET("customer/geocode")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "gh"
    ): List<LocationSuggestion>

    @POST("route/calculate")
    suspend fun calculateRoute(
        @Body request: RouteRequest
    ): RouteResponse

    @POST("customer/register")
    suspend fun registerCustomer(
        @Body request: CustomerRegisterRequest
    ): AuthResponse

    @POST("customer/login")
    suspend fun loginCustomer(
        @Body request: LoginRequest
    ): AuthResponse

    @POST("driver/login")
    suspend fun loginDriver(
        @Body request: LoginRequest
    ): AuthResponse

    @Multipart
    @POST("driver/register")
    suspend fun registerDriver(
        @Part("full_name") name: RequestBody,
        @Part("email") email: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("password") password: RequestBody,
        @Part("license_number") licenseNumber: RequestBody,
        @Part("region") region: RequestBody,
        @Part("vehicle_type") vehicleType: RequestBody,
        @Part("service_type") serviceType: RequestBody,
        @Part("vehicle_number") vehicleNumber: RequestBody,
        @Part profile_pic: MultipartBody.Part? = null,
        @Part drivers_license: MultipartBody.Part? = null,
        @Part insurance_cert: MultipartBody.Part? = null,
        @Part roadworthy_cert: MultipartBody.Part? = null,
        @Part ghana_card: MultipartBody.Part? = null
    ): AuthResponse

    @FormUrlEncoded
    @POST("driver/upload-document")
    suspend fun uploadDriverDocument(
        @Field("driver_id") driverId: String,
        @Field("doc_type") docType: String,
        @Field("file_url") fileUrl: String
    ): AuthResponse

    @GET("driver/status/{id}")
    suspend fun getDriverStatus(
        @Path("id") driverId: String
    ): DriverStatusResponse

    @POST("chat/send")
    suspend fun sendMessage(
        @Body message: Message
    ): Message

    @GET("chat/history/{convId}")
    suspend fun getChatHistory(
        @Path("convId") convId: Int
    ): List<Message>

    @GET("wallet/balance/{driverId}")
    suspend fun getWalletBalance(
        @Path("driverId") driverId: String
    ): Map<String, Double>

    @GET("wallet/transactions/{driverId}")
    suspend fun getWalletTransactions(
        @Path("driverId") driverId: String
    ): List<Map<String, Any>>

    @POST("safety/sos")
    suspend fun triggerSOS(
        @Body request: SOSRequest
    ): AuthResponse

    @GET("safety/share-trip/{driverId}/{deliveryId}")
    suspend fun getShareableTripLink(
        @Path("driverId") driverId: String,
        @Path("deliveryId") deliveryId: String
    ): ShareTripResponse

    @GET("demand/heatmap")
    suspend fun getHeatmapData(): List<HeatmapPoint>

    @GET("demand/surge")
    suspend fun getCurrentSurge(): SurgeInfo

    @POST("orders/create")
    suspend fun createOrder(
        @Body request: OrderCreateRequest
    ): Map<String, Any>

    @GET("orders/status/{orderId}")
    suspend fun getOrderStatus(
        @Path("orderId") orderId: Int
    ): OrderStatusResponse

    @POST("orders/cancel/{orderId}")
    suspend fun cancelOrder(
        @Path("orderId") orderId: Int
    ): Map<String, Any>

    @GET("driver/nearby")
    suspend fun getNearbyDrivers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 5.0
    ): List<DriverLocation>
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

data class OrderStatusResponse(
    val success: Boolean,
    val status: String,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverVehicle: String? = null,
    val driverLat: Double? = null,
    val driverLng: Double? = null,
    val driverRating: Double? = null
)

data class DriverStatusResponse(
    val success: Boolean,
    val status: String, // "PENDING", "APPROVED", "REJECTED"
    val missingDocs: List<String>
)
