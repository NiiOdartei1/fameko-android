package com.example.famekodriver.core.network

/**
 * Configuration for the PostgreSQL database.
 * Toggle between local and production by modifying these values.
 */
object DatabaseConfig {
    // Environment variables with fallbacks
    val USE_STANDALONE_DB = System.getenv("USE_STANDALONE_DB")?.toBoolean() ?: true

    // Remote Database configuration
    val DB_URL = System.getenv("DB_URL") ?: "jdbc:postgresql://switchyard.proxy.rlwy.net:26106/railway?ssl=true&sslmode=require"
    val DB_USER = System.getenv("DB_USER") ?: "postgres"
    val DB_PASS = System.getenv("DB_PASS") ?: "FQyDXSzYQfzSBTgnqxgOzWaSrQSAhLXa"

    fun getJdbcUrl(): String {
        return if (USE_STANDALONE_DB) {
            // Check if we are running on Android
            val isAndroid = try { System.getProperty("java.vendor")?.contains("Android") == true } catch (e: Exception) { false }
            
            if (isAndroid) {
                "" 
            } else {
                "jdbc:h2:./fameko;MODE=PostgreSQL;AUTO_SERVER=TRUE"
            }
        } else {
            DB_URL
        }
    }

    fun getDriverClassName(): String {
        return if (USE_STANDALONE_DB) "org.h2.Driver" else "org.postgresql.Driver"
    }
}
