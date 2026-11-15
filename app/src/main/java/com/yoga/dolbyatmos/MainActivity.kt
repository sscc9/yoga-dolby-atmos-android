package com.yoga.dolbyatmos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.service.DolbyAudioService
import com.yoga.dolbyatmos.ui.DolbyAccessScreen
import com.yoga.dolbyatmos.ui.theme.YogaDolbyTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Start service regardless of notification grant; notification simply shows status when permitted
        DolbyAudioService.startOrUpdate(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DolbyRepository.init(this)
        captureExternalEqualizerIntent(intent)
        ensureServiceAndNotificationPermission()

        setContent {
            YogaDolbyTheme {
                val uiState by DolbyRepository.state.collectAsState()
                DolbyAccessScreen(
                    state = uiState,
                    onMasterToggle = { enabled ->
                        DolbyRepository.setMasterEnabled(enabled)
                        DolbyAudioService.startOrUpdate(this)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureExternalEqualizerIntent(intent)
    }

    private fun captureExternalEqualizerIntent(intent: Intent?) {
        if (intent?.action == AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL) {
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                ?: callingPackage.orEmpty()
            if (sessionId > 0) {
                DolbyRepository.registerAudioSession(sessionId, pkg)
                val sync = Intent(this, DolbyAudioService::class.java).apply {
                    action = DolbyAudioService.ACTION_SYNC_SESSION
                    putExtra(DolbyAudioService.EXTRA_SESSION_ID, sessionId)
                    putExtra(DolbyAudioService.EXTRA_SESSION_OPEN, true)
                    putExtra(DolbyAudioService.EXTRA_PACKAGE_NAME, pkg)
                }
                ContextCompat.startForegroundService(this, sync)
            }
        }
    }

    private fun ensureServiceAndNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        DolbyAudioService.startOrUpdate(this)
    }
}
