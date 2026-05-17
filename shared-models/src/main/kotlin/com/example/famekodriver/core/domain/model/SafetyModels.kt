package com.example.famekodriver.core.domain.model

data class SOSRequest(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class ShareTripResponse(
    val shareUrl: String,
    val deliveryId: String,
    val expiresAt: Long
)
