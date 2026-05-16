package com.example.famekodriver.core.domain.model

data class Delivery(
    val id: String,
    val orderId: Int,
    val driverId: String?,
    val pickupLocation: String,
    val dropoffLocation: String,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val status: DeliveryStatus,
    val distanceKm: Double,
    val estimatedEarnings: Double,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val serviceType: String = "package_delivery",
    val createdAt: String? = null
)

enum class DeliveryStatus {
    PENDING,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
