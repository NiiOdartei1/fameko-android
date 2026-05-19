package com.example.famekodriver.backend.db

import com.example.famekodriver.core.network.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

object DatabaseInitializer {
    private var dataSource: HikariDataSource? = null

    fun init(): DataSource {
        if (dataSource == null) {
            println("Initializing Render PostgreSQL connection...")
            try {
                val envUrl = System.getenv("DATABASE_URL") ?: System.getenv("DB_URL")
                
                var finalUrl: String
                var finalUser: String? = System.getenv("DB_USER")
                var finalPass: String? = System.getenv("DB_PASS")

                if (envUrl != null && (envUrl.startsWith("postgresql://") || envUrl.startsWith("postgres://"))) {
                    // Parse Railway/Render/Heroku style URL: postgresql://user:pass@host:port/db
                    try {
                        val cleanUrl = envUrl.replace("postgresql://", "http://").replace("postgres://", "http://")
                        val uri = java.net.URI(cleanUrl)
                        val userInfo = uri.userInfo
                        if (userInfo != null && userInfo.contains(":")) {
                            val parts = userInfo.split(":")
                            finalUser = parts[0]
                            finalPass = parts[1]
                        }
                        
                        // Reconstruct as a clean JDBC URL
                        val host = uri.host
                        val port = if (uri.port != -1) uri.port else 5432
                        val path = uri.path
                        finalUrl = "jdbc:postgresql://$host:$port$path"
                        
                        // Append queries if present (like sslmode)
                        if (uri.query != null) {
                            finalUrl += "?" + uri.query
                        } else if (envUrl.contains("railway.internal")) {
                            // Internal Railway connections don't usually need SSL
                            finalUrl += "?sslmode=disable"
                        }
                    } catch (e: Exception) {
                        println("Warning: Manual URL parsing failed, falling back: ${e.message}")
                        finalUrl = if (envUrl.startsWith("jdbc:")) envUrl else "jdbc:$envUrl"
                    }
                } else {
                    finalUrl = envUrl ?: DatabaseConfig.getJdbcUrl()
                    if (!finalUrl.startsWith("jdbc:")) {
                        finalUrl = "jdbc:$finalUrl"
                    }
                }

                finalUser = finalUser ?: DatabaseConfig.DB_USER
                finalPass = finalPass ?: DatabaseConfig.DB_PASS

                println("Connecting to: $finalUrl")
                println("User: $finalUser")

                Class.forName("org.postgresql.Driver")
                val config = HikariConfig().apply {
                    jdbcUrl = finalUrl
                    username = finalUser
                    password = finalPass
                    driverClassName = "org.postgresql.Driver"
                    
                    maximumPoolSize = 5 // Low pool size for free tier
                    isAutoCommit = true
                    validate()
                }
                dataSource = HikariDataSource(config)
                
                // 1. Try to enable PostGIS in a separate block
                try {
                    dataSource!!.connection.use { conn ->
                        conn.createStatement().execute("CREATE EXTENSION IF NOT EXISTS postgis;")
                        println("PostGIS extension checked/enabled.")
                    }
                } catch (e: Exception) {
                    println("Warning: PostGIS extension not available. Spatial features will be disabled: ${e.message}")
                }

                // 2. Setup tables using a FRESH connection (in case the previous one was marked broken by Hikari)
                dataSource!!.connection.use { conn ->
                    println("Setting up database tables...")
                    createTables(conn)
                    migrateTables(conn)
                    seedAdmin(conn)
                    println("Database setup complete.")
                }
            } catch (e: Exception) {
                println("Database initialization failed!")
                e.printStackTrace()
                throw e
            }
        }
        return dataSource!!
    }

    fun getDataSource(): DataSource {
        return dataSource ?: init()
    }

