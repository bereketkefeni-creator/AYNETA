package com.example.ayneta.features.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface SpeechToTextRepository {
    suspend fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit
    )

    fun stopListening()
}

class AndroidSpeechToTextRepository(
    private val context: Context
) : SpeechToTextRepository {

    @Volatile
    private var activeRecognizer: SpeechRecognizer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit
    ) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission has not been granted."
        }

        withContext(Dispatchers.Main.immediate) {
            check(SpeechRecognizer.isRecognitionAvailable(context)) {
                "No speech recognition service is installed on this device."
            }
            check(activeRecognizer == null) { "Speech recognition is already running." }

            suspendCancellableCoroutine { continuation ->
                // Use the device's default recognition service so it can use its online
                // language models when an on-device model is not installed.
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                activeRecognizer = recognizer

                var triedEnglishFallback = false
                var completed = false

                fun finish(result: Result<String>) {
                    if (completed) return
                    completed = true
                    if (activeRecognizer === recognizer) activeRecognizer = null
                    recognizer.destroy()
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = {
                                onFinalResult(it)
                                continuation.resume(Unit)
                            },
                            onFailure = continuation::resumeWithException
                        )
                    }
                }

                fun startWithLanguage(languageTag: String) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    recognizer.startListening(intent)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        onPartialResult("Listening for Amharic speech...")
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!partial.isNullOrBlank()) onPartialResult(partial)
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val finalText = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.takeIf(String::isNotBlank)
                            ?: "No speech detected."
                        finish(Result.success(finalText))
                    }

                    override fun onError(error: Int) {
                        if (!triedEnglishFallback && error in LANGUAGE_UNAVAILABLE_ERRORS) {
                            triedEnglishFallback = true
                            runCatching { startWithLanguage(ENGLISH_LOCALE) }
                                .onFailure { finish(Result.failure(it)) }
                        } else {
                            finish(Result.failure(IOException(errorMessage(error))))
                        }
                    }
                })

                onPartialResult("Starting speech recognition...")
                runCatching { startWithLanguage(AMHARIC_LOCALE) }
                    .onFailure { finish(Result.failure(it)) }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        if (activeRecognizer === recognizer) activeRecognizer = null
                        runCatching { recognizer.cancel() }
                        recognizer.destroy()
                    }
                }
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            runCatching {
                activeRecognizer?.stopListening()
            }
        }
    }

    private companion object {
        const val AMHARIC_LOCALE = "am-ET"
        const val ENGLISH_LOCALE = "en-US"
        val LANGUAGE_UNAVAILABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
        )

        fun errorMessage(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording failed. Please check the microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition was stopped."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The device speech service needs a network connection."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "The device speech service returned an error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard. Please try again."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Amharic and English speech models are unavailable on this device."
            else -> "Speech recognition failed (error $error)."
        }
    }
}
