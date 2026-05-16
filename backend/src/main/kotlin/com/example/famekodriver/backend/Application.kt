package com.example.famekodriver.backend

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.example.famekodriver.backend.plugins.*
import com.example.famekodriver.backend.db.DatabaseInitializer

fun main() {
    Runtime.getRuntime().addShutdownHook(Thread {
        println("JVM is shutting down...")
    })
    try {
        println("Starting backend application...")
        DatabaseInitializer.init()
        println("Database initialized successfully.")
        println("Starting Ktor server on port 8080...")
        embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
            .start(wait = true)
    } catch (e: Exception) {
        println("CRITICAL ERROR during startup: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}

fun Application.module() {
    configureSerialization()
    configureTemplating()
    configureSecurity()
    configureRouting()
}
