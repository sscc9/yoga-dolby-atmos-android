package com.yoga.dolbyatmos.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.service.DolbyAudioService

class AudioSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        DolbyRepository.init(appCtx)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (DolbyRepository.state.value.masterEnabled) {
                    DolbyAudioService.startOrUpdate(appCtx)
                }
            }
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()
                if (sessionId <= 0) return

                val isOpen = intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                if (isOpen) {
                    DolbyRepository.registerAudioSession(sessionId, pkg)
                } else {
                    DolbyRepository.unregisterAudioSession(sessionId)
                }

                if (DolbyRepository.state.value.masterEnabled) {
                    val svcIntent = Intent(appCtx, DolbyAudioService::class.java).apply {
                        action = DolbyAudioService.ACTION_SYNC_SESSION
                        putExtra(DolbyAudioService.EXTRA_SESSION_ID, sessionId)
                        putExtra(DolbyAudioService.EXTRA_SESSION_OPEN, isOpen)
                        putExtra(DolbyAudioService.EXTRA_PACKAGE_NAME, pkg)
                    }
                    try {
                        ContextCompat.startForegroundService(appCtx, svcIntent)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
