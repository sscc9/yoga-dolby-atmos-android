package com.yoga.dolbyatmos

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.service.DolbyAudioService
import com.yoga.dolbyatmos.ui.DolbyAccessScreen
import com.yoga.dolbyatmos.ui.theme.YogaDolbyTheme

class MainActivity : ComponentActivity() {

    private val resumeRefreshTick = mutableIntStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        DolbyRepository.markNotificationPermissionAsked()
        DolbyAudioService.startOrUpdate(this)
        resumeRefreshTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DolbyRepository.init(this)
        applyExcludeFromRecents(DolbyRepository.state.value.excludeFromRecents)
        captureExternalEqualizerIntent(intent)
        ensureServiceAndNotificationPermission()

        setContent {
            YogaDolbyTheme {
                val uiState by DolbyRepository.state.collectAsState()
                val refreshTick = resumeRefreshTick.intValue
                val batteryIgnored = rememberSystemStatus(refreshTick) { isIgnoringBatteryOptimizations() }
                val notificationsVisible = rememberSystemStatus(refreshTick) { areNotificationsVisible() }

                DolbyAccessScreen(
                    state = uiState,
                    isBatteryOptimizationIgnored = batteryIgnored,
                    areNotificationsVisible = notificationsVisible,
                    onMasterToggle = { enabled ->
                        DolbyRepository.setMasterEnabled(enabled)
                        DolbyAudioService.startOrUpdate(this)
                    },
                    onRebindDsp = {
                        DolbyAudioService.forceRebind(this)
                    },
                    onToggleExcludeFromRecents = { exclude ->
                        DolbyRepository.setExcludeFromRecents(exclude)
                        applyExcludeFromRecents(exclude)
                    },
                    onRequestIgnoreBatteryOptimization = {
                        requestIgnoreBatteryOptimization()
                    },
                    onOpenAppDetailsSettings = {
                        openAppDetailsSettings()
                    },
                    onOpenNotificationSettings = {
                        openNotificationSettings()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyExcludeFromRecents(DolbyRepository.state.value.excludeFromRecents)
        resumeRefreshTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureExternalEqualizerIntent(intent)
    }

    private fun applyExcludeFromRecents(exclude: Boolean) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(exclude)
            }
        } catch (_: Throwable) {
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(packageName) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun areNotificationsVisible(): Boolean {
        return try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return true
            if (!nm.areNotificationsEnabled()) return false
            val channel = nm.getNotificationChannel("yoga_dolby_dsp_channel")
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } catch (_: Throwable) {
            true
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Throwable) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun openNotificationSettings() {
        try {
            val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, "yoga_dolby_dsp_channel")
            }
            startActivity(channelIntent)
        } catch (_: Throwable) {
            try {
                val appNotifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(appNotifIntent)
            } catch (_: Throwable) {
                openAppDetailsSettings()
            }
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !DolbyRepository.hasAskedNotificationPermission()) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            } else {
                DolbyRepository.markNotificationPermissionAsked()
            }
        }
        DolbyAudioService.startOrUpdate(this)
    }
}

@androidx.compose.runtime.Composable
private fun rememberSystemStatus(tick: Int, provider: () -> Boolean): Boolean {
    return androidx.compose.runtime.remember(tick) { provider() }
}
