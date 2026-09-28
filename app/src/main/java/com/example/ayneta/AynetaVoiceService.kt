package com.example.ayneta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class AynetaVoiceService : Service() {

    companion object {
        private const val TAG = "AynetaVoiceService"
        private const val CHANNEL_ID = "AYNETA_VOICE"
        private const val NOTIFICATION_ID = 2
    }

    //  STATE FLAG: Remembers if the camera is currently on
    private var isCameraActive = false

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setSpeechRate(0.85f)
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AYNETA Voice")
            .setContentText("Listening for commands...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startListening()

        return START_STICKY
    }

    private fun startListening() {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listener: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                mainHandler.postDelayed({ startListening() }, 500)
            }

            override fun onError(error: Int) {
                mainHandler.postDelayed({ startListening() }, 2000)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (text in matches) {
                        val spokenText = text.lowercase().trim()
                        Log.d(TAG, "Heard: $spokenText")

                        // 1. ACTIVATION LOGIC
                        if (spokenText.contains("ayneta") && spokenText.contains("activated")) {
                            if (isCameraActive) {
                                tts.speak("You are already activated.", TextToSpeech.QUEUE_FLUSH, null, null)
                            } else {
                                handleActivation()
                            }
                            return
                        }

                        // 2. DEACTIVATION LOGIC
                        if (spokenText.contains("ayneta") && spokenText.contains("deactivated")) {
                            handleDeactivation()
                            return
                        }
                    }
                }
                mainHandler.postDelayed({ startListening() }, 500)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleActivation() {
        Log.i(TAG, "ACTIVATION PHRASE DETECTED!")
        isCameraActive = true // Update state

        // Start the Camera Service
        ContextCompat.startForegroundService(this, Intent(this, AynetaCameraService::class.java))
        tts.speak("Yes? I am listening.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun handleDeactivation() {
        Log.i(TAG, "DEACTIVATION PHRASE DETECTED! Shutting down.")
        isCameraActive = false // Reset state

        // Stop the Camera Service
        stopService(Intent(this, AynetaCameraService::class.java))

        tts.speak("Goodbye. Shutting down.", TextToSpeech.QUEUE_FLUSH, null, null)

        // Wait for TTS to finish, then permanently kill the voice service
        mainHandler.postDelayed({
            stopSelf()
        }, 2500)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AYNETA Voice",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}