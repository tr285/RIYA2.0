package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "RiyaMicCapturer"
private const val SAMPLE_RATE_HZ = 16_000
private const val MAX_CAPTURE_BYTES = SAMPLE_RATE_HZ * 2 * 45 // 45s @ 16kHz 16-bit mono
private const val SPEECH_THRESHOLD = 0.14f
private const val AUTO_SILENCE_STOP_MS = 1_800L

class RiyaMicrophoneCapturer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isCapturing = AtomicBoolean(false)
    private val bufferLock = Any()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var pcmBuffer = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startCapture(
        autoStopOnSilence: Boolean,
        onAudioLevel: (Float) -> Unit,
        onAutoSilenceStop: (ByteArray) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        cancelCapture()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (se: SecurityException) {
            onError(IllegalStateException("Microphone permission is required to capture voice.", se))
            return
        } catch (e: Exception) {
            onError(IllegalStateException("Unable to initialize Android Microphone API.", e))
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            onError(IllegalStateException("Android Microphone hardware could not be initialized."))
            return
        }

        synchronized(bufferLock) {
            pcmBuffer = ByteArrayOutputStream()
        }
        audioRecord = record
        isCapturing.set(true)

        captureJob = scope.launch {
            val shortChunk = ShortArray(minBuf / 2)
            val byteChunk = ByteArray(shortChunk.size * 2)
            var heardSpeech = false
            var silenceStartedAt = 0L

            try {
                record.startRecording()
                while (isCapturing.get()) {
                    val readCount = record.read(shortChunk, 0, shortChunk.size)
                    if (readCount <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = shortChunk[i].toInt()
                        sumSq += s * s
                        byteChunk[i * 2] = (s and 0xFF).toByte()
                        byteChunk[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    val rms = sqrt(sumSq / max(1, readCount))
                    val normalized = (rms / 6500.0).toFloat().coerceIn(0.04f, 1.0f)

                    val exceededMax: Boolean
                    synchronized(bufferLock) {
                        pcmBuffer.write(byteChunk, 0, readCount * 2)
                        exceededMax = pcmBuffer.size() >= MAX_CAPTURE_BYTES
                    }

                    withContext(Dispatchers.Main) {
                        if (isCapturing.get()) onAudioLevel(normalized)
                    }

                    if (normalized >= SPEECH_THRESHOLD) {
                        heardSpeech = true
                        silenceStartedAt = 0L
                    } else if (autoStopOnSilence && heardSpeech) {
                        val now = System.currentTimeMillis()
                        if (silenceStartedAt == 0L) {
                            silenceStartedAt = now
                        } else if (now - silenceStartedAt >= AUTO_SILENCE_STOP_MS) {
                            break
                        }
                    }
                    if (exceededMax) break
                }

                if (isCapturing.compareAndSet(true, false)) {
                    val wavBytes = finalizeWavInternal()
                    withContext(Dispatchers.Main) {
                        onAutoSilenceStop(wavBytes)
                    }
                }
            } catch (se: SecurityException) {
                isCapturing.set(false)
                releaseRecordOnly()
                withContext(Dispatchers.Main) {
                    onError(IllegalStateException("Microphone permission was revoked.", se))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Microphone capture failed", e)
                isCapturing.set(false)
                releaseRecordOnly()
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun stopAndEncodeWav(): ByteArray {
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null
        return finalizeWavInternal()
    }

    fun cancelCapture() {
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null
        releaseRecordOnly()
        synchronized(bufferLock) {
            pcmBuffer = ByteArrayOutputStream()
        }
    }

    fun release() {
        cancelCapture()
        scope.cancel()
    }

    private fun finalizeWavInternal(): ByteArray {
        releaseRecordOnly()
        val pcmData = synchronized(bufferLock) {
            val bytes = pcmBuffer.toByteArray()
            pcmBuffer = ByteArrayOutputStream()
            bytes
        }
        if (pcmData.isEmpty()) return ByteArray(0)
        return buildWavHeader(pcmData.size) + pcmData
    }

    private fun releaseRecordOnly() {
        val rec = audioRecord
        audioRecord = null
        if (rec != null) {
            runCatching {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
            runCatching { rec.release() }
        }
    }

    private fun buildWavHeader(pcmByteSize: Int): ByteArray {
        val totalDataLen = pcmByteSize + 36
        val byteRate = SAMPLE_RATE_HZ * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(1) // Channels = Mono
            putInt(SAMPLE_RATE_HZ)
            putInt(byteRate)
            putShort(2) // Block align
            putShort(16) // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmByteSize)
        }.array()
    }
}
