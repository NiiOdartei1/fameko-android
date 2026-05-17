package com.example.famekodriver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.famekodriver.core.data.SessionManager
import com.example.famekodriver.core.data.repository.DriverRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class DriverProfileActivity : AppCompatActivity() {
    private lateinit var sessionManager: SessionManager
    private val repository = DriverRepository()
    private var pendingDocType: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null && pendingDocType != null) {
                uploadDoc(pendingDocType!!, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_profile)

        sessionManager = SessionManager(this)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.tvDriverName).text = sessionManager.getDriverName()
        updateStatusUI()

        setupDocItem(R.id.layoutProfilePic, "Profile Picture", "profile_pic")
        setupDocItem(R.id.layoutLicense, "Driver's License", "drivers_license")
        setupDocItem(R.id.layoutGhanaCard, "Ghana Card", "ghana_card")
        setupDocItem(R.id.layoutInsurance, "Insurance Certificate", "insurance_cert")
        setupDocItem(R.id.layoutRoadworthy, "Roadworthy Certificate", "roadworthy_cert")
        
        refreshStatus()
    }

    private fun setupDocItem(layoutId: Int, title: String, type: String) {
        val view = findViewById<View>(layoutId)
        view.findViewById<TextView>(R.id.tvDocTitle).text = title
        view.findViewById<MaterialButton>(R.id.btnUpload).setOnClickListener {
            pendingDocType = type
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }
    }

    private fun updateStatusUI() {
        val status = sessionManager.getDriverStatus()
        findViewById<TextView>(R.id.tvDriverStatus).text = "Status: $status"
    }

    private fun refreshStatus() {
        val driverId = sessionManager.getDriverId() ?: return
        lifecycleScope.launch {
            repository.getDriverStatus(driverId).onSuccess { response ->
                sessionManager.updateStatus(response.status)
                updateStatusUI()
                // Update individual item statuses based on response.missingDocs
                updateDocStatus(R.id.layoutProfilePic, "profile_pic" !in response.missingDocs)
                updateDocStatus(R.id.layoutLicense, "drivers_license" !in response.missingDocs)
                updateDocStatus(R.id.layoutGhanaCard, "ghana_card" !in response.missingDocs)
                updateDocStatus(R.id.layoutInsurance, "insurance_cert" !in response.missingDocs)
                updateDocStatus(R.id.layoutRoadworthy, "roadworthy_cert" !in response.missingDocs)
            }.onFailure {
                updateStatusUI()
            }
        }
    }

    private fun updateDocStatus(layoutId: Int, uploaded: Boolean) {
        val view = findViewById<View>(layoutId)
        val tvStatus = view.findViewById<TextView>(R.id.tvDocStatus)
        if (uploaded) {
            tvStatus.text = "Uploaded"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#28A745"))
            view.findViewById<MaterialButton>(R.id.btnUpload).text = "Replace"
        } else {
            tvStatus.text = "Not uploaded"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#DC3545"))
            view.findViewById<MaterialButton>(R.id.btnUpload).text = "Upload"
        }
    }

    private fun uploadDoc(type: String, uri: Uri) {
        val driverId = sessionManager.getDriverId() ?: return
        val file = uriToFile(uri) ?: return

        lifecycleScope.launch {
            Toast.makeText(this@DriverProfileActivity, "Uploading...", Toast.LENGTH_SHORT).show()
            repository.uploadDocument(driverId, type, file).onSuccess {
                Toast.makeText(this@DriverProfileActivity, "Upload successful", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }.onFailure {
                Toast.makeText(this@DriverProfileActivity, "Upload failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            return file
        } catch (e: Exception) {
            return null
        }
    }
}
