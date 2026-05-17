package com.example.famekodriver.core.domain.model

data class SurgeInfo(
    val multiplier: Double,
    val isActive: Boolean,
    val reason: String?,
    val region: String? = "Accra"
)
