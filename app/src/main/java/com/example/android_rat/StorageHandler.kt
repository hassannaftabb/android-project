package com.example.android_rat

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object StorageHandler {

    private const val TAG = "StorageHandler"

    // Check if storage permission is granted
    fun checkStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    // Logic to access storage
    fun accessStorage(context: Context) {
        if (checkStoragePermission(context)) {
            Log.d(TAG, "Storage accessed successfully")
            // Implement storage access logic here
        } else {
            Log.d(TAG, "Storage permission not granted")
        }
    }
}
