package com.example.famekodriver.core.domain.model

data class CustomerRegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String?,
    val user_id: String?,
    val name: String?
)
