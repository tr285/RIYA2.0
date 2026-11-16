package com.example.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "RiyaAudioRecorder"

class RiyaAudioRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Result<Unit> {
        return try {
            stopAndDiscard()
            val file = File(appContext.cacheDir, "riya_audio_${System.currentTimeMillis()}.m4a")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopAndDiscard()
            Result.failure(e)
        }
    }

    fun currentAmplitudeNormalized(): Float {
        return try {
            val raw = recorder?.maxAmplitude ?: 0
            (raw / 24_000f).coerceIn(0.05f, 1f)
        } catch (_: Exception) {
            0.05f
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            if (file == null || !file.exists()) {
                Result.failure(IllegalStateException("Audio recording file was not found."))
            } else {
                val bytes = file.readBytes()
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio recording", e)
            Result.failure(e)
        } finally {
            runCatching { recorder?.release() }
            recorder = null
            runCatching { file?.delete() }
            outputFile = null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        try {
            outputFile?.delete()
        } catch (_: Exception) {
        }
        outputFile = null
    }
}
