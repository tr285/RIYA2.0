package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val TAG = "RiyaVoiceEngine"

enum class VoiceState(val label: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    TRANSCRIBING("Transcribing speech..."),
    THINKING("Thinking..."),
    SPEAKING("Speaking..."),
    INTERRUPTED("Interrupted"),
    ERROR("Voice Error")
}

data class VoiceSnapshot(
    val voiceModeActive: Boolean = false,
    val isPaused: Boolean = false,
    val isPushToTalkActive: Boolean = false,
    val isDictating: Boolean = false,
    val voiceState: VoiceState = VoiceState.IDLE,
    val micActive: Boolean = false,
    val assistantSpeaking: Boolean = false,
    val audioLevel: Float = 0f,
    val waveformBars: List<Float> = List(24) { 0.08f },
    val partialTranscript: String = "",
    val usingDirectMicFallback: Boolean = false,
    val sttAvailable: Boolean = false,
    val ttsReady: Boolean = false,
    val lastVoiceError: String? = null
)

class RiyaVoiceEngine(
    context: Context,
    var onRecognizedText: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val _snapshot = MutableStateFlow(
        VoiceSnapshot(
            sttAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        )
    )
    val snapshot: StateFlow<VoiceSnapshot> = _snapshot.asStateFlow()

    var onDictationResult: (String) -> Unit = {}
    var onSpeechToTextAudio: (suspend (ByteArray) -> String)? = null

    private val micCapturer = RiyaMicrophoneCapturer()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var transcribeJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var phase = 0.0
    private var activeLanguageCode = "en"
    private var voiceResponsesEnabled = true

    private var activeForDictation = false
    private var activeContinuous = false
    private var activeUsingDirectMic = false

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                ttsInitialized = status == TextToSpeech.SUCCESS
                if (ttsInitialized) {
                    applyTtsLanguage(activeLanguageCode)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _snapshot.value = _snapshot.value.copy(
                                voiceState = VoiceState.SPEAKING,
                                assistantSpeaking = true,
                                audioLevel = 0.65f
                            )
                        }

                        override fun onDone(utteranceId: String?) {
                            val current = _snapshot.value
                            _snapshot.value = current.copy(
                                assistantSpeaking = false,
                                voiceState = VoiceState.IDLE,
                                audioLevel = 0f
                            )
                            if (current.voiceModeActive && !current.isPaused) {
                                startListening(continuous = true, forDictation = false)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _snapshot.value = _snapshot.value.copy(
                                assistantSpeaking = false,
                                voiceState = VoiceState.IDLE,
                                audioLevel = 0f
                            )
                        }
                    })
                }
                _snapshot.value = _snapshot.value.copy(ttsReady = ttsInitialized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS init error", e)
        }
    }

    fun updateSettings(languageCode: String, voiceResponses: Boolean) {
        activeLanguageCode = languageCode
        voiceResponsesEnabled = voiceResponses
        if (ttsInitialized) {
            applyTtsLanguage(languageCode)
        }
    }

    private fun applyTtsLanguage(code: String) {
        val locale = when (code) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "mr" -> Locale.forLanguageTag("mr-IN")
            else -> Locale.US
        }
        tts?.language = locale
    }

    fun setVoiceMode(enabled: Boolean) {
        if (!enabled) {
            stopListening()
            stopSpeaking()
            _snapshot.value = _snapshot.value.copy(
                voiceModeActive = false,
                isPaused = false,
                micActive = false,
                isDictating = false,
                isPushToTalkActive = false,
                partialTranscript = "",
                usingDirectMicFallback = false,
                voiceState = VoiceState.IDLE,
                audioLevel = 0f
            )
        } else {
            _snapshot.value = _snapshot.value.copy(
                voiceModeActive = true,
                isPaused = false,
                isDictating = false,
                partialTranscript = "",
                lastVoiceError = null
            )
            startListening(continuous = true, forDictation = false)
        }
    }

    fun togglePause() {
        val cur = _snapshot.value
        val nextPaused = !cur.isPaused
        if (nextPaused) {
            stopListening()
            _snapshot.value = cur.copy(
                isPaused = true,
                micActive = false,
                partialTranscript = "",
                voiceState = VoiceState.IDLE,
                audioLevel = 0f
            )
        } else {
            _snapshot.value = cur.copy(isPaused = false)
            if (cur.voiceModeActive) {
                startListening(continuous = true, forDictation = false)
            }
        }
    }

    fun startDictation() {
        stopSpeaking()
        stopListening()
        _snapshot.value = _snapshot.value.copy(
            isDictating = true,
            isPushToTalkActive = false,
            partialTranscript = "",
            lastVoiceError = null
        )
        startListening(continuous = false, forDictation = true)
    }

    fun stopDictation() {
        if (activeUsingDirectMic) {
            finishDirectMicrophoneCapture()
            return
        }
        _snapshot.value = _snapshot.value.copy(
            isDictating = false,
            voiceState = VoiceState.TRANSCRIBING
        )
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopDictation warning", e)
        }
    }

    fun startPushToTalk() {
        stopSpeaking()
        stopListening()
        _snapshot.value = _snapshot.value.copy(
            isPushToTalkActive = true,
            isDictating = false,
            partialTranscript = "",
            lastVoiceError = null
        )
        startListening(continuous = false, forDictation = false)
    }

    fun stopPushToTalk() {
        if (activeUsingDirectMic) {
            finishDirectMicrophoneCapture()
            return
        }
        _snapshot.value = _snapshot.value.copy(
            isPushToTalkActive = false,
            voiceState = VoiceState.TRANSCRIBING
        )
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening warning", e)
        }
    }

    fun startListening(continuous: Boolean, forDictation: Boolean = false) {
        activeContinuous = continuous
        activeForDictation = forDictation

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            startDirectMicrophoneCapture(continuous = continuous, forDictation = forDictation)
            return
        }

        activeUsingDirectMic = false
        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _snapshot.value = _snapshot.value.copy(
                        voiceState = VoiceState.LISTENING,
                        micActive = true,
                        usingDirectMicFallback = false,
                        lastVoiceError = null
                    )
                }

                override fun onBeginningOfSpeech() {
                    _snapshot.value = _snapshot.value.copy(
                        voiceState = VoiceState.LISTENING,
                        micActive = true
                    )
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _snapshot.value = _snapshot.value.copy(audioLevel = normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _snapshot.value = _snapshot.value.copy(
                        voiceState = VoiceState.TRANSCRIBING,
                        micActive = false,
                        audioLevel = 0.1f
                    )
                }

                override fun onError(error: Int) {
                    val shouldFallbackToAudioRecord = error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_SERVER ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    if (shouldFallbackToAudioRecord && onSpeechToTextAudio != null) {
                        Log.w(TAG, "SpeechRecognizer error $error; falling back to direct AudioRecord mic capture")
                        runCatching { speechRecognizer?.destroy() }
                        speechRecognizer = null
                        startDirectMicrophoneCapture(continuous = continuous, forDictation = forDictation)
                        return
                    }

                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NO_MATCH -> if (continuous) null else "No speech detected. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (continuous) null else "Speech timed out."
                        else -> "Speech recognizer error ($error)."
                    }
                    val cur = _snapshot.value
                    _snapshot.value = cur.copy(
                        micActive = false,
                        isPushToTalkActive = false,
                        isDictating = false,
                        partialTranscript = "",
                        voiceState = if (msg == null) VoiceState.IDLE else VoiceState.ERROR,
                        lastVoiceError = msg,
                        audioLevel = 0f
                    )
                    if (continuous && cur.voiceModeActive && !cur.isPaused &&
                        (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    ) {
                        startListening(continuous = true, forDictation = false)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotEmpty()) {
                        _snapshot.value = _snapshot.value.copy(partialTranscript = partial)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    _snapshot.value = _snapshot.value.copy(
                        micActive = false,
                        isPushToTalkActive = false,
                        isDictating = false,
                        partialTranscript = "",
                        voiceState = if (!forDictation && spoken.isNotEmpty()) VoiceState.THINKING else VoiceState.IDLE,
                        audioLevel = 0f
                    )
                    if (spoken.isNotEmpty()) {
                        if (forDictation) {
                            onDictationResult(spoken)
                        } else {
                            onRecognizedText(spoken)
                        }
                    } else if (continuous && _snapshot.value.voiceModeActive && !_snapshot.value.isPaused) {
                        startListening(continuous = true, forDictation = false)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                val langTag = when (activeLanguageCode) {
                    "hi" -> "hi-IN"
                    "mr" -> "mr-IN"
                    "auto" -> Locale.getDefault().toLanguageTag()
                    else -> "en-US"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "SpeechRecognizer failed; trying direct AudioRecord capture", e)
            startDirectMicrophoneCapture(continuous = continuous, forDictation = forDictation)
        }
    }

    private fun startDirectMicrophoneCapture(continuous: Boolean, forDictation: Boolean) {
        if (onSpeechToTextAudio == null) {
            _snapshot.value = _snapshot.value.copy(
                voiceModeActive = false,
                micActive = false,
                isPushToTalkActive = false,
                isDictating = false,
                voiceState = VoiceState.ERROR,
                lastVoiceError = "Speech recognition service is not available on this device."
            )
            return
        }
        activeUsingDirectMic = true
        activeContinuous = continuous
        activeForDictation = forDictation
        _snapshot.value = _snapshot.value.copy(
            voiceState = VoiceState.LISTENING,
            micActive = true,
            usingDirectMicFallback = true,
            partialTranscript = if (forDictation) {
                "Listening via Android Microphone — speak clearly, then tap Stop."
            } else {
                "Listening via Android Microphone — speak, then pause or tap Finish."
            },
            lastVoiceError = null
        )

        micCapturer.startCapture(
            autoStopOnSilence = !forDictation,
            onAudioLevel = { level ->
                _snapshot.value = _snapshot.value.copy(audioLevel = level)
            },
            onAutoSilenceStop = { wavBytes ->
                activeUsingDirectMic = false
                transcribeAudioAndDispatch(
                    wavBytes = wavBytes,
                    forDictation = forDictation,
                    resumeContinuous = continuous
                )
            },
            onError = { err ->
                activeUsingDirectMic = false
                _snapshot.value = _snapshot.value.copy(
                    micActive = false,
                    isPushToTalkActive = false,
                    isDictating = false,
                    voiceModeActive = false,
                    partialTranscript = "",
                    usingDirectMicFallback = false,
                    voiceState = VoiceState.ERROR,
                    lastVoiceError = err.localizedMessage ?: "Microphone capture failed.",
                    audioLevel = 0f
                )
            }
        )
    }

    private fun finishDirectMicrophoneCapture() {
        activeUsingDirectMic = false
        val wavBytes = micCapturer.stopAndEncodeWav()
        transcribeAudioAndDispatch(
            wavBytes = wavBytes,
            forDictation = activeForDictation,
            resumeContinuous = activeContinuous
        )
    }

    private fun transcribeAudioAndDispatch(
        wavBytes: ByteArray,
        forDictation: Boolean,
        resumeContinuous: Boolean
    ) {
        if (wavBytes.isEmpty()) {
            _snapshot.value = _snapshot.value.copy(
                micActive = false,
                isPushToTalkActive = false,
                isDictating = false,
                partialTranscript = "",
                usingDirectMicFallback = false,
                voiceState = VoiceState.IDLE,
                audioLevel = 0f
            )
            return
        }

        val sttHandler = onSpeechToTextAudio
        if (sttHandler == null) {
            _snapshot.value = _snapshot.value.copy(
                micActive = false,
                isPushToTalkActive = false,
                isDictating = false,
                partialTranscript = "",
                usingDirectMicFallback = false,
                voiceState = VoiceState.ERROR,
                lastVoiceError = "Speech-to-text handler unavailable.",
                audioLevel = 0f
            )
            return
        }

        _snapshot.value = _snapshot.value.copy(
            micActive = false,
            voiceState = VoiceState.TRANSCRIBING,
            partialTranscript = "Transcribing microphone audio...",
            audioLevel = 0.1f
        )

        transcribeJob?.cancel()
        transcribeJob = engineScope.launch {
            try {
                val spoken = sttHandler(wavBytes).trim()
                _snapshot.value = _snapshot.value.copy(
                    micActive = false,
                    isPushToTalkActive = false,
                    isDictating = false,
                    partialTranscript = "",
                    usingDirectMicFallback = false,
                    voiceState = if (!forDictation && spoken.isNotEmpty()) VoiceState.THINKING else VoiceState.IDLE,
                    audioLevel = 0f
                )
                if (spoken.isNotEmpty()) {
                    if (forDictation) {
                        onDictationResult(spoken)
                    } else {
                        onRecognizedText(spoken)
                    }
                } else {
                    if (resumeContinuous && _snapshot.value.voiceModeActive && !_snapshot.value.isPaused) {
                        startListening(continuous = true, forDictation = false)
                    } else if (!forDictation) {
                        _snapshot.value = _snapshot.value.copy(
                            lastVoiceError = "No speech detected. Try speaking closer to the microphone."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallback speech-to-text failed", e)
                _snapshot.value = _snapshot.value.copy(
                    micActive = false,
                    isPushToTalkActive = false,
                    isDictating = false,
                    partialTranscript = "",
                    usingDirectMicFallback = false,
                    voiceState = VoiceState.ERROR,
                    lastVoiceError = e.localizedMessage ?: "Speech-to-text failed.",
                    audioLevel = 0f
                )
            }
        }
    }

    fun stopListening() {
        activeUsingDirectMic = false
        transcribeJob?.cancel()
        micCapturer.cancelCapture()
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
        _snapshot.value = _snapshot.value.copy(
            micActive = false,
            isPushToTalkActive = false,
            isDictating = false,
            partialTranscript = "",
            usingDirectMicFallback = false,
            audioLevel = 0f
        )
    }

    fun cancelCapture() {
        stopListening()
        _snapshot.value = _snapshot.value.copy(
            voiceState = VoiceState.IDLE,
            lastVoiceError = null
        )
    }

    fun markThinking(thinking: Boolean) {
        val cur = _snapshot.value
        if (thinking) {
            _snapshot.value = cur.copy(voiceState = VoiceState.THINKING)
        } else if (cur.voiceState == VoiceState.THINKING) {
            _snapshot.value = cur.copy(voiceState = VoiceState.IDLE)
        }
    }

    fun speakIfEnabled(text: String, force: Boolean = false) {
        if (!force && (!voiceResponsesEnabled || !_snapshot.value.voiceModeActive)) return
        if (!ttsInitialized) return
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " code block ")
            .replace(Regex("[*#`_]"), "")
            .take(1500)
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "riya_utt_${System.currentTimeMillis()}")
    }

    fun testSpeaker(): Result<String> {
        return if (!ttsInitialized) {
            Result.failure(IllegalStateException("Text-to-Speech engine is not initialized yet."))
        } else {
            tts?.speak(
                "RIYA audio playback test succeeded.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "riya_test_${System.currentTimeMillis()}"
            )
            Result.success("Audio synthesized and played via Android TextToSpeech.")
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _snapshot.value = _snapshot.value.copy(
            assistantSpeaking = false,
            voiceState = VoiceState.INTERRUPTED,
            audioLevel = 0f
        )
    }

    fun clearError() {
        _snapshot.value = _snapshot.value.copy(
            lastVoiceError = null,
            voiceState = VoiceState.IDLE
        )
    }

    fun tickWaveform() {
        val cur = _snapshot.value
        phase = (phase + 0.35) % (2.0 * PI)
        val barCount = 24
        val center = (barCount - 1) / 2.0
        val level = cur.audioLevel.toDouble()

        val bars = (0 until barCount).map { idx ->
            val distScale = 1.0 - 0.55 * (abs(idx - center) / max(1.0, center))
            val value = when (cur.voiceState) {
                VoiceState.LISTENING -> {
                    val amp = 0.14 + 0.86 * min(1.0, max(0.0, level))
                    val wave = 0.5 + 0.5 * sin(phase + idx * 0.45)
                    amp * distScale * (0.45 + 0.55 * wave)
                }
                VoiceState.SPEAKING -> {
                    val amp = 0.22 + 0.78 * min(1.0, max(0.25, level))
                    val wave = 0.5 + 0.5 * sin(phase * 1.4 + idx * 0.55)
                    amp * distScale * (0.40 + 0.60 * wave)
                }
                VoiceState.TRANSCRIBING, VoiceState.THINKING -> {
                    val pulse = 0.5 + 0.5 * sin(phase * 0.9 - idx * 0.35)
                    0.15 + 0.35 * pulse * distScale
                }
                else -> 0.08 * distScale
            }
            max(0.04, min(1.0, value)).toFloat()
        }
        _snapshot.value = cur.copy(waveformBars = bars)
    }

    fun release() {
        stopListening()
        micCapturer.release()
        engineScope.cancel()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
        }
    }
}
