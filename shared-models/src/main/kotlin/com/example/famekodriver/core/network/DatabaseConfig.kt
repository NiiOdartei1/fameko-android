package com.example.famekodriver.core.network

/**
 * Configuration for the PostgreSQL database hosted on Render.
 */
object DatabaseConfig {
    // Remote Database configuration from Render
    const val DB_HOST = "dpg-d84hg6t7vvec73f9hfig-a.oregon-postgres.render.com"
    const val DB_PORT = 5432
    const val DB_NAME = "fameko_db_gw9b"
    const val DB_USER = "fameko_db_gw9b_user"
    const val DB_PASS = "fFLDwaaBQSPOf2I5KR3Y8ZGtLtzc84PM"

    fun getJdbcUrl(): String {
        // Render requires SSL. We use NonValidatingFactory to avoid issues with missing root.crt on some environments
        return "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?ssl=true&sslmode=require&sslfactory=org.postgresql.ssl.NonValidatingFactory"
    }

    fun getDriverClassName(): String {
        return "org.postgresql.Driver"
    }
}
