package com.example.famekodriver.core.domain.model

data class DriverStats(
    val isOnline: Boolean = false,
    val activeDeliveries: Int = 0,
    val completedToday: Int = 0,
    val earningsToday: Double = 0.0,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val totalDeliveries: Int = 0,
    val completionRate: Int = 0,
    val totalEarnings: Double = 0.0
)
