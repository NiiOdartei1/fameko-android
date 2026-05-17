package com.example.famekodriver.driver

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class DriverSignupActivity : AppCompatActivity() {
    private val repository = DriverRepository()

    private var selectedVehicleType: String? = null
    private var selectedServiceType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_signup)

        setupRegistrationForm()
    }

    private fun setupRegistrationForm() {
        val actRegion = findViewById<AutoCompleteTextView>(R.id.actRegion)
        val regions = arrayOf("Savannah", "Northern", "North East", "Upper East", "Upper West", "Ashanti", "Greater Accra")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        actRegion.setAdapter(adapter)

        setupVehicleAndServiceSelection()
        setupServiceOptions()

        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener {
            if (validateForm()) {
                submitRegistration()
            }
        }

        findViewById<TextView>(R.id.tvLogin).setOnClickListener { finish() }
    }

    private fun setupVehicleAndServiceSelection() {
        val llVehicleOptions = findViewById<LinearLayout>(R.id.llVehicleOptions)
        
        val vehicleMappings = mapOf(
            R.id.optionBicycle to "bicycle",
            R.id.optionPraggia to "praggia",
            R.id.optionAboboyaa to "abobo_yaa",
            R.id.optionOkada to "motor_okada",
            R.id.optionTruck to "truck",
            R.id.optionCar to "taxi_car"
        )

        vehicleMappings.forEach { (viewId, id) ->
            findViewById<View>(viewId).setOnClickListener {
                selectedVehicleType = id
                highlightSelectedVehicle(llVehicleOptions, it)
            }
        }
    }

    private fun setupServiceOptions() {
        val llServiceOptions = findViewById<LinearLayout>(R.id.llServiceOptions)
        llServiceOptions.removeAllViews()

        val services = listOf(
            "package_delivery" to "Package Delivery",
            "personal_transportation" to "Personal Transportation",
            "both" to "Both Services"
        )

        services.forEach { (id, label) ->
            val radioButton = RadioButton(this)
            radioButton.text = label
            radioButton.setOnClickListener {
                selectedServiceType = id
                for (i in 0 until llServiceOptions.childCount) {
                    val child = llServiceOptions.getChildAt(i) as? RadioButton
                    child?.let {
                        it.isChecked = (it.text == label)
                    }
                }
            }
            llServiceOptions.addView(radioButton)
        }
    }

    private fun highlightSelectedVehicle(container: LinearLayout, selected: View) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.isSelected = (child == selected)
            child.alpha = if (child == selected) 1.0f else 0.7f
        }
    }

    private fun validateForm(): Boolean {
        val name = findViewById<TextInputEditText>(R.id.etName).text?.toString() ?: ""
        val email = findViewById<TextInputEditText>(R.id.etEmail).text?.toString() ?: ""
        val phone = findViewById<TextInputEditText>(R.id.etPhone).text?.toString() ?: ""
        val license = findViewById<TextInputEditText>(R.id.etLicenseNumber).text?.toString() ?: ""
        val region = findViewById<AutoCompleteTextView>(R.id.actRegion).text?.toString() ?: ""
        val vNum = findViewById<TextInputEditText>(R.id.etVehicleNumber).text?.toString() ?: ""
        val pass = findViewById<TextInputEditText>(R.id.etPassword).text?.toString() ?: ""
        val confirmPass = findViewById<TextInputEditText>(R.id.etConfirmPassword).text?.toString() ?: ""

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || license.isEmpty() || 
            region.isEmpty() || vNum.isEmpty() || pass.isEmpty() || selectedVehicleType == null || selectedServiceType == null) {
            Toast.makeText(this, "Please fill all fields and select vehicle/service type", Toast.LENGTH_SHORT).show()
            return false
        }

        if (pass != confirmPass) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun submitRegistration() {
        val name = findViewById<TextInputEditText>(R.id.etName).text?.toString() ?: ""
        val email = findViewById<TextInputEditText>(R.id.etEmail).text?.toString() ?: ""
        val phone = findViewById<TextInputEditText>(R.id.etPhone).text?.toString() ?: ""
        val password = findViewById<TextInputEditText>(R.id.etPassword).text?.toString() ?: ""
        val licenseNum = findViewById<TextInputEditText>(R.id.etLicenseNumber).text?.toString() ?: ""
        val region = findViewById<AutoCompleteTextView>(R.id.actRegion).text?.toString() ?: ""
        val vehicleNum = findViewById<TextInputEditText>(R.id.etVehicleNumber).text?.toString() ?: ""
        
        val vehicleType = selectedVehicleType ?: "car"
        val serviceType = selectedServiceType ?: "both"

        findViewById<MaterialButton>(R.id.btnSubmit).isEnabled = false
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            repository.driverRegister(
                name, email, phone, password, licenseNum, region, vehicleType, serviceType, vehicleNum, emptyMap()
            ).onSuccess {
                Toast.makeText(this@DriverSignupActivity, "Registration successful! You can now log in.", Toast.LENGTH_LONG).show()
                finish()
            }.onFailure {
                findViewById<MaterialButton>(R.id.btnSubmit).isEnabled = true
                Toast.makeText(this@DriverSignupActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
