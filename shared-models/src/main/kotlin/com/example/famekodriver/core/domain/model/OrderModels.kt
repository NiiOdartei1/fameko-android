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
