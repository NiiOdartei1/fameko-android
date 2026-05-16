package com.example.famekodriver.core.domain.model

data class Driver(
    val id: Int,
    val fullName: String,
    val email: String,
    val phone: String,
    val region: String,
    val licenseNumber: String,
    val vehicleType: String?,
    val vehicleNumber: String?,
    val status: String,
    val isOnline: Boolean,
    val rating: Double,
    val profilePicture: String? = null
)

data class Wallet(
    val id: Int,
    val driverId: Int,
    val balance: Double,
    val totalCredited: Double,
    val totalDebitted: Double
)

data class WalletTransaction(
    val id: Int,
    val walletId: Int,
    val deliveryId: Int?,
    val transactionType: String,
    val amount: Double,
    val description: String?,
    val status: String,
    val createdAt: String
)
