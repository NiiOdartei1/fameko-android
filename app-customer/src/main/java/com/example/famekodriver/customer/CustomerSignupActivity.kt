package com.example.famekodriver.customer

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class CustomerSignupActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_signup)
        sessionManager = SessionManager(this)

        // Fields from layout
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val actRegion = findViewById<AutoCompleteTextView>(R.id.actRegion)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val btnSignup = findViewById<MaterialButton>(R.id.btnSignup)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        // Setup Region Dropdown
        val regions = arrayOf("Savannah", "Northern", "North East", "Upper East", "Upper West", "Ashanti", "Greater Accra")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        actRegion.setAdapter(adapter)

        // Final Submission
        btnSignup.setOnClickListener {
            val name = etName.text?.toString() ?: ""
            val email = etEmail.text?.toString() ?: ""
            val phone = etPhone.text?.toString() ?: ""
            val region = actRegion.text?.toString() ?: ""
            val address = etAddress.text?.toString() ?: ""
            val password = etPassword.text?.toString() ?: ""
            val confirmPassword = etConfirmPassword.text?.toString() ?: ""

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || region.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the terms and conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignup.isEnabled = false
            lifecycleScope.launch {
                println("Fameko: Starting registration for $email")
                repository.customerRegister(name, email, phone, address, password, region = region)
                    .onSuccess {
                        println("Fameko: Registration success, attempting auto-login")
                        Toast.makeText(this@CustomerSignupActivity, "Account created successfully! Logging in...", Toast.LENGTH_LONG).show()
                        
                        // Automatic login
                        repository.customerLogin(email, password).onSuccess { (id, customerName) ->
                            println("Fameko: Auto-login success for $id")
                            sessionManager.saveSession(id, customerName)
                            val intent = Intent().setClassName(this@CustomerSignupActivity, "com.example.famekodriver.customer.CustomerMapActivity")
                            startActivity(intent)
                            finishAffinity()
                        }.onFailure { error ->
                            println("Fameko: Auto-login failed: ${error.message}")
                            btnSignup.isEnabled = true
                            Toast.makeText(this@CustomerSignupActivity, "Auto-login failed: ${error.message}. Please login manually.", Toast.LENGTH_LONG).show()
                            // Don't finish, let them try to login manually or see the error
                        }
                    }
                    .onFailure { error ->
                        println("Fameko: Registration failed: ${error.message}")
                        btnSignup.isEnabled = true
                        Toast.makeText(this@CustomerSignupActivity, "Signup failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }
}
