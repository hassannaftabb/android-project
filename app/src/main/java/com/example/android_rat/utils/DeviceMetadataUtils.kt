package com.example.android_rat.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.lang.ref.WeakReference

object DeviceMetadataUtils {

    private var contextRef: WeakReference<Context>? = null

    // Initialize the context with WeakReference to avoid memory leaks
    fun initialize(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    private fun requireContext(): Context {
        return contextRef?.get() ?: throw IllegalStateException("Context not initialized. Please call initialize(context) first.")
    }

    // Generate metadata as a JSON object
    fun getDeviceBasicMetadataInJSON(): JSONObject {
        val metadata = JSONObject().apply {
            put("device_id", getDeviceId())
            put("build_id", getBuildId())
            put("android_version", getAndroidVersion())
            put("device_model", getDeviceModel())
            put("manufacturer", getManufacturer())
            put("device_name", getDeviceName())
            put("hardware", getHardware())
            put("brand", getBrand())
        }
        return metadata
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun getBuildId(): String = Build.ID

    fun getAndroidVersion(): Int = Build.VERSION.SDK_INT

    fun getDeviceModel(): String = Build.MODEL

    fun getManufacturer(): String = Build.MANUFACTURER

    fun getDeviceName(): String = Build.DEVICE

    fun getHardware(): String = Build.HARDWARE

    fun getBrand(): String = Build.BRAND
}
