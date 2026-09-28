package com.example.ayneta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech
    private val TAG = "AYNETA_Main"

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startAynetaServices()
        } else {
            tts.speak("Permissions denied. AYNETA cannot run.", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS first
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set language to US English
                tts.language = Locale.US

                // 🎙️ MAKE IT CALM AND HUMAN-LIKE
                tts.setSpeechRate(0.80f) // Slower than default (1.0f). Try 0.8f if still too fast.
                tts.setPitch(0.9f)       // Normal pitch. Try 0.9f for a slightly deeper, calmer voice.

                // Now check permissions and start
                checkPermissionsAndStart()
            } else {
                Log.e(TAG, "TTS Initialization failed")
            }
        }

        // Empty UI (Zero-Touch)
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // No buttons. Audio only.
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        // Android 13+ needs notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startAynetaServices()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startAynetaServices() {
        Log.d(TAG, "Starting Services...")

        // Start Voice Service (Wake Word / Mic)
        val voiceIntent = Intent(this, AynetaVoiceService::class.java)
        ContextCompat.startForegroundService(this, voiceIntent)

        tts.speak("Ayneta is activated. I am listening.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}