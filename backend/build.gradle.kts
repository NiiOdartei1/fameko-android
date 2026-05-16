plugins {
    kotlin("jvm")
    alias(libs.plugins.ktor)
}

group = "com.example.famekodriver"
version = "1.0.0"

application {
    mainClass.set("com.example.famekodriver.backend.ApplicationKt")
}

dependencies {
    implementation(project(":shared-models"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.thymeleaf)
    implementation(libs.ktor.server.host.common)
    implementation(libs.logback.classic)
    implementation(libs.hikaricp)
    
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.h2database:h2:2.2.224")
}

kotlin {
    jvmToolchain(17)
}
