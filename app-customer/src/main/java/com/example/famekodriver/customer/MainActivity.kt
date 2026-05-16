package com.example.famekodriver.customer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("Fameko", "MainActivity onCreate")
        
        setContentView(R.layout.activity_main)

        val btnDriver = findViewById<MaterialButton>(R.id.btnDriver)
        val btnCustomer = findViewById<MaterialButton>(R.id.btnCustomer)

        btnDriver.setOnClickListener {
            Toast.makeText(this, "Please use the Fameko Driver app", Toast.LENGTH_SHORT).show()
        }

        btnCustomer.setOnClickListener {
            try {
                val intent = Intent(this, CustomerLoginActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Error starting login: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                Log.e("Fameko", "Failed to start CustomerLoginActivity", e)
            }
        }
    }
}
