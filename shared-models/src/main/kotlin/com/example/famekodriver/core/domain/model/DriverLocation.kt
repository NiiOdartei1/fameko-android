package com.example.famekodriver.core.domain.model

data class DriverLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val isOnline: Boolean = true,
    val isAvailable: Boolean = true
)
