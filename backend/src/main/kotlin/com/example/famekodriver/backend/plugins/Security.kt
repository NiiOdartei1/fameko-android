package com.example.famekodriver.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    install(Authentication) {
        form("auth-form") {
            userParamName = "username"
            passwordParamName = "password"
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "admin123") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
            challenge {
                // In a real app, redirect to login page
            }
        }
    }
}
