package com.example.android_rat

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object MicrophoneHandler {

    private const val TAG = "MicrophoneHandler"

    // Check if microphone permission is granted
    fun checkMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // Logic to access the microphone
    fun accessMicrophone(context: Context) {
        if (checkMicrophonePermission(context)) {
            Log.d(TAG, "Microphone accessed successfully")
            // Implement microphone access logic here
        } else {
            Log.d(TAG, "Microphone permission not granted")
        }
    }
}
