package com.example.carmodelpredictor

import ImageClassifier
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Context
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnPickImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var tvResult: TextView
    private var imageClassifier: ImageClassifier? = null
    private val PERMISSIONS_REQUEST_CODE = 101

    fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermission()

        imageView = findViewById(R.id.imageView)
        btnPickImage = findViewById(R.id.btnPickImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        tvResult = findViewById(R.id.tvResult)

        // Initialize TensorFlow Lite classifier
        try {
            imageClassifier = ImageClassifier(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize ImageClassifier", e)
            Toast.makeText(this, "Failed to initialize ImageClassifier", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        btnTakePhoto.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }
    }


    private fun requestPermission() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            val shouldShowRationale = notGrantedPermissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            if (shouldShowRationale) {
                showPermissionRationaleDialog(notGrantedPermissions)
            } else {
                ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    private fun showPermissionRationaleDialog(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs access to your camera and storage to function properly.")
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()

            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }

            if (deniedPermissions.isEmpty()) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                val permanentlyDeniedPermissions = deniedPermissions.filter {
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (permanentlyDeniedPermissions.isNotEmpty()) {
                    showSettingsDialog()
                } else {
                    Toast.makeText(this, "Permissions denied: $deniedPermissions", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Please enable camera and photo permission in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val imageUri: Uri? = data?.data
                imageUri?.let {
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                        imageView.setImageBitmap(bitmap)
                        processAndClassifyImage(bitmap)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val bitmap = data?.extras?.get("data") as? Bitmap
                bitmap?.let {
                    imageView.setImageBitmap(it)
                    processAndClassifyImage(it)
                } ?: Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }

    private fun processAndClassifyImage(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, 224, 224)
        imageClassifier?.let { classifier ->
            val prediction = classifier.classifyImage(resizedBitmap)
            tvResult.text = "Prediction: $prediction"
        } ?: Toast.makeText(this, "Classifier not initialized", Toast.LENGTH_SHORT).show()
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

//    override fun onDestroy() {
//        imageClassifier?.close()
//        imageClassifier = null
//        super.onDestroy()
//    }
    companion object {
    fun assetFilePath(context: Context, assetName: String): String? {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath

    }
}
}
