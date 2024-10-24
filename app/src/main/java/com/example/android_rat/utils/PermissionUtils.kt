package com.example.android_rat.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat

object PermissionUtils {

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33 and above) requires these specific permissions for media access
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 (API 29 to 32) uses external storage permissions
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            // For Android versions below 10, older permissions can still be used
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    // Function to check if all permissions are already granted
    fun areAllPermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Function to check and request permissions
    fun checkAndRequestPermissions(context: Context, permissions: Array<String>, requestPermissions: (Array<String>) -> Unit) {
        try {
            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                requestPermissions(missingPermissions.toTypedArray())
            } else {
                Log.d("PermissionUtils", "All required permissions are already granted.")
                Toast.makeText(context, "Permissions already granted", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("PermissionUtils", "Error checking or requesting permissions: ${e.localizedMessage}")
        }
    }

    // Function to open notification settings
    fun openNotificationSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}
