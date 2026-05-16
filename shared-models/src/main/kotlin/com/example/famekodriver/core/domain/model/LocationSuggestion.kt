package com.example.famekodriver.core.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a location suggestion from the backend
 */
data class LocationSuggestion(
    @SerializedName("display_name")
    val displayName: String,
    
    @SerializedName("lat")
    val latitude: String,
    
    @SerializedName("lon")
    val longitude: String
)
