package com.yoga.dolbyatmos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yoga.dolbyatmos.MainActivity
import com.yoga.dolbyatmos.R
import com.yoga.dolbyatmos.audio.DolbyDspEngine
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.model.DolbyUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DolbyAudioService : Service() {

    companion object {
        private const val CHANNEL_ID = "yoga_dolby_dsp_channel"
        private const val NOTIFICATION_ID = 8301

        const val ACTION_SYNC_SESSION = "com.yoga.dolbyatmos.ACTION_SYNC_SESSION"
        const val ACTION_TOGGLE_MASTER = "com.yoga.dolbyatmos.ACTION_TOGGLE_MASTER"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_SESSION_OPEN = "extra_session_open"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun startOrUpdate(context: Context) {
            val appCtx = context.applicationContext
            DolbyRepository.init(appCtx)
            val intent = Intent(appCtx, DolbyAudioService::class.java)
            try {
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (_: Throwable) {
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var dspEngine: DolbyDspEngine

    private val dynamicSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()
            if (sessionId <= 0) return

            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    DolbyRepository.registerAudioSession(sessionId, pkg)
                    dspEngine.onSessionOpened(sessionId)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    DolbyRepository.unregisterAudioSession(sessionId)
                    dspEngine.onSessionClosed(sessionId)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DolbyRepository.init(this)
        createNotificationChannel()

        val initialNotification = buildNotification(DolbyRepository.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        dspEngine = DolbyDspEngine(this)
        dspEngine.start()

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(
            this,
            dynamicSessionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        serviceScope.launch {
            DolbyRepository.state.collectLatest { uiState ->
                dspEngine.applyState(uiState)
                updateNotification(uiState)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_MASTER -> {
                val next = !DolbyRepository.state.value.masterEnabled
                DolbyRepository.setMasterEnabled(next)
            }
            ACTION_SYNC_SESSION -> {
                val sid = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                val open = intent.getBooleanExtra(EXTRA_SESSION_OPEN, true)
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                if (sid > 0) {
                    if (open) {
                        DolbyRepository.registerAudioSession(sid, pkg)
                        dspEngine.onSessionOpened(sid)
                    } else {
                        DolbyRepository.unregisterAudioSession(sid)
                        dspEngine.onSessionClosed(sid)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dynamicSessionReceiver)
        } catch (_: Throwable) {
        }
        dspEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "杜比音效实时引擎",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 20 段零延迟动态音频引擎在后台生效"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(state: DolbyUiState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: DolbyUiState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DolbyAudioService::class.java).apply { action = ACTION_TOGGLE_MASTER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.masterEnabled) {
            "杜比音效已开启 · ${state.currentProfile.titleCn}模式"
        } else {
            "杜比音效已暂停（直通原声）"
        }

        val subtitle = buildString {
            append(state.effectiveEndpoint.labelCn)
            append("（")
            append(state.connectedDeviceName)
            append("）")
        }

        val toggleLabel = if (state.masterEnabled) "暂停音效" else "开启音效"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openIntent)
            .setOngoing(state.masterEnabled)
            .setSilent(true)
            .addAction(0, toggleLabel, toggleIntent)
            .build()
    }
}
