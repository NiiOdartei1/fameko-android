package com.example.famekodriver.core.domain.model

import com.google.gson.annotations.SerializedName

data class RouteRequest(
    val start: RouteLocation,
    val end: RouteLocation,
    @SerializedName("vehicle_type") val vehicleType: String = "car",
    @SerializedName("route_type") val routeType: String = "fastest"
)

data class RouteLocation(
    val lat: Double,
    val lng: Double
)

data class RouteResponse(
    @SerializedName("from_cache") val fromCache: Boolean,
    @SerializedName("route_coords") val routeCoords: List<List<Double>>,
    @SerializedName("distance_m") val distanceM: Int,
    @SerializedName("eta_min") val etaMin: Double,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("route_type") val routeType: String,
    val waypoints: Int,
    @SerializedName("computed_at") val computedAt: String
)