package com.example.famekodriver.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap
import com.example.famekodriver.core.domain.model.WebSocketMessage
import com.google.gson.Gson
import kotlin.time.Duration.Companion.seconds

val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
private val gson = Gson()

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws/{userId}") {
            val userId = call.parameters["userId"] ?: "anonymous"
            sessions[userId] = this
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        // Handle incoming messages if needed
                    }
                }
            } catch (e: Exception) {
                println("WebSocket error for $userId: ${e.localizedMessage}")
            } finally {
                sessions.remove(userId)
            }
        }
    }
}

suspend fun broadcastToDrivers(type: String, payload: Any) {
    val message = WebSocketMessage(type, gson.toJson(payload))
    val frame = Frame.Text(gson.toJson(message))
    sessions.values.forEach { session ->
        try {
            session.send(frame)
        } catch (e: Exception) {
            // Log error
        }
    }
}
suspend fun sendToUser(userId: String, type: String, payload: Any) {
    val session = sessions[userId] ?: return
    val message = WebSocketMessage(type, gson.toJson(payload))
    try {
        session.send(Frame.Text(gson.toJson(message)))
    } catch (e: Exception) {
        sessions.remove(userId)
    }
}
