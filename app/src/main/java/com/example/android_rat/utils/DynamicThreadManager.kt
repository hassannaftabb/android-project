package com.example.android_rat.utils

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

object DynamicThreadManager {

    private const val MAX_THREADS = 10
    private const val MIN_THREADS = 1
    private var threadPoolExecutor: ThreadPoolExecutor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor

    fun adjustThreadPool(context: Context) {
        val memoryPercentage = DeviceHealthMonitor.getAvailableMemoryPercentage(context)
        val batteryLevel = DeviceHealthMonitor.getBatteryLevel(context)

        val maxAllowedThreads = calculateMaxAllowedThreads(memoryPercentage, batteryLevel)
        Log.d("DynamicThreadManager", "Max Allowed Threads: $maxAllowedThreads")

        // Adjust the thread pool size based on performance metrics
        threadPoolExecutor.corePoolSize = maxAllowedThreads
        threadPoolExecutor.maximumPoolSize = maxAllowedThreads
    }

    private fun calculateMaxAllowedThreads(memoryPercentage: Int, batteryLevel: Int): Int {
        var availableThreads = MAX_THREADS

        // Reduce thread count based on memory
        if (memoryPercentage < 20) {
            availableThreads -= 3
        }

        // Reduce thread count if battery is low
        if (batteryLevel < 20) {
            availableThreads -= 2
        }

        return availableThreads.coerceIn(MIN_THREADS, MAX_THREADS)
    }

    fun submitTask(task: Runnable) {
        threadPoolExecutor.submit(task)
    }
}
