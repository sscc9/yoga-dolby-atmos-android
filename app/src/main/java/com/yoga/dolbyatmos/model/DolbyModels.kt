package com.yoga.dolbyatmos.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Factory DAX3 20-band tuning constants extracted directly from:
 * - Lenovo YOGA Pro 14s IRP8D (83BU): DEV_0287_SUBSYS_17AA38BF_PCI_SUBSYS_380217AA.xml
 * - Dolby Atmos 3.7 Headphone: Headphone_Default_Generic_Default_Atmos3.7.xml
 *
 * Raw XML values are stored in 1/16 dB units (192 = 12.0 dB).
 */
object YogaDolbyTuningData {

    const val BAND_COUNT = 20

    /** Exact 20 band center frequencies (Hz) at 48kHz from <band_20_freq fs_48000="..."> */
    val BAND_CENTERS_HZ = floatArrayOf(
        47f, 141f, 234f, 328f, 469f,
        656f, 844f, 1031f, 1313f, 1688f,
        2250f, 3000f, 3750f, 4688f, 5813f,
        7125f, 9000f, 11250f, 13875f, 19688f
    )

    /** Upper cutoff frequencies (Hz) for Android DynamicsProcessing EqBand / MbcBand */
    val BAND_CUTOFFS_HZ: FloatArray = FloatArray(BAND_COUNT) { i ->
        if (i < BAND_COUNT - 1) {
            sqrt(BAND_CENTERS_HZ[i] * BAND_CENTERS_HZ[i + 1])
        } else {
            20000f
        }
    }

    /** 10-band standard Dolby Access UI Graphic Equalizer center frequencies (Hz) */
    val UI_GEQ_FREQS_HZ = floatArrayOf(
        32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    )

    val UI_GEQ_LABELS = listOf(
        "32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"
    )

    // Raw 1/16 dB IEQ target spectral envelopes from laptop XML
    private val RAW_IEQ_DETAILED = intArrayOf(
        150, 142, 188, 216, 189, 195, 202, 199, 210, 225,
        230, 236, 235, 235, 214, 165, 112, 49, -24, -217
    )

    private val RAW_IEQ_BALANCED = intArrayOf(
        157, 167, 218, 218, 203, 188, 192, 192, 205, 213,
        218, 209, 193, 159, 134, 97, 71, 22, -90, -283
    )

    private val RAW_IEQ_WARM = intArrayOf(
        114, 146, 183, 169, 170, 128, 103, 90, 98, 126,
        127, 140, 96, 85, 80, 66, 38, -32, -132, -275
    )

    /**
     * Neutral pink-like reference envelope (in 1/16 dB) so IEQ target curves convert cleanly
     * into relative tonal shaping adjustments around 0 dB without excessive overall gain shift.
     */
    private val RAW_IEQ_NEUTRAL_REF = intArrayOf(
        135, 152, 190, 195, 185, 175, 175, 175, 180, 188,
        190, 188, 172, 152, 132, 102, 68, 12, -85, -260
    )

