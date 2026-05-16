package com.example.famekodriver.driver

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.appcompat.app.AlertDialog
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DriverSignupActivity : AppCompatActivity() {
    private val repository = DriverRepository()
    private var currentStep = 1

    // Document files
    private val selectedDocs = mutableMapOf<String, File>()
    private var activeDocKey: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let { handleFileSelection(it) }
        }
    }

    private var selectedVehicleType: String? = null
    private var selectedServiceType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_signup)

        setupStep1()
        setupStep2()
        setupVehicleAndServiceSelection()
        setupServiceOptions()
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
                // Deselect others manually since they aren't in a RadioGroup
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

    private fun setupStep1() {
        val actRegion = findViewById<AutoCompleteTextView>(R.id.actRegion)
        val regions = arrayOf("Savannah", "Northern", "North East", "Upper East", "Upper West", "Ashanti")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        actRegion.setAdapter(adapter)

        findViewById<MaterialButton>(R.id.btnNextStep).setOnClickListener {
            if (validateStep1()) {
                currentStep = 2
                updateStepUI()
            }
        }

        findViewById<TextView>(R.id.tvLogin).setOnClickListener { finish() }
    }

    private fun validateStep1(): Boolean {
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

    private fun setupStep2() {
        findViewById<View>(R.id.cardProfilePic).setOnClickListener { openPicker("profile_pic") }
        findViewById<View>(R.id.cardLicense).setOnClickListener { openPicker("drivers_license") }
        findViewById<View>(R.id.cardInsurance).setOnClickListener { openPicker("insurance_cert") }
        findViewById<View>(R.id.cardRoadworthy).setOnClickListener { openPicker("roadworthy_cert") }
        findViewById<View>(R.id.cardGhanaCard).setOnClickListener { openPicker("ghana_card") }

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            currentStep = 1
            updateStepUI()
        }

        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener {
            submitRegistration()
        }
    }

    private lateinit var photoUri: Uri

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            handleFileSelection(photoUri)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPicker(key: String) {
        activeDocKey = key
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Document Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCamera()
                    1 -> startGallery()
                }
            }
            .show()
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val photoFile = File.createTempFile(
                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_",
                ".jpg",
                getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            )
            photoUri = FileProvider.getUriForFile(
                this,
                "com.example.famekodriver.driver.fileprovider",
                photoFile
            )
            cameraLauncher.launch(photoUri)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        pickImageLauncher.launch(intent)
    }

    private fun handleFileSelection(uri: Uri) {
        val key = activeDocKey ?: return
        val file = uriToFile(uri)
        if (file != null) {
            selectedDocs[key] = file
            updateImagePreview(key, uri)
        }
    }

    private fun updateImagePreview(key: String, uri: Uri) {
        val imageViewId = when (key) {
            "profile_pic" -> R.id.ivProfilePic
            "drivers_license" -> R.id.ivLicense
            "insurance_cert" -> R.id.ivInsurance
            "roadworthy_cert" -> R.id.ivRoadworthy
            "ghana_card" -> R.id.ivGhanaCard
            else -> return
        }
        findViewById<ImageView>(imageViewId).setImageURI(uri)
    }

    private fun uriToFile(uri: Uri): File? {
        val fileName = getFileName(uri) ?: return null
        val tempFile = File(cacheDir, fileName)
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    private fun updateStepUI() {
        val viewFlipper = findViewById<ViewFlipper>(R.id.viewFlipper)
        viewFlipper.displayedChild = currentStep - 1

        val tvStep1Circle = findViewById<TextView>(R.id.tvStep1Circle)
        val tvStep1Label = findViewById<TextView>(R.id.tvStep1Label)
        val tvStep2Circle = findViewById<TextView>(R.id.tvStep2Circle)
        val tvStep2Label = findViewById<TextView>(R.id.tvStep2Label)

        if (currentStep == 1) {
            tvStep1Circle.setBackgroundResource(R.drawable.step_circle_active)
            tvStep1Circle.setTextColor(Color.WHITE)
            tvStep1Label.setTextColor("#004E89".toColorInt())
            
            tvStep2Circle.setBackgroundResource(R.drawable.step_circle_inactive)
            tvStep2Circle.setTextColor("#757575".toColorInt())
            tvStep2Label.setTextColor("#757575".toColorInt())
        } else {
            tvStep1Circle.setBackgroundResource(R.drawable.step_circle_active)
            tvStep2Circle.setBackgroundResource(R.drawable.step_circle_active)
            tvStep2Circle.setTextColor(Color.WHITE)
            tvStep2Label.setTextColor("#004E89".toColorInt())
        }
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

        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Please fill in all basic info", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<MaterialButton>(R.id.btnSubmit).isEnabled = false
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            repository.driverRegister(
                name, email, phone, password, licenseNum, region, vehicleType, serviceType, vehicleNum, selectedDocs
            ).onSuccess {
                Toast.makeText(this@DriverSignupActivity, "Account created! Please log in to upload documents.", Toast.LENGTH_LONG).show()
                finish()
            }.onFailure {
                findViewById<MaterialButton>(R.id.btnSubmit).isEnabled = true
                Toast.makeText(this@DriverSignupActivity, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
