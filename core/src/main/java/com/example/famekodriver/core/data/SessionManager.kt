package com.example.famekodriver.core.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "FamekoDriverPrefs"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_DRIVER_ID = "driverId"
        private const val KEY_DRIVER_NAME = "driverName"
        private const val KEY_DRIVER_STATUS = "driverStatus"
        private const val KEY_IS_ONLINE = "isOnline"
    }

    fun saveSession(driverId: String, driverName: String, status: String = "PENDING") {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_DRIVER_ID, driverId)
            putString(KEY_DRIVER_NAME, driverName)
            putString(KEY_DRIVER_STATUS, status)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getDriverId(): String? = prefs.getString(KEY_DRIVER_ID, null)

    fun getDriverName(): String? = prefs.getString(KEY_DRIVER_NAME, "Driver")

    fun getDriverStatus(): String = prefs.getString(KEY_DRIVER_STATUS, "PENDING_DOCS") ?: "PENDING_DOCS"

    fun isOnline(): Boolean = prefs.getBoolean(KEY_IS_ONLINE, false)

    fun setOnline(online: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ONLINE, online).apply()
    }

    fun updateStatus(status: String) {
        prefs.edit().putString(KEY_DRIVER_STATUS, status).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
