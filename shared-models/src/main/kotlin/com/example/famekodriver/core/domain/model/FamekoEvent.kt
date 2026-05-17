package com.example.famekodriver.core.domain.model

sealed class FamekoEvent {
    data class NewDeliveryRequest(val delivery: Delivery) : FamekoEvent()
    data class DeliveryStatusChanged(val deliveryId: String, val status: DeliveryStatus) : FamekoEvent()
    data class NewMessage(val message: Message) : FamekoEvent()
    data object Ping : FamekoEvent()
}

data class WebSocketMessage(
    val type: String,
    val payload: String
)