    /** Headphone VLLDP <audio-optimizer-bands> air-band treble compensation (1/16 dB) */
    private val RAW_HP_VLLDP_OPT = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 2, 4, 6, 8, 10, 12, 12, 14, 16
    )

    /**
     * YOGA ALC287 Speaker VLLDP <audio-optimizer-bands> clarity & presence curve (1/16 dB),
     * with sub-140Hz rumble gently rolled off to protect phone micro-speakers from distortion.
     */
    private val RAW_SPK_DYNAMIC_OPT = intArrayOf(
        -64, -16, 8, 0, 0, 0, 0, 0, 0, -28,
        -19, 9, 56, 56, 32, 32, 96, 56, 30, 32
    )

    private val RAW_SPK_MEDIA_OPT = intArrayOf(
        -56, 20, 18, -16, -38, -24, -24, -24, -24, -40,
        -42, 3, 56, 56, 32, 32, 96, 56, 30, 32
    )

    /** YOGA Speaker VLLDP <regulator-tuning><threshold_high> excursion protection (1/16 dB) */
    val SPEAKER_REGULATOR_THRESHOLDS_DB: FloatArray = intArrayOf(
        -116, -137, -172, -160, -122, -80, -8, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    ).map { it / 16f - 6f }.toFloatArray()

    fun getIeqDeltaDb(preset: IeqPreset, amount0To10: Float): FloatArray {
        if (preset == IeqPreset.OFF || amount0To10 <= 0.01f) {
            return FloatArray(BAND_COUNT) { 0f }
        }
        val rawTarget = when (preset) {
            IeqPreset.DETAILED -> RAW_IEQ_DETAILED
            IeqPreset.BALANCED -> RAW_IEQ_BALANCED
            IeqPreset.WARM -> RAW_IEQ_WARM
            IeqPreset.OFF -> RAW_IEQ_NEUTRAL_REF
        }
        val scale = (amount0To10 / 10f).coerceIn(0f, 1.2f)
        return FloatArray(BAND_COUNT) { i ->
            val rawDiffDb = (rawTarget[i] - RAW_IEQ_NEUTRAL_REF[i]) / 16f
            (rawDiffDb * scale).coerceIn(-6.5f, 6.5f)
        }
    }

    fun getEndpointOptimizerDb(endpoint: ActiveEndpoint, profile: DolbyProfile): FloatArray {
        return when (endpoint) {
            ActiveEndpoint.HEADPHONE -> FloatArray(BAND_COUNT) { i ->
                RAW_HP_VLLDP_OPT[i] / 16f
            }
            ActiveEndpoint.SPEAKER -> {
                val src = when (profile) {
                    DolbyProfile.MOVIE, DolbyProfile.MUSIC, DolbyProfile.GAME -> RAW_SPK_MEDIA_OPT
                    else -> RAW_SPK_DYNAMIC_OPT
                }
                // Scale Lenovo speaker clarity curve by 0.72x for balanced phone dual-speaker playback
                FloatArray(BAND_COUNT) { i -> (src[i] / 16f) * 0.72f }
            }
        }
    }

    /** Interpolates 10-band user Graphic EQ (32Hz..16kHz) smoothly onto the 20 hardware bands */
    fun map10BandTo20Band(geq10Db: List<Float>): FloatArray {
        if (geq10Db.size != 10) return FloatArray(BAND_COUNT) { 0f }
        val logUi = FloatArray(10) { ln(UI_GEQ_FREQS_HZ[it]) }
        return FloatArray(BAND_COUNT) { idx ->
            val f = BAND_CENTERS_HZ[idx]
            val lf = ln(f)
            when {
                lf <= logUi.first() -> geq10Db.first()
                lf >= logUi.last() -> geq10Db.last()
                else -> {
                    var k = 0
                    while (k < logUi.size - 2 && lf > logUi[k + 1]) {
                        k++
                    }
                    val t = ((lf - logUi[k]) / (logUi[k + 1] - logUi[k])).coerceIn(0f, 1f)
                    geq10Db[k] * (1f - t) + geq10Db[k + 1] * t
                }
            }
        }
    }

    /** Computes the complete 20-band composite EQ curve (dB) for both DSP and UI visualizer */
    fun computeComposite20BandDb(state: DolbyUiState, activeEndpoint: ActiveEndpoint): FloatArray {
        if (!state.masterEnabled) return FloatArray(BAND_COUNT) { 0f }

        val baseOpt = getEndpointOptimizerDb(activeEndpoint, state.currentProfile)
        val ieqDelta = getIeqDeltaDb(state.ieqPreset, state.ieqAmount)
        val userGeq20 = if (state.currentProfile == DolbyProfile.CUSTOM || state.customEqEnabledInPreset) {
            map10BandTo20Band(state.activeCustomBands)
        } else {
            FloatArray(BAND_COUNT) { 0f }
        }

        val result = FloatArray(BAND_COUNT)
        for (i in 0 until BAND_COUNT) {
            val freq = BAND_CENTERS_HZ[i]
            var gain = baseOpt[i] + ieqDelta[i] + userGeq20[i]

            // Profile acoustic signature from DAX3 tuning-cp
            when (state.currentProfile) {
                DolbyProfile.MOVIE -> {
                    // Sub-bass theater impact + spacious upper-detail lift
                    if (activeEndpoint == ActiveEndpoint.HEADPHONE && freq <= 160f) {
                        gain += 2.6f * exp(-((freq - 60f) / 95f).pow(2))
                    }
                    if (freq in 2500f..12000f) {
                        gain += 1.4f
                    }
                }
                DolbyProfile.GAME -> {
                    // Footstep & positional cue emphasis (1.2kHz - 4.8kHz) + controlled sub-bass
                    if (freq in 1200f..5000f) {
                        gain += 2.4f * exp(-((ln(freq) - ln(2600f)) / 0.65f).pow(2))
                    }
                    if (activeEndpoint == ActiveEndpoint.HEADPHONE && freq <= 150f) {
                        gain += 1.5f
                    }
                }
                DolbyProfile.VOICE -> {
                    // Clean low-mud cut (<220Hz) + vocal intelligibility focus (800Hz - 3.8kHz)
                    if (freq < 220f) gain -= 3.2f
                    if (freq in 800f..4000f) gain += 2.2f
                }
                DolbyProfile.DYNAMIC, DolbyProfile.MUSIC, DolbyProfile.CUSTOM -> Unit
            }

            // Dialog Enhancer (<dialog-enhancer-amount> 0..10 -> 800Hz..3800Hz vocal band presence)
            if (state.dialogEnhancerEnabled && state.dialogEnhancerAmount > 0f) {
                val vocalBell = exp(-((ln(freq) - ln(1850f)) / 0.68f).pow(2))
                val mudTrim = if (freq in 180f..420f) -0.12f * state.dialogEnhancerAmount else 0f
                gain += (state.dialogEnhancerAmount / 10f) * 3.8f * vocalBell + mudTrim
            }

            // Bass Enhancer (<bass-enhancer-boost value="144" cutoff="100">)
            if (state.bassEnhancerEnabled && state.bassEnhancerAmount > 0f) {
                if (activeEndpoint == ActiveEndpoint.HEADPHONE) {
                    // 100Hz resonant shelf modeled from Headphone XML
                    val shelf = 1f / (1f + (freq / 115f).pow(2.4f))
                    gain += (state.bassEnhancerAmount / 10f) * 6.5f * shelf
                } else {
                    // Psychoacoustic upper-bass harmonic punch (130Hz - 260Hz) safe for phone speakers
                    val punch = exp(-((ln(freq) - ln(175f)) / 0.42f).pow(2))
                    gain += (state.bassEnhancerAmount / 10f) * 3.5f * punch
                }
            }

            // Subtle stereo presence air compensation when Surround Virtualizer is active
            if (state.surroundEnabled && state.surroundAmount > 0f && freq in 1800f..9500f) {
                gain += (state.surroundAmount / 10f) * 1.1f
            }

            result[i] = min(12f, max(-12f, gain))
        }
        return result
    }
}

