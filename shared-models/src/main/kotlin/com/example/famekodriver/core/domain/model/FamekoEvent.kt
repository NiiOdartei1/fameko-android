package com.example.famekodriver.core.domain.model

sealed class FamekoEvent {
    data class NewDeliveryRequest(val delivery: Delivery) : FamekoEvent()
    data class DeliveryStatusChanged(val deliveryId: String, val status: DeliveryStatus) : FamekoEvent()
    data class NewMessage(val message: Message) : FamekoEvent()
    data class IncomingCall(val callId: String, val callerName: String, val customerId: Int? = null, val driverId: Int? = null) : FamekoEvent()
    data class CallAccepted(val callId: String) : FamekoEvent()
    data class CallRejected(val callId: String, val reason: String?) : FamekoEvent()
    data class CallEnded(val callId: String) : FamekoEvent()
    data object Ping : FamekoEvent()
}

data class WebSocketMessage(
    val type: String,
    val payload: String
)
