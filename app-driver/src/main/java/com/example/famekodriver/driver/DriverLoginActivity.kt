package com.example.famekodriver.driver

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class DriverLoginActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Check if already logged in
        if (sessionManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_driver_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString() ?: ""
            val password = etPassword.text?.toString() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                repository.login(email, password)
                    .onSuccess { driver ->
                        if (driver != null) {
                            sessionManager.saveSession(driver.id.toString(), driver.fullName, driver.status)
                            navigateToMain()
                        } else {
                            btnLogin.isEnabled = true
                            Toast.makeText(this@DriverLoginActivity, "Invalid email or password", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure { error ->
                        btnLogin.isEnabled = true
                        val message = error.message ?: "Login failed"
                        Toast.makeText(this@DriverLoginActivity, message, Toast.LENGTH_LONG).show()
                    }
            }
        }

        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        tvRegister.setOnClickListener {
            Toast.makeText(this, "Opening Registration...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DriverSignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
