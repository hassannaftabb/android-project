package com.example.android_rat

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object CameraHandler {

    private const val TAG = "CameraHandler"

    // Check if camera permission is granted
    fun checkCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Logic to access the camera (this is where you implement the camera access)
    fun accessCamera(context: Context) {
        if (checkCameraPermission(context)) {
            Log.d(TAG, "Camera accessed successfully")
            // Implement camera access logic here
        } else {
            Log.d(TAG, "Camera permission not granted")
        }
    }
}
