package com.yoga.dolbyatmos.data

import android.content.Context
import android.content.SharedPreferences
import com.yoga.dolbyatmos.model.ActiveEndpoint
import com.yoga.dolbyatmos.model.DolbyProfile
import com.yoga.dolbyatmos.model.DolbyUiState
import com.yoga.dolbyatmos.model.EndpointOverride
import com.yoga.dolbyatmos.model.IeqPreset
import com.yoga.dolbyatmos.model.YogaDolbyTuningData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DolbyRepository {

    // New v2 namespace ensures 100% verbatim PC XML factory defaults without stale v1.0.0 cache
    private const val PREFS_NAME = "yoga_dolby_atmos_prefs_v2_raw_pc"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(DolbyUiState())
    val state: StateFlow<DolbyUiState> = _state.asStateFlow()

    // Active media player audio session IDs reported by Android / players
    private val _activeSessions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeSessions: StateFlow<Map<Int, String>> = _activeSessions.asStateFlow()

    private fun keyPrefix(endpoint: ActiveEndpoint, profile: DolbyProfile): String {
        return "${endpoint.name}_${profile.id}"
    }

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val profile = DolbyProfile.fromId(sp.getString("profile", DolbyProfile.DYNAMIC.id) ?: DolbyProfile.DYNAMIC.id)
        val override = EndpointOverride.fromId(sp.getString("endpoint_override", EndpointOverride.AUTO.id) ?: EndpointOverride.AUTO.id)
        val initialEndpoint = when (override) {
            EndpointOverride.FORCE_HEADPHONE -> ActiveEndpoint.HEADPHONE
            EndpointOverride.FORCE_SPEAKER -> ActiveEndpoint.SPEAKER
            EndpointOverride.AUTO -> ActiveEndpoint.SPEAKER
        }

        val flat10 = List(10) { 0f }
        val slot0 = loadBands(sp, "slot_0", flat10)
        val slot1 = loadBands(sp, "slot_1", flat10)
        val slot2 = loadBands(sp, "slot_2", flat10)

        val baseState = DolbyUiState(
            masterEnabled = sp.getBoolean("master_enabled", true),
            excludeFromRecents = sp.getBoolean("exclude_from_recents", false),
            globalMixFallback = sp.getBoolean("global_mix_fallback", true),
            endpointOverride = override,
            currentProfile = profile,
            customSlotIndex = sp.getInt("custom_slot_idx", 0).coerceIn(0, 2),
            customEqEnabledInPreset = sp.getBoolean("custom_eq_in_preset", false),
            customSlots = listOf(slot0, slot1, slot2)
        )
        _state.value = applyResolvedParams(baseState, initialEndpoint, profile, sp)
    }

    private fun applyResolvedParams(
        base: DolbyUiState,
        endpoint: ActiveEndpoint,
        profile: DolbyProfile,
        sp: SharedPreferences?
    ): DolbyUiState {
        val spec = YogaDolbyTuningData.getFactorySpec(endpoint, profile)
        val kp = keyPrefix(endpoint, profile)
        return base.copy(
            currentProfile = profile,
            ieqPreset = sp?.getString("ieq_$kp", null)?.let { IeqPreset.fromId(it) } ?: spec.ieqPreset,
            ieqAmount = sp?.getFloat("ieq_amt_$kp", spec.ieqAmount) ?: spec.ieqAmount,
            surroundEnabled = sp?.getBoolean("surr_en_$kp", spec.surroundEnabled) ?: spec.surroundEnabled,
            surroundAmount = sp?.getFloat("surr_amt_$kp", spec.surroundAmount) ?: spec.surroundAmount,
            dialogEnhancerEnabled = sp?.getBoolean("de_en_$kp", spec.dialogEnabled) ?: spec.dialogEnabled,
            dialogEnhancerAmount = sp?.getFloat("de_amt_$kp", spec.dialogAmount) ?: spec.dialogAmount,
            volumeLevelerEnabled = sp?.getBoolean("vl_en_$kp", spec.volumeLevelerEnabled) ?: spec.volumeLevelerEnabled,
            volumeLevelerAmount = sp?.getFloat("vl_amt_$kp", spec.volumeLevelerAmount) ?: spec.volumeLevelerAmount,
            bassEnhancerEnabled = sp?.getBoolean("bass_en_$kp", spec.bassEnabled) ?: spec.bassEnabled,
            bassEnhancerAmount = sp?.getFloat("bass_amt_$kp", spec.bassAmount) ?: spec.bassAmount
        )
    }

    fun registerAudioSession(sessionId: Int, packageName: String) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            val updated = current.toMutableMap()
            updated[sessionId] = packageName.ifBlank { "播放器" }
            updated
        }
        _state.update { it.copy(activeSessionCount = _activeSessions.value.size) }
    }

    fun unregisterAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            val updated = current.toMutableMap()
            updated.remove(sessionId)
            updated
        }
        _state.update { it.copy(activeSessionCount = _activeSessions.value.size) }
    }

    fun updateRuntimeHardwareStatus(
        detectedHeadphone: Boolean,
        deviceName: String,
        globalMixAttached: Boolean,
        sessionCount: Int,
        sessionSummary: String
    ) {
        _state.update { prev ->
            val prevEndpoint = prev.effectiveEndpoint
            val updatedHw = prev.copy(
                detectedHeadphone = detectedHeadphone,
                connectedDeviceName = deviceName,
                globalMixAttached = globalMixAttached,
                activeSessionCount = sessionCount,
                attachedSessionSummary = sessionSummary
            )
            val newEndpoint = updatedHw.effectiveEndpoint
            if (newEndpoint != prevEndpoint) {
                applyResolvedParams(updatedHw, newEndpoint, updatedHw.currentProfile, prefs)
            } else {
                updatedHw
            }
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("master_enabled", enabled)?.apply()
        _state.update { it.copy(masterEnabled = enabled) }
    }

    fun setExcludeFromRecents(exclude: Boolean) {
        prefs?.edit()?.putBoolean("exclude_from_recents", exclude)?.apply()
        _state.update { it.copy(excludeFromRecents = exclude) }
    }

    fun hasAskedNotificationPermission(): Boolean {
        return prefs?.getBoolean("asked_notification_perm", false) ?: false
    }

    fun markNotificationPermissionAsked() {
        prefs?.edit()?.putBoolean("asked_notification_perm", true)?.apply()
    }

    fun setGlobalMixFallback(enabled: Boolean) {
        prefs?.edit()?.putBoolean("global_mix_fallback", enabled)?.apply()
        _state.update { it.copy(globalMixFallback = enabled) }
    }

    fun setEndpointOverride(override: EndpointOverride) {
        prefs?.edit()?.putString("endpoint_override", override.id)?.apply()
        _state.update { prev ->
            val next = prev.copy(endpointOverride = override)
            applyResolvedParams(next, next.effectiveEndpoint, next.currentProfile, prefs)
        }
    }

    fun selectProfile(profile: DolbyProfile) {
        prefs?.edit()?.putString("profile", profile.id)?.apply()
        _state.update { prev ->
            applyResolvedParams(prev, prev.effectiveEndpoint, profile, prefs)
        }
    }

    fun resetCurrentProfileToFactory() {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()?.apply {
            remove("ieq_$kp")
            remove("ieq_amt_$kp")
            remove("surr_en_$kp")
            remove("surr_amt_$kp")
            remove("de_en_$kp")
            remove("de_amt_$kp")
            remove("vl_en_$kp")
            remove("vl_amt_$kp")
            remove("bass_en_$kp")
            remove("bass_amt_$kp")
        }?.apply()
        selectProfile(current.currentProfile)
    }

    fun setIeq(preset: IeqPreset, amount: Float = _state.value.ieqAmount) {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()
            ?.putString("ieq_$kp", preset.id)
            ?.putFloat("ieq_amt_$kp", amount)
            ?.apply()
        _state.update { it.copy(ieqPreset = preset, ieqAmount = amount) }
    }

    fun setSurround(enabled: Boolean, amount: Float = _state.value.surroundAmount) {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()
            ?.putBoolean("surr_en_$kp", enabled)
            ?.putFloat("surr_amt_$kp", amount)
            ?.apply()
        _state.update { it.copy(surroundEnabled = enabled, surroundAmount = amount) }
    }

    fun setDialogEnhancer(enabled: Boolean, amount: Float = _state.value.dialogEnhancerAmount) {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()
            ?.putBoolean("de_en_$kp", enabled)
            ?.putFloat("de_amt_$kp", amount)
            ?.apply()
        _state.update { it.copy(dialogEnhancerEnabled = enabled, dialogEnhancerAmount = amount) }
    }

    fun setVolumeLeveler(enabled: Boolean, amount: Float = _state.value.volumeLevelerAmount) {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()
            ?.putBoolean("vl_en_$kp", enabled)
            ?.putFloat("vl_amt_$kp", amount)
            ?.apply()
        _state.update { it.copy(volumeLevelerEnabled = enabled, volumeLevelerAmount = amount) }
    }

    fun setBassEnhancer(enabled: Boolean, amount: Float = _state.value.bassEnhancerAmount) {
        val current = _state.value
        val kp = keyPrefix(current.effectiveEndpoint, current.currentProfile)
        prefs?.edit()
            ?.putBoolean("bass_en_$kp", enabled)
            ?.putFloat("bass_amt_$kp", amount)
            ?.apply()
        _state.update { it.copy(bassEnhancerEnabled = enabled, bassEnhancerAmount = amount) }
    }

    fun selectCustomSlot(index: Int) {
        val idx = index.coerceIn(0, 2)
        prefs?.edit()?.putInt("custom_slot_idx", idx)?.apply()
        _state.update { it.copy(customSlotIndex = idx) }
    }

    fun setCustomEqEnabledInPreset(enabled: Boolean) {
        prefs?.edit()?.putBoolean("custom_eq_in_preset", enabled)?.apply()
        _state.update { it.copy(customEqEnabledInPreset = enabled) }
    }

    fun updateCustomBand(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0..9) return
        val clamped = gainDb.coerceIn(-12f, 12f)
        _state.update { current ->
            val slotIdx = current.customSlotIndex
            val newSlots = current.customSlots.mapIndexed { idx, list ->
                if (idx == slotIdx) {
                    list.toMutableList().also { it[bandIndex] = clamped }
                } else {
                    list
                }
            }
            saveBands(prefs, "slot_$slotIdx", newSlots[slotIdx])
            current.copy(customSlots = newSlots)
        }
    }

    fun flattenCurrentCustomSlot() {
        val flat = List(10) { 0f }
        _state.update { current ->
            val slotIdx = current.customSlotIndex
            val newSlots = current.customSlots.mapIndexed { idx, list ->
                if (idx == slotIdx) flat else list
            }
            saveBands(prefs, "slot_$slotIdx", flat)
            current.copy(customSlots = newSlots)
        }
    }

    private fun loadBands(sp: SharedPreferences, key: String, fallback: List<Float>): List<Float> {
        val raw = sp.getString(key, null) ?: return fallback
        val parsed = raw.split(",").mapNotNull { it.toFloatOrNull() }
        return if (parsed.size == 10) parsed else fallback
    }

    private fun saveBands(sp: SharedPreferences?, key: String, bands: List<Float>) {
        sp?.edit()?.putString(key, bands.joinToString(","))?.apply()
    }
}