    private fun isAlreadyInitialized(conn: Connection): Boolean {
        return try {
            val dbMeta = conn.metaData
            // Check if the 'drivers' table exists as a marker for an initialized DB
            dbMeta.getTables(null, null, "drivers", arrayOf("TABLE")).use { rs ->
                rs.next()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun migrateTables(conn: Connection) {
        val migrations = listOf(
            "ALTER TABLE customers ADD COLUMN IF NOT EXISTS region TEXT;",
            "ALTER TABLE customers ADD COLUMN IF NOT EXISTS profile_picture TEXT;",
            "ALTER TABLE deliveries ADD COLUMN IF NOT EXISTS estimated_earnings NUMERIC(12, 2) DEFAULT 0.0;"
        )
        conn.createStatement().use { stmt ->
            migrations.forEach { sql ->
                try {
                    stmt.execute(sql)
                } catch (e: Exception) {
                    println("Migration skipped: ${e.message}")
                }
            }
        }
    }

    private fun seedAdmin(conn: Connection) {
        try {
            // Check if admin already exists to avoid duplicates without relying on ON CONFLICT
            val sqlCheckAdmin = "SELECT COUNT(*) FROM admins WHERE username = 'admin'"
            val adminExists = conn.createStatement().use { it.executeQuery(sqlCheckAdmin).use { rs -> if (rs.next()) rs.getInt(1) > 0 else false } }
            
            if (!adminExists) {
                val sqlAdmin = "INSERT INTO admins (username, email, password, can_manage_drivers, is_active) VALUES ('admin', 'niiodartei24@gmail.com', 'feroA5002', true, true)"
                conn.createStatement().use { it.execute(sqlAdmin) }
                println("Admin seeded successfully.")
            }

            // Check if test driver already exists
            val sqlCheckDriver = "SELECT COUNT(*) FROM drivers WHERE email = 'driver@test.com'"
            val driverExists = conn.createStatement().use { it.executeQuery(sqlCheckDriver).use { rs -> if (rs.next()) rs.getInt(1) > 0 else false } }

            if (!driverExists) {
                val sqlDriver = "INSERT INTO drivers (full_name, email, phone, region, password, license_number, status, vehicle_type) VALUES ('Test Driver', 'driver@test.com', '0240000000', 'Ashanti', 'pass123', 'DL-12345', 'PENDING', 'Car')"
                conn.createStatement().use { it.execute(sqlDriver) }
                println("Test Driver seeded successfully.")
            }
        } catch (e: Exception) {
            println("Seeding failed: ${e.message}")
        }
    }

    private fun createTables(conn: Connection) {
        val statements = listOf(
            "CREATE EXTENSION IF NOT EXISTS postgis;",
            """
            CREATE TABLE IF NOT EXISTS drivers (
                id SERIAL PRIMARY KEY,
                full_name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                phone TEXT NOT NULL,
                region TEXT NOT NULL,
                password TEXT NOT NULL,
                license_number TEXT UNIQUE NOT NULL,
                license_expiry TIMESTAMP,
                vehicle_type TEXT,
                vehicle_number TEXT,
                vehicle_model TEXT,
                insurance_policy_number TEXT,
                insurance_expiry TIMESTAMP,
                status TEXT DEFAULT 'PENDING',
                is_online BOOLEAN DEFAULT FALSE,
                service_types TEXT DEFAULT 'both',
                profile_picture TEXT,
                license_image TEXT,
                id_front_image TEXT,
                id_back_image TEXT,
                vehicle_image TEXT,
                total_earnings NUMERIC(12, 2) DEFAULT 0.0,
                total_tips_earned NUMERIC(12, 2) DEFAULT 0.0,
                total_bonuses_earned NUMERIC(12, 2) DEFAULT 0.0,
                total_commissions_paid NUMERIC(12, 2) DEFAULT 0.0,
                completed_deliveries INTEGER DEFAULT 0,
                cancelled_deliveries INTEGER DEFAULT 0,
                commission_rate DOUBLE PRECISION DEFAULT 25.0,
                rating DOUBLE PRECISION DEFAULT 5.0,
                date_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS driver_stats (
                driver_id INTEGER PRIMARY KEY REFERENCES drivers(id),
                is_online BOOLEAN DEFAULT FALSE,
                active_deliveries INTEGER DEFAULT 0,
                completed_today INTEGER DEFAULT 0,
                earnings_today NUMERIC(12, 2) DEFAULT 0.0,
                rating DOUBLE PRECISION DEFAULT 5.0,
                rating_count INTEGER DEFAULT 0,
                total_deliveries INTEGER DEFAULT 0,
                completion_rate INTEGER DEFAULT 100,
                total_earnings NUMERIC(12, 2) DEFAULT 0.0,
                latitude DOUBLE PRECISION,
                longitude DOUBLE PRECISION,
                bearing REAL DEFAULT 0.0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            // Add spatial column separately so it doesn't crash the whole table creation if PostGIS is missing
            "ALTER TABLE driver_stats ADD COLUMN IF NOT EXISTS location GEOGRAPHY(POINT, 4326);",
            "CREATE INDEX IF NOT EXISTS driver_stats_location_idx ON driver_stats USING GIST (location);",
            """
            CREATE TABLE IF NOT EXISTS customers (
                id SERIAL PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                phone TEXT NOT NULL,
                region TEXT,
                password TEXT NOT NULL,
                default_address TEXT,
                default_latitude DOUBLE PRECISION,
                default_longitude DOUBLE PRECISION,
                profile_picture TEXT,
                is_active BOOLEAN DEFAULT TRUE,
                date_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS orders (
                id SERIAL PRIMARY KEY,
                customer_id INTEGER REFERENCES customers(id) NOT NULL,
                total_amount NUMERIC(12, 2) NOT NULL,
                order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status TEXT DEFAULT 'Pending',
                shipping_name TEXT NOT NULL,
                shipping_address TEXT NOT NULL,
                shipping_phone TEXT NOT NULL,
                latitude DOUBLE PRECISION,
                longitude DOUBLE PRECISION,
                payment_method TEXT DEFAULT 'Cash on Delivery',
                payment_status TEXT DEFAULT 'Pending',
                notes TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS order_items (
                id SERIAL PRIMARY KEY,
                order_id INTEGER REFERENCES orders(id) NOT NULL,
                item_name TEXT NOT NULL,
                item_description TEXT,
                quantity INTEGER DEFAULT 1,
                unit_price NUMERIC(12, 2) NOT NULL,
                total_price NUMERIC(12, 2) NOT NULL,
                status TEXT DEFAULT 'Pending'
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS deliveries (
                id SERIAL PRIMARY KEY,
                order_id INTEGER REFERENCES orders(id) NOT NULL,
                driver_id INTEGER REFERENCES drivers(id),
                pickup_location TEXT NOT NULL,
                pickup_address TEXT,
                pickup_lat DOUBLE PRECISION,
                pickup_lng DOUBLE PRECISION,
                dropoff_location TEXT NOT NULL,
                dropoff_address TEXT,
                dropoff_lat DOUBLE PRECISION,
                dropoff_lng DOUBLE PRECISION,
                route_coords TEXT,
                distance_km DOUBLE PRECISION,
                estimated_duration_minutes INTEGER,
                estimated_earnings NUMERIC(12, 2) DEFAULT 0.0,
                base_fare NUMERIC(12, 2) DEFAULT 0.0,
                per_km_rate NUMERIC(12, 2),
                platform_commission NUMERIC(12, 2) DEFAULT 0.0,
                driver_commission_percent DOUBLE PRECISION DEFAULT 75.0,
                tips NUMERIC(12, 2) DEFAULT 0.0,
                bonuses NUMERIC(12, 2) DEFAULT 0.0,
                cancellation_fee NUMERIC(12, 2) DEFAULT 1.50,
                wait_time_fee NUMERIC(12, 2) DEFAULT 0.15,
                peak_multiplier DOUBLE PRECISION DEFAULT 1.0,
                total_fare NUMERIC(12, 2),
                actual_driver_earnings NUMERIC(12, 2),
                total_platform_fees NUMERIC(12, 2) DEFAULT 0.0,
                status TEXT DEFAULT 'Pending',
                service_type TEXT DEFAULT 'package_delivery',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                assigned_at TIMESTAMP,
                started_at TIMESTAMP,
                completed_at TIMESTAMP,
                notes TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS driver_locations (
                driver_id INTEGER PRIMARY KEY REFERENCES drivers(id),
                latitude DOUBLE PRECISION,
                longitude DOUBLE PRECISION,
                delivery_id INTEGER REFERENCES deliveries(id),
                accuracy DOUBLE PRECISION,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS driver_ratings (
                id SERIAL PRIMARY KEY,
                driver_id INTEGER REFERENCES drivers(id) NOT NULL,
                delivery_id INTEGER REFERENCES deliveries(id) NOT NULL,
                rating DOUBLE PRECISION NOT NULL,
                comment TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS wallets (
                id SERIAL PRIMARY KEY,
                driver_id INTEGER UNIQUE REFERENCES drivers(id) NOT NULL,
                balance NUMERIC(12, 2) DEFAULT 0.0,
                total_credited NUMERIC(12, 2) DEFAULT 0.0,
                total_debitted NUMERIC(12, 2) DEFAULT 0.0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS wallet_transactions (
                id SERIAL PRIMARY KEY,
                wallet_id INTEGER REFERENCES wallets(id) NOT NULL,
                delivery_id INTEGER REFERENCES deliveries(id),
                transaction_type TEXT NOT NULL,
                amount NUMERIC(12, 2) NOT NULL,
                description TEXT,
                status TEXT DEFAULT 'Completed',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS admins (
                id SERIAL PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                can_manage_drivers BOOLEAN DEFAULT FALSE,
                can_view_analytics BOOLEAN DEFAULT FALSE,
                can_manage_orders BOOLEAN DEFAULT FALSE,
                can_manage_admins BOOLEAN DEFAULT FALSE,
                is_active BOOLEAN DEFAULT TRUE,
                date_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS conversations (
                id SERIAL PRIMARY KEY,
                delivery_id INTEGER REFERENCES deliveries(id),
                customer_id INTEGER REFERENCES customers(id) NOT NULL,
                driver_id INTEGER REFERENCES drivers(id),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_message_at TIMESTAMP
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS messages (
                id SERIAL PRIMARY KEY,
                conversation_id INTEGER REFERENCES conversations(id) NOT NULL,
                sender_type TEXT NOT NULL,
                sender_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                read BOOLEAN DEFAULT FALSE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS pricing_config (
                id SERIAL PRIMARY KEY,
                driver_commission_percent DOUBLE PRECISION DEFAULT 75.0,
                peak_multiplier DOUBLE PRECISION DEFAULT 1.5,
                cancellation_fee NUMERIC(12, 2) DEFAULT 1.50,
                wait_time_fee NUMERIC(12, 2) DEFAULT 0.15,
                bonus_per_delivery NUMERIC(12, 2) DEFAULT 0.50,
                referral_bonus NUMERIC(12, 2) DEFAULT 5.00,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_by_admin_id INTEGER REFERENCES admins(id)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS sos_alerts (
                id SERIAL PRIMARY KEY,
                driver_id INTEGER REFERENCES drivers(id),
                latitude DOUBLE PRECISION NOT NULL,
                longitude DOUBLE PRECISION NOT NULL,
                status TEXT DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                resolved_at TIMESTAMP
            )
            """.trimIndent()
        )

        conn.createStatement().use { stmt ->
            statements.forEach { sql ->
                try {
                    stmt.execute(sql)
                } catch (e: Exception) {
                    println("Statement skipped: ${e.message}")
                }
            }
        }
    }
}
