package com.example.carmodelpredictor

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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.provider.Settings


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnPickImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var tvResult: TextView
    private lateinit var imageClassifier: ImageClassifier
    private val PERMISSIONS_REQUEST_CODE = 101

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

        // Pick image from gallery
        btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        // Take photo using camera
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
            // Check if user previously denied and selected "Don't ask again"
            val shouldShowRationale = notGrantedPermissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            if (shouldShowRationale) {
                // Show a dialog explaining why permission is needed
                showPermissionRationaleDialog(notGrantedPermissions)
            } else {
                // Request permissions directly
                ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
            }
        }
    }

    // Show a dialog to explain why permissions are needed
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

    // Handle the permission request result
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
                // Check if user denied with "Don't ask again"
                val permanentlyDeniedPermissions = deniedPermissions.filter {
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (permanentlyDeniedPermissions.isNotEmpty()) {
                    // Show a dialog to open settings
                    showSettingsDialog()
                } else {
                    Toast.makeText(this, "Permissions denied: $deniedPermissions", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Show dialog to direct user to settings
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


    // Handle gallery image selection
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

    // Handle camera capture
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    processAndClassifyImage(bitmap)
                } else {
                    Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Function to resize and classify image
    private fun processAndClassifyImage(bitmap: Bitmap) {
        val resizedBitmap = resizeBitmap(bitmap, 224, 224)
        imageClassifier?.let { classifier ->
            val prediction = classifier.classifyImage(resizedBitmap)
            tvResult.text = "Prediction: $prediction"
        }
    }


    // Function to resize bitmap
    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    // Convert bitmap to ByteBuffer in the format required by TensorFlow Lite
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4) // Float32 (4 bytes per value)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF) / 255.0f // Normalize
            val g = (pixelValue shr 8 and 0xFF) / 255.0f
            val b = (pixelValue and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }

//    override fun onDestroy() {
//        (imageClassifier as? ImageClassifier)?.close()
//        imageClassifier = None
//        super.onDestroy()
//    }

}
