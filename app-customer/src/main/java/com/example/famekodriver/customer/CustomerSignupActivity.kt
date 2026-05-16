package com.example.famekodriver.customer

import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class CustomerSignupActivity : AppCompatActivity() {
    private val repository = DriverRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_signup)

        // Fields from layout
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        val btnSignup = findViewById<MaterialButton>(R.id.btnSignup)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        // Final Submission
        btnSignup.setOnClickListener {
            val name = etName.text?.toString() ?: ""
            val email = etEmail.text?.toString() ?: ""
            val phone = etPhone.text?.toString() ?: ""
            val address = etAddress.text?.toString() ?: ""
            val password = etPassword.text?.toString() ?: ""
            val confirmPassword = etConfirmPassword.text?.toString() ?: ""

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
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
                repository.customerRegister(name, email, phone, address, password)
                    .onSuccess {
                        Toast.makeText(this@CustomerSignupActivity, "Account created successfully!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .onFailure { error ->
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
