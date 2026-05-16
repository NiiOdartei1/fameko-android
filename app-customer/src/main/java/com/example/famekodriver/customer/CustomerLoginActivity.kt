package com.example.famekodriver.customer

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

class CustomerLoginActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("Fameko", "CustomerLoginActivity onCreate")
        sessionManager = SessionManager(this)

        // Check if already logged in as customer
        if (sessionManager.isLoggedIn()) {
            navigateToCustomerMap()
            return
        }

        setContentView(R.layout.activity_customer_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvBackToHome = findViewById<TextView>(R.id.tvBackToHome)

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
                repository.customerLogin(email, password)
                    .onSuccess { (customerId, customerName) ->
                        sessionManager.saveSession(customerId, customerName)
                        navigateToCustomerMap()
                    }
                    .onFailure { error ->
                        btnLogin.isEnabled = true
                        Toast.makeText(this@CustomerLoginActivity, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        tvRegister.setOnClickListener {
            val intent = Intent(this, CustomerSignupActivity::class.java)
            startActivity(intent)
        }

        tvBackToHome.setOnClickListener {
            finish()
        }
    }

    private fun navigateToCustomerMap() {
        val intent = Intent().setClassName(this, "com.example.famekodriver.customer.CustomerMapActivity")
        startActivity(intent)
        finish()
    }
}
