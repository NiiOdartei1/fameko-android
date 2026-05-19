package com.example.famekodriver.core.network

/**
 * Configuration for the PostgreSQL database hosted on Railway.
 */
object DatabaseConfig {
    // Remote Database configuration from Railway
    const val DB_HOST = "autorack.proxy.rlwy.net"
    const val DB_PORT = 12188
    const val DB_NAME = "railway"
    const val DB_USER = "postgres"
    const val DB_PASS = "QqaHpNvRfmatGijHxHOgRVWiDdHCqQlL"

    fun getJdbcUrl(): String {
        // Railway External Connection URL
        return "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?sslmode=disable"
    }

    fun getDriverClassName(): String {
        return "org.postgresql.Driver"
    }
}
