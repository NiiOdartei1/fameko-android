package com.example.famekodriver.core.network

/**
 * Configuration for the PostgreSQL database.
 * Toggle between local and production by modifying these values.
 */
object DatabaseConfig {
    // Set this to true to use a local file (standalone mode like Python/SQLite)
    // Set to false for remote PostgreSQL (Railway)
    const val USE_STANDALONE_DB = true

    // Railway Production configuration (for reference)
    const val DB_HOST = "switchyard.proxy.rlwy.net"
    const val DB_PORT = 26106
    const val DB_NAME = "railway"
    const val DB_USER = "postgres"
    const val DB_PASS = "FQyDXSzYQfzSBTgnqxgOzWaSrQSAhLXa"

    fun getJdbcUrl(): String {
        return if (USE_STANDALONE_DB) {
            // Check if we are running on Android
            val isAndroid = try { System.getProperty("java.vendor")?.contains("Android") == true } catch (e: Exception) { false }
            
            if (isAndroid) {
                // Android should NOT try to connect to a local H2 file via JDBC
                // It must use the API. We return an empty string to trigger an error if JDBC is attempted.
                "" 
            } else {
                // This creates 'fameko.mv.db' in your PC's project folder
                "jdbc:h2:./fameko;MODE=PostgreSQL;AUTO_SERVER=TRUE"
            }
        } else {
            "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?ssl=true&sslmode=require"
        }
    }

    fun getDriverClassName(): String {
        return if (USE_STANDALONE_DB) "org.h2.Driver" else "org.postgresql.Driver"
    }
}
