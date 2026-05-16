package com.example.famekodriver.core.domain.model

data class Conversation(
    val id: Int,
    val deliveryId: Int?,
    val customerId: Int,
    val driverId: Int?,
    val createdAt: String,
    val lastMessageAt: String?
)

data class Message(
    val id: Int,
    val conversationId: Int,
    val senderType: String,
    val senderId: Int,
    val body: String,
    val createdAt: String,
    val read: Boolean
)