enum class DolbyProfile(
    val id: String,
    val titleCn: String,
    val subtitleCn: String,
    val defaultIeq: IeqPreset,
    val defaultIeqAmount: Float,
    val defaultDialogEnabled: Boolean,
    val defaultDialogAmount: Float,
    val defaultSurroundEnabled: Boolean,
    val defaultSurroundAmount: Float,
    val defaultVolumeLeveler: Boolean,
    val defaultVolumeLevelerAmount: Float,
    val defaultBassEnabled: Boolean,
    val defaultBassAmount: Float
) {
    DYNAMIC(
        id = "dynamic",
        titleCn = "动态",
        subtitleCn = "根据播放内容智能平衡低频冲击与对白清晰度（YOGA 默认推荐）",
        defaultIeq = IeqPreset.BALANCED,
        defaultIeqAmount = 8f,
        defaultDialogEnabled = true,
        defaultDialogAmount = 5f,
        defaultSurroundEnabled = true,
        defaultSurroundAmount = 6f,
        defaultVolumeLeveler = true,
        defaultVolumeLevelerAmount = 4f,
        defaultBassEnabled = true,
        defaultBassAmount = 4f
    ),
    MOVIE(
        id = "movie",
        titleCn = "电影",
        subtitleCn = "宽阔环绕声场、深沉影院低频下潜，智能提亮人声对白",
        defaultIeq = IeqPreset.BALANCED,
        defaultIeqAmount = 6f,
        defaultDialogEnabled = true,
        defaultDialogAmount = 6f,
        defaultSurroundEnabled = true,
        defaultSurroundAmount = 8.5f,
        defaultVolumeLeveler = true,
        defaultVolumeLevelerAmount = 5f,
        defaultBassEnabled = true,
        defaultBassAmount = 6f
    ),
    MUSIC(
        id = "music",
        titleCn = "音乐",
        subtitleCn = "还原纯净三频乐器分离度与人声结像，支持均衡/温暖/细腻调音",
        defaultIeq = IeqPreset.BALANCED,
        defaultIeqAmount = 8f,
        defaultDialogEnabled = false,
        defaultDialogAmount = 3f,
        defaultSurroundEnabled = false,
        defaultSurroundAmount = 3f,
        defaultVolumeLeveler = false,
        defaultVolumeLevelerAmount = 2f,
        defaultBassEnabled = true,
        defaultBassAmount = 3.5f
    ),
    GAME(
        id = "game",
        titleCn = "游戏",
        subtitleCn = "拓宽 3D 听声辨位声场，强化脚步声与环境细节瞬态",
        defaultIeq = IeqPreset.DETAILED,
        defaultIeqAmount = 7f,
        defaultDialogEnabled = false,
        defaultDialogAmount = 4f,
        defaultSurroundEnabled = true,
        defaultSurroundAmount = 8f,
        defaultVolumeLeveler = true,
        defaultVolumeLevelerAmount = 3f,
        defaultBassEnabled = true,
        defaultBassAmount = 4f
    ),
    VOICE(
        id = "voice",
        titleCn = "语音 / 网课",
        subtitleCn = "源自联想 YOGA 专调网课与播客模式，滤除低频嗡鸣、聚焦人声",
        defaultIeq = IeqPreset.OFF,
        defaultIeqAmount = 0f,
        defaultDialogEnabled = true,
        defaultDialogAmount = 9.5f,
        defaultSurroundEnabled = false,
        defaultSurroundAmount = 0f,
        defaultVolumeLeveler = true,
        defaultVolumeLevelerAmount = 7f,
        defaultBassEnabled = false,
        defaultBassAmount = 0f
    ),
    CUSTOM(
        id = "custom",
        titleCn = "自定义",
        subtitleCn = "10 段手动图形均衡器（±12dB）与四大杜比音效模块自由组合",
        defaultIeq = IeqPreset.OFF,
        defaultIeqAmount = 0f,
        defaultDialogEnabled = true,
        defaultDialogAmount = 5f,
        defaultSurroundEnabled = true,
        defaultSurroundAmount = 5f,
        defaultVolumeLeveler = true,
        defaultVolumeLevelerAmount = 3f,
        defaultBassEnabled = true,
        defaultBassAmount = 4f
    );

    companion object {
        fun fromId(id: String): DolbyProfile = entries.firstOrNull { it.id == id } ?: DYNAMIC
    }
}

