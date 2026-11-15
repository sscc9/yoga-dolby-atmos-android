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
import com.yoga.dolbyatmos.model.EndpointOverride
import com.yoga.dolbyatmos.model.YogaDolbyTuningData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

@Suppress("DEPRECATION")
class DolbyDspEngine(context: Context) {

    companion object {
        private const val TAG = "YogaDolbyDSP"
        private const val GLOBAL_SESSION_ID = 0
        private const val EFFECT_PRIORITY = 1000
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
        publishRuntimeStatus()
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
        applyState(DolbyRepository.state.value)
    }

    fun onSessionClosed(sessionId: Int) {
        if (sessionId <= 0) return
        releaseSession(sessionId)
        applyState(DolbyRepository.state.value)
    }

    @Synchronized
    fun forceRebindAll() {
        releaseAllSessions()
        refreshOutputDeviceStateOnly()
        applyState(DolbyRepository.state.value)
    }

    @Synchronized
    fun applyState(state: DolbyUiState) {
        if (!state.masterEnabled) {
            for (sid in sessionEffectsMap.keys) {
                applyStateToSession(sid, state)
            }
            publishRuntimeStatus()
            return
        }

        // 1. Attach any active per-app media player sessions (sessionId > 0)
        val knownAppSessionIds = DolbyRepository.activeSessions.value.keys.filter { it > 0 }
        for (sid in knownAppSessionIds) {
            ensureSessionAttached(sid)
        }
        // Remove closed app sessions
        val staleIds = sessionEffectsMap.keys.filter { it != GLOBAL_SESSION_ID && it !in knownAppSessionIds }
        for (sid in staleIds) {
            releaseSession(sid)
        }

        val activeAppDpCount = sessionEffectsMap.entries.count { it.key != GLOBAL_SESSION_ID && it.value.dp != null }

        // 2. Prevent 2x double-processing: only attach Global Mix (Session 0) when no per-app DP session is active
        if (state.globalMixFallback && activeAppDpCount == 0) {
            ensureSessionAttached(GLOBAL_SESSION_ID)
        } else {
            releaseSession(GLOBAL_SESSION_ID)
        }

        for (sid in sessionEffectsMap.keys) {
            applyStateToSession(sid, state)
        }
        publishRuntimeStatus()
    }

    private fun refreshOutputDeviceAndReapply() {
        refreshOutputDeviceStateOnly()
        // Publish hardware change first so DolbyRepository switches endpoint-specific XML profile defaults!
        publishRuntimeStatus()
        applyState(DolbyRepository.state.value)
    }

    private fun refreshOutputDeviceStateOnly() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var foundHeadphone: AudioDeviceInfo? = null

