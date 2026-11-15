package com.yoga.dolbyatmos.data

import android.content.Context
import android.content.SharedPreferences
import com.yoga.dolbyatmos.model.DolbyProfile
import com.yoga.dolbyatmos.model.DolbyUiState
import com.yoga.dolbyatmos.model.EndpointOverride
import com.yoga.dolbyatmos.model.IeqPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DolbyRepository {

    private const val PREFS_NAME = "yoga_dolby_atmos_prefs"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(DolbyUiState())
    val state: StateFlow<DolbyUiState> = _state.asStateFlow()

    // Active media player audio session IDs reported by Android / players
    private val _activeSessions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val activeSessions: StateFlow<Map<Int, String>> = _activeSessions.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val profile = DolbyProfile.fromId(sp.getString("profile", DolbyProfile.DYNAMIC.id) ?: "dynamic")
        val slot0 = loadBands(sp, "slot_0", listOf(3.5f, 4.0f, 2.0f, -0.5f, -1.0f, 0.0f, 1.5f, 3.0f, 3.5f, 2.5f))
        val slot1 = loadBands(sp, "slot_1", listOf(-1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f, 3.0f, 1.5f, 1.0f, 0.5f))
        val slot2 = loadBands(sp, "slot_2", listOf(5.5f, 5.0f, 2.5f, 0.5f, 0.0f, 1.0f, 2.0f, 2.5f, 2.0f, 1.5f))

        _state.value = DolbyUiState(
            masterEnabled = sp.getBoolean("master_enabled", true),
            globalMixFallback = sp.getBoolean("global_mix_fallback", true),
            endpointOverride = EndpointOverride.fromId(sp.getString("endpoint_override", "auto") ?: "auto"),
            currentProfile = profile,
            ieqPreset = IeqPreset.fromId(sp.getString("ieq_${profile.id}", profile.defaultIeq.id) ?: profile.defaultIeq.id),
            ieqAmount = sp.getFloat("ieq_amt_${profile.id}", profile.defaultIeqAmount),
            surroundEnabled = sp.getBoolean("surr_en_${profile.id}", profile.defaultSurroundEnabled),
            surroundAmount = sp.getFloat("surr_amt_${profile.id}", profile.defaultSurroundAmount),
            dialogEnhancerEnabled = sp.getBoolean("de_en_${profile.id}", profile.defaultDialogEnabled),
            dialogEnhancerAmount = sp.getFloat("de_amt_${profile.id}", profile.defaultDialogAmount),
            volumeLevelerEnabled = sp.getBoolean("vl_en_${profile.id}", profile.defaultVolumeLeveler),
            volumeLevelerAmount = sp.getFloat("vl_amt_${profile.id}", profile.defaultVolumeLevelerAmount),
            bassEnhancerEnabled = sp.getBoolean("bass_en_${profile.id}", profile.defaultBassEnabled),
            bassEnhancerAmount = sp.getFloat("bass_amt_${profile.id}", profile.defaultBassAmount),
            customSlotIndex = sp.getInt("custom_slot_idx", 0).coerceIn(0, 2),
            customEqEnabledInPreset = sp.getBoolean("custom_eq_in_preset", false),
            customSlots = listOf(slot0, slot1, slot2)
        )
    }

    fun registerAudioSession(sessionId: Int, packageName: String) {
        if (sessionId <= 0) return
        _activeSessions.update { current ->
            val updated = current.toMutableMap()
            updated[sessionId] = packageName.ifBlank { "media_player" }
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
        sessionCount: Int
    ) {
        _state.update {
            it.copy(
                detectedHeadphone = detectedHeadphone,
                connectedDeviceName = deviceName,
                globalMixAttached = globalMixAttached,
                activeSessionCount = sessionCount
            )
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean("master_enabled", enabled)?.apply()
        _state.update { it.copy(masterEnabled = enabled) }
    }

    fun setGlobalMixFallback(enabled: Boolean) {
        prefs?.edit()?.putBoolean("global_mix_fallback", enabled)?.apply()
        _state.update { it.copy(globalMixFallback = enabled) }
    }

    fun setEndpointOverride(override: EndpointOverride) {
        prefs?.edit()?.putString("endpoint_override", override.id)?.apply()
        _state.update { it.copy(endpointOverride = override) }
    }

    fun selectProfile(profile: DolbyProfile) {
        val sp = prefs
        val ieq = sp?.getString("ieq_${profile.id}", null)?.let { IeqPreset.fromId(it) } ?: profile.defaultIeq
        val ieqAmt = sp?.getFloat("ieq_amt_${profile.id}", profile.defaultIeqAmount) ?: profile.defaultIeqAmount
        val surrEn = sp?.getBoolean("surr_en_${profile.id}", profile.defaultSurroundEnabled) ?: profile.defaultSurroundEnabled
        val surrAmt = sp?.getFloat("surr_amt_${profile.id}", profile.defaultSurroundAmount) ?: profile.defaultSurroundAmount
        val deEn = sp?.getBoolean("de_en_${profile.id}", profile.defaultDialogEnabled) ?: profile.defaultDialogEnabled
        val deAmt = sp?.getFloat("de_amt_${profile.id}", profile.defaultDialogAmount) ?: profile.defaultDialogAmount
        val vlEn = sp?.getBoolean("vl_en_${profile.id}", profile.defaultVolumeLeveler) ?: profile.defaultVolumeLeveler
        val vlAmt = sp?.getFloat("vl_amt_${profile.id}", profile.defaultVolumeLevelerAmount) ?: profile.defaultVolumeLevelerAmount
        val bassEn = sp?.getBoolean("bass_en_${profile.id}", profile.defaultBassEnabled) ?: profile.defaultBassEnabled
        val bassAmt = sp?.getFloat("bass_amt_${profile.id}", profile.defaultBassAmount) ?: profile.defaultBassAmount

        sp?.edit()?.putString("profile", profile.id)?.apply()
        _state.update {
            it.copy(
                currentProfile = profile,
                ieqPreset = ieq,
                ieqAmount = ieqAmt,
                surroundEnabled = surrEn,
                surroundAmount = surrAmt,
                dialogEnhancerEnabled = deEn,
                dialogEnhancerAmount = deAmt,
                volumeLevelerEnabled = vlEn,
                volumeLevelerAmount = vlAmt,
                bassEnhancerEnabled = bassEn,
                bassEnhancerAmount = bassAmt
            )
        }
    }

    fun resetCurrentProfileToFactory() {
        val p = _state.value.currentProfile
        prefs?.edit()?.apply {
            remove("ieq_${p.id}")
            remove("ieq_amt_${p.id}")
            remove("surr_en_${p.id}")
            remove("surr_amt_${p.id}")
            remove("de_en_${p.id}")
            remove("de_amt_${p.id}")
            remove("vl_en_${p.id}")
            remove("vl_amt_${p.id}")
            remove("bass_en_${p.id}")
            remove("bass_amt_${p.id}")
        }?.apply()
        selectProfile(p)
    }

    fun setIeq(preset: IeqPreset, amount: Float = _state.value.ieqAmount) {
        val p = _state.value.currentProfile
        prefs?.edit()
            ?.putString("ieq_${p.id}", preset.id)
            ?.putFloat("ieq_amt_${p.id}", amount)
            ?.apply()
        _state.update { it.copy(ieqPreset = preset, ieqAmount = amount) }
    }

    fun setSurround(enabled: Boolean, amount: Float = _state.value.surroundAmount) {
        val p = _state.value.currentProfile
        prefs?.edit()
            ?.putBoolean("surr_en_${p.id}", enabled)
            ?.putFloat("surr_amt_${p.id}", amount)
            ?.apply()
        _state.update { it.copy(surroundEnabled = enabled, surroundAmount = amount) }
    }

    fun setDialogEnhancer(enabled: Boolean, amount: Float = _state.value.dialogEnhancerAmount) {
        val p = _state.value.currentProfile
        prefs?.edit()
            ?.putBoolean("de_en_${p.id}", enabled)
            ?.putFloat("de_amt_${p.id}", amount)
            ?.apply()
        _state.update { it.copy(dialogEnhancerEnabled = enabled, dialogEnhancerAmount = amount) }
    }

    fun setVolumeLeveler(enabled: Boolean, amount: Float = _state.value.volumeLevelerAmount) {
        val p = _state.value.currentProfile
        prefs?.edit()
            ?.putBoolean("vl_en_${p.id}", enabled)
            ?.putFloat("vl_amt_${p.id}", amount)
            ?.apply()
        _state.update { it.copy(volumeLevelerEnabled = enabled, volumeLevelerAmount = amount) }
    }

    fun setBassEnhancer(enabled: Boolean, amount: Float = _state.value.bassEnhancerAmount) {
        val p = _state.value.currentProfile
        prefs?.edit()
            ?.putBoolean("bass_en_${p.id}", enabled)
            ?.putFloat("bass_amt_${p.id}", amount)
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
