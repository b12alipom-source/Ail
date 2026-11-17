package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID

private const val TAG = "VoiceRecorder"

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording() {
        stopAndCleanupOnly()
        val file = File(context.cacheDir, "bayan_voice_${UUID.randomUUID()}.m4a")
        outputFile = file

        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        instance.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = instance
    }

    fun getNormalizedAmplitude(): Float {
        return try {
            val raw = recorder?.maxAmplitude ?: 0
            (raw.toFloat() / 22000f).coerceIn(0.05f, 1.0f)
        } catch (_: Exception) {
            0.05f
        }
    }

    fun stopAndReadBytes(): ByteArray {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop warning", e)
        } finally {
            runCatching { recorder?.release() }
            recorder = null
        }

        return try {
            if (file != null && file.exists() && file.length() > 0L) {
                file.readBytes()
            } else {
                ByteArray(0)
            }
        } finally {
            if (file?.exists() == true) {
                file.delete()
            }
            outputFile = null
        }
    }

    fun stopAndCleanupOnly() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.let { file ->
            if (file.exists()) file.delete()
        }
        outputFile = null
    }
}