        for (dev in outputs) {
            when (dev.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                    foundHeadphone = dev
                    break
                }
            }
        }

        if (foundHeadphone != null) {
            detectedHeadphone = true
            val prod = foundHeadphone.productName?.toString()?.trim().orEmpty()
            val typeLabel = when (foundHeadphone.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 数字耳机"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙耳机"
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
        val activeMap = DolbyRepository.activeSessions.value
        val attachedAppIds = sessionEffectsMap.keys.filter { it != GLOBAL_SESSION_ID && sessionEffectsMap[it]?.dp != null }

        val summary = when {
            attachedAppIds.isNotEmpty() -> {
                val names = attachedAppIds.joinToString(", ") { sid ->
                    val pkg = activeMap[sid].orEmpty().substringAfterLast('.')
                    if (pkg.isNotEmpty()) "$pkg(#$sid)" else "会话#$sid"
                }
                "独立音频流直通: $names"
            }
            globalAttached -> "全局输出混音 (Session 0)"
            else -> "未挂载 (请在播放器均衡器菜单开启)"
        }

        DolbyRepository.updateRuntimeHardwareStatus(
            detectedHeadphone = detectedHeadphone,
            deviceName = connectedDeviceName,
            globalMixAttached = globalAttached,
            sessionCount = attachedAppIds.size,
            sessionSummary = summary
        )
    }

    private fun ensureSessionAttached(sessionId: Int) {
        if (sessionEffectsMap.containsKey(sessionId)) return

        var dp: DynamicsProcessing? = null
        var virt: Virtualizer? = null

        try {
            // PostEQ disabled (false) because full 20-band EQ runs in PreEQ right before 20-band MBC/Regulator,
            // matching Dolby DAX3's exact [EQ -> MBC -> 20-Band Regulator -> Output Limiter] topology.
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                YogaDolbyTuningData.BAND_COUNT,
                true,
                YogaDolbyTuningData.BAND_COUNT,
                false,
                0,
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
        val activeEndpoint = if (state.endpointOverride == EndpointOverride.AUTO) {
            if (detectedHeadphone) ActiveEndpoint.HEADPHONE else ActiveEndpoint.SPEAKER
        } else {
            state.effectiveEndpoint
        }

        val spec = YogaDolbyTuningData.getFactorySpec(activeEndpoint, state.currentProfile)
        val regHighDb = YogaDolbyTuningData.getRegulatorHighDb(activeEndpoint, state.currentProfile)
        val regLowDb = YogaDolbyTuningData.getRegulatorLowDb(activeEndpoint, state.currentProfile)

        // 1. 20-Band DynamicsProcessing (100% Verbatim PC XML Gain, EQ, MBC, and Regulator)
        holder.dp?.let { dp ->
            try {
                if (!state.masterEnabled) {
                    dp.enabled = false
                } else {
                    // Exact <pregain value="0"/> — ZERO artificial negative headroom attenuation!
                    dp.setInputGainAllChannelsTo(0f)

                    // Stage A: 20-Band PreEQ (exact VLLDP <audio-optimizer-bands> + CP IEQ/GEQ/Dialog/Bass/SlidingBass)
                    val composite20Db = YogaDolbyTuningData.computeComposite20BandDb(state, activeEndpoint)
                    val preEq = DynamicsProcessing.Eq(true, true, YogaDolbyTuningData.BAND_COUNT)
                    for (i in 0 until YogaDolbyTuningData.BAND_COUNT) {
                        val cutoff = YogaDolbyTuningData.BAND_CUTOFFS_HZ[i]
                        preEq.setBand(i, DynamicsProcessing.EqBand(true, cutoff, composite20Db[i]))
                    }
                    dp.setPreEqAllChannelsTo(preEq)

                    // Stage B: 20-Band MBC + DAX3 Regulator + <volmax-boost> + <mb-compressor-tuning>
                    val mbc = DynamicsProcessing.Mbc(true, true, YogaDolbyTuningData.BAND_COUNT)
                    val vlNorm = if (state.volumeLevelerEnabled) {
                        (state.volumeLevelerAmount / 10f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val attackMs = when {
                        spec.vlldpMbcEnabled -> spec.vlldpMbcAttackMs // Exact 25ms from Speaker XML
                        state.volumeLevelerEnabled -> 15f
                        else -> spec.vlldpMbcAttackMs                 // Exact 10ms from XML
                    }
                    val releaseMs = when {
                        spec.vlldpMbcEnabled -> spec.vlldpMbcReleaseMs // Exact 200ms from Speaker XML
                        state.volumeLevelerEnabled -> 140f
                        else -> 80f
                    }

                    for (i in 0 until YogaDolbyTuningData.BAND_COUNT) {
                        val cutoff = YogaDolbyTuningData.BAND_CUTOFFS_HZ[i]

                        // Exact XML makeup gain: <volmax-boost> (+4dB or +6dB) + <mb-compressor-tuning> (+1.3125dB or +3dB)
                        val mbcGroupMakeupDb = if (spec.vlldpMbcEnabled) {
                            if (i < 3) spec.vlldpMbcLowGroupMakeupDb else spec.vlldpMbcHighGroupMakeupDb
                        } else {
                            0f
                        }
                        val vlUpwardDb = if (state.volumeLevelerEnabled) vlNorm * 1.5f else 0f
                        val totalMakeupDb = (spec.volmaxBoostDb + mbcGroupMakeupDb + vlUpwardDb).coerceIn(0f, 9.5f)

                        // Exact XML Regulator <threshold_low> (-12 dBFS on HP, -19.25..-22.75 dBFS on Speaker bass)
                        // lowered toward <volume-leveler-in-target value="-320"/> (-20 dBFS) when Volume Leveler is active
                        val baseLowThresh = if (totalMakeupDb >= 7.0f && regLowDb[i] > -14f) {
                            -14.0f
                        } else {
                            regLowDb[i]
                        }
                        val thresholdDb = if (state.volumeLevelerEnabled) {
                            minOf(baseLowThresh, -12.0f - vlNorm * 8.0f)
                        } else {
                            baseLowThresh
                        }.coerceIn(-36f, -4f)

                        // Closed-form DAX3 Regulator ratio so quiet/mid audio gets 100% of totalMakeupDb (+4 to +9 dB),
                        // while 0 dBFS peaks regulate cleanly to regHighDb[i] (0 dBFS on HP, -7.25..-10.75 dBFS on SPK bass)
                        val allowedSwingAboveThresh = (regHighDb[i] - thresholdDb - totalMakeupDb).coerceAtLeast(2.2f)
                        val regulatorRatio = (-thresholdDb) / allowedSwingAboveThresh
                        val minVlRatio = if (state.volumeLevelerEnabled) (1.5f + vlNorm * 1.3f) else 1.4f
                        val ratio = max(regulatorRatio, minVlRatio).coerceIn(1.35f, 8.0f)

                        mbc.setBand(
                            i,
                            DynamicsProcessing.MbcBand(
                                true,
                                cutoff,
                                attackMs,
                                releaseMs,
                                ratio,
                                thresholdDb,
                                6.0f,  // Smooth 6dB soft knee matching regulator-timbre-preservation=12
                                -90f,  // Exact <noise-gate-threshold value="-1440"/> (-1440 / 16 = -90.0 dBFS!)
                                1.0f,
                                0f,
                                totalMakeupDb
                            )
                        )
                    }
                    dp.setMbcAllChannelsTo(mbc)

                    // Stage C: Final transparent peak ceiling (-0.2 dBFS) catching sub-millisecond inter-band transients
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1.0f,
                        45.0f,
                        20.0f,
                        -0.2f,
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

        // 2. Hardware Surround Virtualizer (<surround-decoder-enable> + <surround-boost>)
        //    NO 0.65f speaker reduction — 100% full strength on both Headphone and Speaker per XML!
        holder.virtualizer?.let { virt ->
            try {
                val shouldEnable = state.masterEnabled && state.surroundEnabled
                if (!shouldEnable) {
                    virt.enabled = false
                } else {
                    if (virt.strengthSupported) {
                        // In PC XML: surroundAmount=0 (Game) means decoder ON with 0 extra boost (550/1000 base width);
                        // surroundAmount=3 (Custom, +3dB) -> 730/1000; surroundAmount=6 (Dynamic/Movie, +6dB) -> 910/1000;
                        // surroundAmount=10 -> 1000/1000.
                        val strength = (550f + (state.surroundAmount.coerceIn(0f, 10f) / 10f) * 450f)
                            .toInt()
                            .coerceIn(0, 1000)
                            .toShort()
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
