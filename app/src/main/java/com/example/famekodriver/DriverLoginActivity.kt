package com.example.famekodriver

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DriverLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        btnLogin.setOnClickListener {
            Toast.makeText(this, "Attempting to login...", Toast.LENGTH_SHORT).show()
        }

        tvRegister.setOnClickListener {
            val intent = Intent(this, DriverSignupActivity::class.java)
            startActivity(intent)
        }
    }
}
