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
            println("Initializing database connection...")
            try {
                Class.forName(DatabaseConfig.getDriverClassName())
                val config = HikariConfig().apply {
                    jdbcUrl = DatabaseConfig.getJdbcUrl()
                    username = DatabaseConfig.DB_USER
                    password = DatabaseConfig.DB_PASS
                    driverClassName = DatabaseConfig.getDriverClassName()
                    
                    // Connection pool settings
                    maximumPoolSize = 10
                    isAutoCommit = true
                    validate()
                }
                dataSource = HikariDataSource(config)
                
                // Only run migrations/setup if the database is not yet initialized
                dataSource!!.connection.use { conn ->
                    if (!isAlreadyInitialized(conn)) {
                        println("Database not initialized. Creating tables and seeding data...")
                        createTables(conn)
                        seedAdmin(conn)
                        println("Database initialization completed successfully.")
                    } else {
                        println("Database already initialized. Skipping table creation.")
                    }
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

    private fun seedAdmin(conn: Connection) {
        try {
            val sqlAdmin = "INSERT INTO admins (username, email, password, can_manage_drivers, is_active) VALUES ('admin', 'niiodartei24@gmail.com', 'feroA5002', true, true) ON CONFLICT (username) DO NOTHING"
            val sqlDriver = "INSERT INTO drivers (full_name, email, phone, region, password, license_number, status, vehicle_type) VALUES ('Test Driver', 'driver@test.com', '0240000000', 'Ashanti', 'pass123', 'DL-12345', 'PENDING', 'Car') ON CONFLICT (email) DO NOTHING"
            conn.createStatement().use { 
                it.execute(sqlAdmin)
                it.execute(sqlDriver)
            }
        } catch (e: Exception) {
            println("Seeding failed: ${e.message}")
            // Don't rethrow, seeding failure shouldn't stop the app
        }
    }

    private fun createTables(conn: Connection) {
        val statements = listOf(
            // ... (rest of the statements)
            """
            CREATE TABLE IF NOT EXISTS customers (
                id SERIAL PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                phone TEXT NOT NULL,
                password TEXT NOT NULL,
                default_address TEXT,
                default_latitude DOUBLE PRECISION,
                default_longitude DOUBLE PRECISION,
                is_active BOOLEAN DEFAULT TRUE,
                date_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
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
            """.trimIndent()
        )

        conn.createStatement().use { stmt ->
            statements.forEach { sql ->
                stmt.execute(sql)
            }
        }
    }
}
