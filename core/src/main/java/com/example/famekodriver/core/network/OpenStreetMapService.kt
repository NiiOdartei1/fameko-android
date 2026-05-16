package com.example.famekodriver.core.network

import com.example.famekodriver.core.domain.model.LocationSuggestion
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Native Android implementation of the Geocoding and Routing logic
 */
interface OpenStreetMapService {
    
    // Direct call to Nominatim (Search)
    @GET("https://nominatim.openstreetmap.org/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "gh",
        @Header("User-Agent") userAgent: String = "FamekoAndroidApp_Native_v1.0"
    ): List<LocationSuggestion>

    // Direct call to OSRM (Road Routing)
    @GET
    suspend fun getRoute(@Url url: String): OsrmResponse
}

data class OsrmResponse(
    @SerializedName("routes") val routes: List<OsrmRoute>,
    @SerializedName("code") val code: String
)

data class OsrmRoute(
    @SerializedName("geometry") val geometry: OsrmGeometry,
    @SerializedName("distance") val distance: Double, // meters
    @SerializedName("duration") val duration: Double  // seconds
)

data class OsrmGeometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>, // [[lng, lat], ...]
    @SerializedName("type") val type: String
)
