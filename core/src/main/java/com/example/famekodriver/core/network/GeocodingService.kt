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

    @Multipart
    @POST("driver/upload-document")
    suspend fun uploadDriverDocument(
        @Part("driver_id") driverId: RequestBody,
        @Part("doc_type") docType: RequestBody, // e.g., "profile_pic", "drivers_license"
        @Part document: MultipartBody.Part
    ): AuthResponse

    @GET("driver/status/{id}")
    suspend fun getDriverStatus(
        @Path("id") driverId: String
    ): DriverStatusResponse
}

data class DriverStatusResponse(
    val success: Boolean,
    val status: String, // "PENDING", "APPROVED", "REJECTED"
    val missingDocs: List<String>
)
