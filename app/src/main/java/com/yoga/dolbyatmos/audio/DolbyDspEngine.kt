package com.yoga.dolbyatmos.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.model.ActiveEndpoint
import com.yoga.dolbyatmos.model.DolbyUiState
import com.yoga.dolbyatmos.model.YogaDolbyTuningData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Suppress("DEPRECATION")
class DolbyDspEngine(context: Context) {

    companion object {
        private const val TAG = "YogaDolbyDSP"
        private const val GLOBAL_SESSION_ID = 0
        private const val EFFECT_PRIORITY = 100
    }

    private data class SessionEffects(
        val sessionId: Int,
        val dp: DynamicsProcessing?,
        val virtualizer: Virtualizer?
    )

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionEffectsMap = ConcurrentHashMap<Int, SessionEffects>()

    @Volatile
    private var detectedHeadphone: Boolean = false

    @Volatile
    private var connectedDeviceName: String = "手机内置扬声器"

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDeviceAndReapply()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDeviceAndReapply()
        }
    }

    fun start() {
        refreshOutputDeviceStateOnly()
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        applyState(DolbyRepository.state.value)
    }

    fun stop() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {
        }
        releaseAllSessions()
    }

    fun onSessionOpened(sessionId: Int) {
        if (sessionId <= 0) return
        ensureSessionAttached(sessionId)
        applyStateToSession(sessionId, DolbyRepository.state.value)
        publishRuntimeStatus()
    }

    fun onSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        releaseSession(sessionId)
        publishRuntimeStatus()
    }

    @Synchronized
    fun applyState(state: DolbyUiState) {
        // Manage global mix (session 0) fallback for players that don't broadcast audio sessions
        if (state.masterEnabled && state.globalMixFallback) {
            ensureSessionAttached(GLOBAL_SESSION_ID)
        } else {
            releaseSession(GLOBAL_SESSION_ID)
        }

        // Attach any known active player sessions
        if (state.masterEnabled) {
            for (sid in DolbyRepository.activeSessions.value.keys) {
                ensureSessionAttached(sid)
            }
        }

        for (sid in sessionEffectsMap.keys) {
            applyStateToSession(sid, state)
        }
        publishRuntimeStatus()
    }

    private fun refreshOutputDeviceAndReapply() {
        refreshOutputDeviceStateOnly()
        applyState(DolbyRepository.state.value)
    }

    private fun refreshOutputDeviceStateOnly() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var foundHeadphone: AudioDeviceInfo? = null

        for (dev in outputs) {
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                    foundHeadphone = dev
                    break
                }
            }
        }

        if (foundHeadphone != null) {
            detectedHeadphone = true
            val prod = foundHeadphone.productName?.toString()?.trim().orEmpty()
            val typeLabel = when (foundHeadphone.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙耳机"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 数字耳机"
                else -> "外接音频设备"
            }
            connectedDeviceName = if (prod.isNotEmpty() && !prod.equals("Android", ignoreCase = true)) {
                "$typeLabel · $prod"
            } else {
                typeLabel
            }
        } else {
            detectedHeadphone = false
            connectedDeviceName = "手机内置扬声器"
        }
    }

    private fun publishRuntimeStatus() {
        val globalAttached = sessionEffectsMap[GLOBAL_SESSION_ID]?.dp != null
        val appSessions = sessionEffectsMap.keys.count { it != GLOBAL_SESSION_ID }
        DolbyRepository.updateRuntimeHardwareStatus(
            detectedHeadphone = detectedHeadphone,
            deviceName = connectedDeviceName,
            globalMixAttached = globalAttached,
            sessionCount = appSessions
        )
    }

    private fun ensureSessionAttached(sessionId: Int) {
        if (sessionEffectsMap.containsKey(sessionId)) return

        var dp: DynamicsProcessing? = null
        var virt: Virtualizer? = null

        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                YogaDolbyTuningData.BAND_COUNT,
                true,
                YogaDolbyTuningData.BAND_COUNT,
                true,
                YogaDolbyTuningData.BAND_COUNT,
                true
            ).build()

            dp = DynamicsProcessing(EFFECT_PRIORITY, sessionId, config)
        } catch (e: Throwable) {
            Log.w(TAG, "DynamicsProcessing attach skipped for session $sessionId: ${e.message}")
        }

        try {
            virt = Virtualizer(EFFECT_PRIORITY, sessionId)
        } catch (e: Throwable) {
            Log.w(TAG, "Virtualizer attach skipped for session $sessionId: ${e.message}")
        }

        if (dp != null || virt != null) {
            sessionEffectsMap[sessionId] = SessionEffects(sessionId, dp, virt)
        }
    }

    private fun applyStateToSession(sessionId: Int, state: DolbyUiState) {
        val holder = sessionEffectsMap[sessionId] ?: return
        val activeEndpoint = if (state.endpointOverride == com.yoga.dolbyatmos.model.EndpointOverride.AUTO) {
            if (detectedHeadphone) ActiveEndpoint.HEADPHONE else ActiveEndpoint.SPEAKER
        } else {
            state.effectiveEndpoint
        }

        // 1. Configure 20-Band DynamicsProcessing (PreEQ + Multi-Band Compressor + PostEQ + Brickwall Limiter)
        holder.dp?.let { dp ->
            try {
                if (!state.masterEnabled) {
                    dp.enabled = false
                } else {
                    val composite20Db = YogaDolbyTuningData.computeComposite20BandDb(state, activeEndpoint)
                    val peakGain = composite20Db.maxOrNull()?.coerceAtLeast(0f) ?: 0f
                    // Automatic headroom attenuation so boosted bands never clip digital 0 dBFS
                    val inputHeadroomDb = -(peakGain * 0.62f).coerceIn(0f, 6.5f)
                    dp.setInputGainAllChannelsTo(inputHeadroomDb)

                    // Split 20-band shaping cleanly: 40% PreEQ (drives MBC smoothly) + 60% PostEQ (final tonal contour)
                    val preEq = DynamicsProcessing.Eq(true, true, YogaDolbyTuningData.BAND_COUNT)
                    val postEq = DynamicsProcessing.Eq(true, true, YogaDolbyTuningData.BAND_COUNT)
                    for (i in 0 until YogaDolbyTuningData.BAND_COUNT) {
                        val cutoff = YogaDolbyTuningData.BAND_CUTOFFS_HZ[i]
                        val totalGain = composite20Db[i]
                        preEq.setBand(i, DynamicsProcessing.EqBand(true, cutoff, totalGain * 0.40f))
                        postEq.setBand(i, DynamicsProcessing.EqBand(true, cutoff, totalGain * 0.60f))
                    }
                    dp.setPreEqAllChannelsTo(preEq)
                    dp.setPostEqAllChannelsTo(postEq)

                    // 20-Band Multi-Band Compressor (replicates Dolby Volume Leveler + Dialog Upward Focus + Speaker Excursion Regulator)
                    val mbcEnabled = state.volumeLevelerEnabled ||
                        state.dialogEnhancerEnabled ||
                        activeEndpoint == ActiveEndpoint.SPEAKER
                    val mbc = DynamicsProcessing.Mbc(true, mbcEnabled, YogaDolbyTuningData.BAND_COUNT)

                    val levelerStrength = if (state.volumeLevelerEnabled) (state.volumeLevelerAmount / 10f) else 0f
                    for (i in 0 until YogaDolbyTuningData.BAND_COUNT) {
                        val cutoff = YogaDolbyTuningData.BAND_CUTOFFS_HZ[i]
                        val centerHz = YogaDolbyTuningData.BAND_CENTERS_HZ[i]

                        var thresholdDb = -16f - (levelerStrength * 12f) // -16 dBFS down to -28 dBFS
                        var ratio = 1.15f + (levelerStrength * 1.65f)    // 1.15:1 up to 2.8:1 smooth leveling
                        var postGainDb = levelerStrength * 2.8f          // Gentle upward makeup for quiet passages

                        // Speaker low-frequency excursion protection from YOGA XML <regulator-tuning>
                        if (activeEndpoint == ActiveEndpoint.SPEAKER && i <= 6) {
                            val spkLim = YogaDolbyTuningData.SPEAKER_REGULATOR_THRESHOLDS_DB[i]
                            thresholdDb = minOf(thresholdDb, spkLim)
                            ratio = max(ratio, 3.2f)
                        }

                        // Dialog band dynamic density (800Hz - 3800Hz)
                        if (state.dialogEnhancerEnabled && centerHz in 800f..3800f) {
                            val deNorm = state.dialogEnhancerAmount / 10f
                            ratio = max(ratio, 1.3f + deNorm * 0.8f)
                            postGainDb += deNorm * 1.2f
                        }

                        mbc.setBand(
                            i,
                            DynamicsProcessing.MbcBand(
                                true,
                                cutoff,
                                18f,          // 18ms fast-smooth attack
                                165f,         // 165ms natural musical release
                                ratio,
                                thresholdDb.coerceIn(-45f, -2f),
                                6.0f,         // 6dB soft knee (transparent Dolby-style compression)
                                -78f,         // Noise gate threshold
                                1.0f,         // No harsh downward expansion
                                0f,
                                postGainDb.coerceIn(-6f, 6f)
                            )
                        )
                    }
                    dp.setMbcAllChannelsTo(mbc)

                    // Final transparent brickwall safety limiter (-0.8 dBFS)
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        2.0f,
                        60.0f,
                        10.0f,
                        -0.8f,
                        0.0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)

                    if (!dp.enabled) {
                        dp.enabled = true
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed updating DynamicsProcessing on session $sessionId: ${e.message}")
            }
        }

        // 2. Configure Hardware Stereo / Surround Virtualizer (<surround-boost value="96">)
        holder.virtualizer?.let { virt ->
            try {
                val shouldEnable = state.masterEnabled && state.surroundEnabled && state.surroundAmount > 0.1f
                if (!shouldEnable) {
                    virt.enabled = false
                } else {
                    if (virt.strengthSupported) {
                        val endpointFactor = if (activeEndpoint == ActiveEndpoint.HEADPHONE) 1.0f else 0.65f
                        val strength = (state.surroundAmount * 100f * endpointFactor).toInt().coerceIn(0, 1000).toShort()
                        virt.setStrength(strength)
                    }
                    if (!virt.enabled) {
                        virt.enabled = true
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed updating Virtualizer on session $sessionId: ${e.message}")
            }
        }
    }

    private fun releaseSession(sessionId: Int) {
        val removed = sessionEffectsMap.remove(sessionId) ?: return
        try {
            removed.dp?.enabled = false
            removed.dp?.release()
        } catch (_: Throwable) {
        }
        try {
            removed.virtualizer?.enabled = false
            removed.virtualizer?.release()
        } catch (_: Throwable) {
        }
    }

    private fun releaseAllSessions() {
        val keys = sessionEffectsMap.keys.toList()
        for (sid in keys) {
            releaseSession(sid)
        }
        publishRuntimeStatus()
    }
}