enum class IeqPreset(val id: String, val labelCn: String, val descCn: String) {
    OFF("off", "关闭", "不施加智能目标曲线"),
    BALANCED("balanced", "均衡", "三频自然均衡（原厂默认）"),
    WARM("warm", "温暖", "饱满中低频与柔和耐听高频"),
    DETAILED("detailed", "细腻", "增强泛音空气感与乐器解析力");

    companion object {
        fun fromId(id: String): IeqPreset = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

enum class EndpointOverride(val id: String, val labelCn: String) {
    AUTO("auto", "自动识别"),
    FORCE_HEADPHONE("headphone", "强制耳机曲线"),
    FORCE_SPEAKER("speaker", "强制扬声器曲线");

    companion object {
        fun fromId(id: String): EndpointOverride = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class ActiveEndpoint(val labelCn: String) {
    HEADPHONE("耳机模式"),
    SPEAKER("扬声器模式")
}

data class DolbyUiState(
    val masterEnabled: Boolean = true,
    val globalMixFallback: Boolean = true,
    val endpointOverride: EndpointOverride = EndpointOverride.AUTO,
    val detectedHeadphone: Boolean = false,
    val connectedDeviceName: String = "手机内置扬声器",
    val activeSessionCount: Int = 0,
    val globalMixAttached: Boolean = false,
    val currentProfile: DolbyProfile = DolbyProfile.DYNAMIC,
    val ieqPreset: IeqPreset = DolbyProfile.DYNAMIC.defaultIeq,
    val ieqAmount: Float = DolbyProfile.DYNAMIC.defaultIeqAmount,
    val surroundEnabled: Boolean = DolbyProfile.DYNAMIC.defaultSurroundEnabled,
    val surroundAmount: Float = DolbyProfile.DYNAMIC.defaultSurroundAmount,
    val dialogEnhancerEnabled: Boolean = DolbyProfile.DYNAMIC.defaultDialogEnabled,
    val dialogEnhancerAmount: Float = DolbyProfile.DYNAMIC.defaultDialogAmount,
    val volumeLevelerEnabled: Boolean = DolbyProfile.DYNAMIC.defaultVolumeLeveler,
    val volumeLevelerAmount: Float = DolbyProfile.DYNAMIC.defaultVolumeLevelerAmount,
    val bassEnhancerEnabled: Boolean = DolbyProfile.DYNAMIC.defaultBassEnabled,
    val bassEnhancerAmount: Float = DolbyProfile.DYNAMIC.defaultBassAmount,
    val customSlotIndex: Int = 0,
    val customEqEnabledInPreset: Boolean = false,
    val customSlots: List<List<Float>> = listOf(
        // Slot 1: V-Shape Hi-Fi
        listOf(3.5f, 4.0f, 2.0f, -0.5f, -1.0f, 0.0f, 1.5f, 3.0f, 3.5f, 2.5f),
        // Slot 2: Vocal & Acoustic Clarity
        listOf(-1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f, 3.0f, 1.5f, 1.0f, 0.5f),
        // Slot 3: Deep Cinema Sub-Bass
        listOf(5.5f, 5.0f, 2.5f, 0.5f, 0.0f, 1.0f, 2.0f, 2.5f, 2.0f, 1.5f)
    )
) {
    val effectiveEndpoint: ActiveEndpoint
        get() = when (endpointOverride) {
            EndpointOverride.FORCE_HEADPHONE -> ActiveEndpoint.HEADPHONE
            EndpointOverride.FORCE_SPEAKER -> ActiveEndpoint.SPEAKER
            EndpointOverride.AUTO -> if (detectedHeadphone) ActiveEndpoint.HEADPHONE else ActiveEndpoint.SPEAKER
        }

    val activeCustomBands: List<Float>
        get() = customSlots.getOrElse(customSlotIndex.coerceIn(0, 2)) { List(10) { 0f } }
}
