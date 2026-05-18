package com.example.famekodriver.core.domain.model

data class Order(
    val id: Int,
    val customerId: Int,
    val totalAmount: Double,
    val orderDate: String,
    val status: String,
    val shippingName: String,
    val shippingAddress: String,
    val shippingPhone: String,
    val latitude: Double?,
    val longitude: Double?,
    val paymentMethod: String,
    val paymentStatus: String
)

data class OrderItem(
    val id: Int,
    val orderId: Int,
    val itemName: String,
    val itemDescription: String?,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val status: String
)

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
