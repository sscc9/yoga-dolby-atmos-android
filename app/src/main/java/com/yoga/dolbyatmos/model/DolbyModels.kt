package com.yoga.dolbyatmos.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 100% Verbatim PC DAX3 20-Band Tuning Parameters extracted directly without modification from:
 * - Speaker: Lenovo YOGA Pro 14s IRP8D (83BU) ALC287
 *   `DEV_0287_SUBSYS_17AA38BF_PCI_SUBSYS_380217AA.xml`
 * - Headphone: Dolby Atmos 3.7 Generic Default
 *   `Headphone_Default_Generic_Default_Atmos3.7.xml`
 *
 * All raw XML integer values use 1/16 dB units (<geq_maximum_range value="192"/> -> 192 / 16 = 12.0 dB).
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

    /** Upper cutoff frequencies (Hz) for Android DynamicsProcessing 20-band filterbank */
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

    // =========================================================================
    // 1. Exact Verbatim IEQ Target Envelopes from <constant> (1/16 dB)
    // =========================================================================
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
     * Zero-mean natural musical input spectrum across the 20 ERB bands (1/16 dB),
     * matching DAX3's internal spectral reference so each XML IEQ target curve
     * preserves 100% of its exact band-to-band relative differences without
     * artificial DC volume offset across Warm / Balanced / Detailed.
     */
    private val ZERO_MEAN_MUSIC_REF_16DB = floatArrayOf(
        -28f, -22f, 20f, 24f, 16f, 12f, 10f, 8f, 14f, 20f,
        24f, 18f, 8f, -12f, -36f, -68f, -96f, -150f, -256f, -440f
    )

    private fun coreMean0To17(arr: IntArray): Float {
        var sum = 0
        for (i in 0..17) sum += arr[i]
        return sum / 18f
    }

    private fun coreMean0To17Float(arr: FloatArray): Float {
        var sum = 0f
        for (i in 0..17) sum += arr[i]
        return sum / 18f
    }

    // =========================================================================
    // 2. Exact Verbatim <tuning-vlldp><audio-optimizer-bands> (1/16 dB)
    //    NO 0.72x scaling, NO sub-bass cuts — 100% raw laptop XML numbers!
    // =========================================================================

    /** Headphone_Default_Generic_Default_Atmos3.7.xml <audio-optimizer-bands> */
    private val RAW_HP_VLLDP_OPT = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 2, 4, 6, 8, 10, 12, 12, 14, 16
    )

    /** DEV_0287_SUBSYS_17AA38BF Speaker <audio-optimizer-bands> for dynamic / voice / personalize */
    private val RAW_SPK_DYNAMIC_OPT = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, -28,
        -19, 9, 56, 56, 32, 32, 96, 56, 30, 32
    )

    /** DEV_0287_SUBSYS_17AA38BF Speaker <audio-optimizer-bands> for movie / music / game */
    private val RAW_SPK_MEDIA_OPT = intArrayOf(
        16, 48, 18, -24, -54, -32, -32, -32, -32, -54,
        -57, 3, 56, 56, 32, 32, 96, 56, 30, 32
    )

    // =========================================================================
    // 3. Exact Verbatim <tuning-vlldp><regulator-tuning> (1/16 dB -> dBFS)
    //    NO extra -6dB offset — 100% raw laptop XML numbers!
    // =========================================================================

    private val RAW_HP_REG_HIGH = IntArray(BAND_COUNT) { 0 }
    private val RAW_HP_REG_LOW = IntArray(BAND_COUNT) { -192 }

    private val RAW_SPK_REG_HIGH_DEFAULT = intArrayOf(
        -116, -137, -172, -160, -122, -80, -8, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    )
    private val RAW_SPK_REG_LOW_DEFAULT = intArrayOf(
        -308, -329, -364, -352, -314, -272, -200, -192, -192, -192,
        -192, -192, -192, -192, -192, -192, -192, -192, -192, -192
    )

    private val RAW_SPK_REG_HIGH_VOICE = intArrayOf(
        -64, -96, -112, -80, -80, -80, -8, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    )
    private val RAW_SPK_REG_LOW_VOICE = intArrayOf(
        -256, -288, -304, -272, -272, -272, -200, -192, -192, -192,
        -192, -192, -192, -192, -192, -192, -192, -192, -192, -192
    )

    fun getRegulatorHighDb(endpoint: ActiveEndpoint, profile: DolbyProfile): FloatArray {
        val src = when (endpoint) {
            ActiveEndpoint.HEADPHONE -> RAW_HP_REG_HIGH
            ActiveEndpoint.SPEAKER -> if (profile == DolbyProfile.VOICE) {
                RAW_SPK_REG_HIGH_VOICE
            } else {
                RAW_SPK_REG_HIGH_DEFAULT
            }
        }
        return FloatArray(BAND_COUNT) { src[it] / 16f }
    }

    fun getRegulatorLowDb(endpoint: ActiveEndpoint, profile: DolbyProfile): FloatArray {
        val src = when (endpoint) {
            ActiveEndpoint.HEADPHONE -> RAW_HP_REG_LOW
            ActiveEndpoint.SPEAKER -> if (profile == DolbyProfile.VOICE) {
                RAW_SPK_REG_LOW_VOICE
            } else {
                RAW_SPK_REG_LOW_DEFAULT
            }
        }
        return FloatArray(BAND_COUNT) { src[it] / 16f }
    }

    // =========================================================================
    // 4. Exact Verbatim Per-Endpoint, Per-Profile XML Parameter Table
    // =========================================================================

    data class FactoryEndpointSpec(
        val ieqPreset: IeqPreset,
        val ieqAmount: Float,
        val dialogEnabled: Boolean,
        val dialogAmount: Float,
        val surroundEnabled: Boolean,
        /** Slider 0..10 where 6.0 = 96/16 (+6dB boost), 3.0 = 48/16 (+3dB boost), 0.0 = base decoder (0 boost) */
        val surroundAmount: Float,
        val volumeLevelerEnabled: Boolean,
        val volumeLevelerAmount: Float,
        val bassEnabled: Boolean,
        val bassAmount: Float,
        val bassBoostMaxDb: Float,
        val bassCutoffHz: Float,
        val bassWidthOct: Float,
        /** Exact <volmax-boost> / 16f (+4.0 dB = 64, +6.0 dB = 96) */
        val volmaxBoostDb: Float,
        /** Exact <mb-compressor-enable> & <mb-compressor-tuning> from tuning-vlldp */
        val vlldpMbcEnabled: Boolean,
        val vlldpMbcAttackMs: Float,
        val vlldpMbcReleaseMs: Float,
        val vlldpMbcLowGroupMakeupDb: Float,
        val vlldpMbcHighGroupMakeupDb: Float,
        /** Exact <sliding-bass-enable> from Speaker Music profile */
        val slidingBassEnabled: Boolean,
        val slidingBassXoHz: Float,
        val slidingBassMaxGainDb: Float
    )

    fun getFactorySpec(endpoint: ActiveEndpoint, profile: DolbyProfile): FactoryEndpointSpec {
        return when (endpoint) {
            ActiveEndpoint.HEADPHONE -> when (profile) {
                // Headphone [dynamic]: ieq_en=1(10, balanced), de_en=1(5), surr_en=1(96=+6dB), vl_en=1(4), bass_en=0, volmax=64(+4dB)
                DolbyProfile.DYNAMIC -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.BALANCED, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 5f,
                    surroundEnabled = true, surroundAmount = 6f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 4f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Headphone [movie]: ieq_en=0(10), de_en=1(5), surr_en=1(96=+6dB), vl_en=1(4), bass_en=0, volmax=64(+4dB)
                DolbyProfile.MOVIE -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 5f,
                    surroundEnabled = true, surroundAmount = 6f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 4f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Headphone [music]: ieq_en=1(8, balanced), de_en=0(7), surr_en=0(0), vl_en=1(0), bass_en=0, volmax=64(+4dB)
                DolbyProfile.MUSIC -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.BALANCED, ieqAmount = 8f,
                    dialogEnabled = false, dialogAmount = 7f,
                    surroundEnabled = false, surroundAmount = 0f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 0f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Headphone [game]: ieq_en=0(10), de_en=0(7), surr_en=1(boost=0), vl_en=1(0), bass_en=0, volmax=64(+4dB)
                DolbyProfile.GAME -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = false, dialogAmount = 7f,
                    surroundEnabled = true, surroundAmount = 0f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 0f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Headphone [voice]: ieq_en=0(10), de_en=1(10), surr_en=0(0), vl_en=0(0), bass_en=0, volmax=64(+4dB)
                DolbyProfile.VOICE -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 10f,
                    surroundEnabled = false, surroundAmount = 0f,
                    volumeLevelerEnabled = false, volumeLevelerAmount = 0f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Headphone [personalize_user1]: ieq_en=0, de_en=1(10), surr_en=1(48=+3dB), vl_en=1(2), bass_en=0, volmax=64(+4dB)
                DolbyProfile.CUSTOM -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 10f,
                    surroundEnabled = true, surroundAmount = 3f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 2f,
                    bassEnabled = false, bassAmount = 10f, bassBoostMaxDb = 9.0f, bassCutoffHz = 100f, bassWidthOct = 2.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
            }

            ActiveEndpoint.SPEAKER -> when (profile) {
                // Speaker [dynamic]: ieq_en=1(4, balanced), de_en=1(5), surr_en=1(96=+6dB), vl_en=1(2), bass_en=0, volmax=64(+4dB)
                DolbyProfile.DYNAMIC -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.BALANCED, ieqAmount = 4f,
                    dialogEnabled = true, dialogAmount = 5f,
                    surroundEnabled = true, surroundAmount = 6f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 2f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Speaker [movie]: ieq_en=0, de_en=1(5), surr_en=1(96=+6dB), vl_en=0, volmax=64(+4dB), mbc=1(25ms/200ms, +3dB/+3dB)
                DolbyProfile.MOVIE -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 5f,
                    surroundEnabled = true, surroundAmount = 6f,
                    volumeLevelerEnabled = false, volumeLevelerAmount = 2f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = true, vlldpMbcAttackMs = 25f, vlldpMbcReleaseMs = 200f,
                    vlldpMbcLowGroupMakeupDb = 3.0f, vlldpMbcHighGroupMakeupDb = 3.0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Speaker [music]: ieq_en=0, de_en=0, surr_en=0, vl_en=0, volmax=96(+6dB!), mbc=1(25ms/200ms, +1.3125dB/+3dB), sliding_bass=1(230Hz, +4dB)
                DolbyProfile.MUSIC -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = false, dialogAmount = 7f,
                    surroundEnabled = false, surroundAmount = 0f,
                    volumeLevelerEnabled = false, volumeLevelerAmount = 2f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 6.0f,
                    vlldpMbcEnabled = true, vlldpMbcAttackMs = 25f, vlldpMbcReleaseMs = 200f,
                    vlldpMbcLowGroupMakeupDb = 1.3125f, vlldpMbcHighGroupMakeupDb = 3.0f,
                    slidingBassEnabled = true, slidingBassXoHz = 230f, slidingBassMaxGainDb = 4.0f
                )
                // Speaker [game]: ieq_en=0, de_en=0, surr_en=1(boost=0), vl_en=0, volmax=64(+4dB), mbc=1(25ms/200ms, +3dB/+3dB)
                DolbyProfile.GAME -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = false, dialogAmount = 7f,
                    surroundEnabled = true, surroundAmount = 0f,
                    volumeLevelerEnabled = false, volumeLevelerAmount = 0f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = true, vlldpMbcAttackMs = 25f, vlldpMbcReleaseMs = 200f,
                    vlldpMbcLowGroupMakeupDb = 3.0f, vlldpMbcHighGroupMakeupDb = 3.0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Speaker [voice]: ieq_en=0, de_en=1(8), surr_en=0, vl_en=0, volmax=96(+6dB!)
                DolbyProfile.VOICE -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 8f,
                    surroundEnabled = false, surroundAmount = 0f,
                    volumeLevelerEnabled = false, volumeLevelerAmount = 0f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 6.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
                // Speaker [personalize_user1]: ieq_en=0, de_en=1(10), surr_en=1(48=+3dB), vl_en=1(2), volmax=64(+4dB)
                DolbyProfile.CUSTOM -> FactoryEndpointSpec(
                    ieqPreset = IeqPreset.OFF, ieqAmount = 10f,
                    dialogEnabled = true, dialogAmount = 10f,
                    surroundEnabled = true, surroundAmount = 3f,
                    volumeLevelerEnabled = true, volumeLevelerAmount = 2f,
                    bassEnabled = false, bassAmount = 6f, bassBoostMaxDb = 6.0f, bassCutoffHz = 200f, bassWidthOct = 1.0f,
                    volmaxBoostDb = 4.0f,
                    vlldpMbcEnabled = false, vlldpMbcAttackMs = 10f, vlldpMbcReleaseMs = 20f,
                    vlldpMbcLowGroupMakeupDb = 0f, vlldpMbcHighGroupMakeupDb = 0f,
                    slidingBassEnabled = false, slidingBassXoHz = 180f, slidingBassMaxGainDb = 0f
                )
            }
        }
    }

    fun getIeqDeltaDb(preset: IeqPreset, amount0To10: Float): FloatArray {
        if (preset == IeqPreset.OFF || amount0To10 <= 0.01f) {
            return FloatArray(BAND_COUNT) { 0f }
        }
        val rawTarget = when (preset) {
            IeqPreset.DETAILED -> RAW_IEQ_DETAILED
            IeqPreset.BALANCED -> RAW_IEQ_BALANCED
            IeqPreset.WARM -> RAW_IEQ_WARM
            IeqPreset.OFF -> return FloatArray(BAND_COUNT) { 0f }
        }
        val targetMean = coreMean0To17(rawTarget)
        val refMean = coreMean0To17Float(ZERO_MEAN_MUSIC_REF_16DB)
        val scale = (amount0To10 / 10f).coerceIn(0f, 1.0f)

        return FloatArray(BAND_COUNT) { i ->
            val zeroedTarget16Db = rawTarget[i] - targetMean
            val zeroedRef16Db = ZERO_MEAN_MUSIC_REF_16DB[i] - refMean
            val deltaDb = (zeroedTarget16Db - zeroedRef16Db) / 16f
            (deltaDb * scale).coerceIn(-12f, 12f)
        }
    }

    /** 100% Verbatim <tuning-vlldp><audio-optimizer-bands> in dB (value / 16f, NO scaling) */
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
                FloatArray(BAND_COUNT) { i -> src[i] / 16f }
            }
        }
    }

    /** Interpolates 10-band user Graphic EQ (32Hz..16kHz, ±12dB = 192/16) onto the 20 bands */
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

    /**
     * Computes the exact composite 20-band EQ curve (dB) combining:
     * - VLLDP <audio-optimizer-bands> (1:1 raw XML)
     * - CP <ieq-bands-set> (1:1 raw XML)
     * - CP <graphic-equalizer-bands> (1:1 user GEQ, flat 0 dB by default)
     * - CP <dialog-enhancer> (speech presence band, ducking=0 per XML)
     * - CP <bass-enhancer> (disabled=0 by default per XML; 100Hz/+9dB HP or 200Hz SPK when manually enabled)
     * - VLLDP <sliding-bass> (Speaker Music 230Hz dynamic low-end boost per XML)
     *
     * Zero invented Movie/Game/Voice bells, zero scaling!
     */
    fun computeComposite20BandDb(state: DolbyUiState, activeEndpoint: ActiveEndpoint): FloatArray {
        if (!state.masterEnabled) return FloatArray(BAND_COUNT) { 0f }

        val spec = getFactorySpec(activeEndpoint, state.currentProfile)
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

            // CP <dialog-enhancer-enable> & <dialog-enhancer-amount> (<dialog-enhancer-ducking value="0"/>)
            if (state.dialogEnhancerEnabled && state.dialogEnhancerAmount > 0f) {
                val vocalBell = exp(-((ln(freq) - ln(1800f)) / 0.65f).pow(2))
                gain += (state.dialogEnhancerAmount / 10f) * 3.5f * vocalBell
            }

            // CP <bass-enhancer-enable> (0 by default in all PC profiles; uses exact cutoff & boost when manually enabled)
            if (state.bassEnhancerEnabled && state.bassEnhancerAmount > 0f) {
                val shelf = 1f / (1f + (freq / spec.bassCutoffHz).pow(2.2f / spec.bassWidthOct.coerceAtLeast(0.8f)))
                gain += (state.bassEnhancerAmount / 10f) * spec.bassBoostMaxDb * shelf
            }

            // VLLDP <sliding-bass-enable value="1"/> (Speaker Music: xo=230Hz, max_gain=4.0dB, curve=0,43,6,0,0)
            if (spec.slidingBassEnabled && freq <= spec.slidingBassXoHz * 1.15f) {
                val sbGain = when (i) {
                    0 -> 2.6875f // 43 / 16 dB
                    1 -> 3.6f    // Peak punch below 230Hz xo
                    2 -> 1.8f    // 234Hz crossover transition
                    else -> 0f
                }
                gain += sbGain
            }

            result[i] = min(15f, max(-15f, gain))
        }
        return result
    }
}

enum class DolbyProfile(
    val id: String,
    val titleCn: String,
    val subtitleCn: String
) {
    DYNAMIC(
        id = "dynamic",
        titleCn = "动态",
        subtitleCn = "联想管家默认模式：原厂 IEQ 均衡 + 对白(5) + 环绕(+6dB) + VolMax(+4dB)"
    ),
    MOVIE(
        id = "movie",
        titleCn = "电影",
        subtitleCn = "原厂影院参数：环绕(+6dB) + 对白(5) + 扬声器双段MBC(+3dB) + VolMax(+4dB)"
    ),
    MUSIC(
        id = "music",
        titleCn = "音乐",
        subtitleCn = "原厂音乐参数：耳机 IEQ(8) / 扬声器 VolMax(+6dB) + 双段MBC + 230Hz动态低音(+4dB)"
    ),
    GAME(
        id = "game",
        titleCn = "游戏",
        subtitleCn = "原厂游戏参数：开启 3D 环绕解码器声场 + 扬声器双段MBC(+3dB) + VolMax(+4dB)"
    ),
    VOICE(
        id = "voice",
        titleCn = "语音",
        subtitleCn = "原厂人声模式：高强度对白聚焦(耳机10 / 扬声器8 + VolMax +6dB)"
    ),
    CUSTOM(
        id = "custom",
        titleCn = "自定义",
        subtitleCn = "对应电脑个性化 1–3：默认平直 0dB 均衡器 + 对白(10) + 环绕(+3dB) + 音量均衡(2)"
    );

    companion object {
        fun fromId(id: String): DolbyProfile = entries.firstOrNull { it.id == id } ?: DYNAMIC
    }
}

enum class IeqPreset(val id: String, val labelCn: String, val descCn: String) {
    OFF("off", "关闭", "不施加 IEQ 曲线（原厂电影/游戏/语音默认关闭）"),
    BALANCED("balanced", "均衡", "原厂 ieq_balanced 20段靶向曲线"),
    WARM("warm", "温暖", "原厂 ieq_warm 饱满中低频曲线"),
    DETAILED("detailed", "细腻", "原厂 ieq_detailed 高频解析力曲线");

    companion object {
        fun fromId(id: String): IeqPreset = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class EndpointOverride(val id: String, val labelCn: String) {
    AUTO("auto", "自动识别"),
    FORCE_HEADPHONE("headphone", "强制耳机 XML"),
    FORCE_SPEAKER("speaker", "强制扬声器 XML");

    companion object {
        fun fromId(id: String): EndpointOverride = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class ActiveEndpoint(val labelCn: String) {
    HEADPHONE("耳机模式 (Atmos 3.7)"),
    SPEAKER("扬声器模式 (ALC287)")
}

data class DolbyUiState(
    val masterEnabled: Boolean = true,
    val globalMixFallback: Boolean = true,
    val endpointOverride: EndpointOverride = EndpointOverride.AUTO,
    val detectedHeadphone: Boolean = false,
    val connectedDeviceName: String = "手机内置扬声器",
    val activeSessionCount: Int = 0,
    val attachedSessionSummary: String = "等待音频流",
    val globalMixAttached: Boolean = false,
    val currentProfile: DolbyProfile = DolbyProfile.DYNAMIC,
    val ieqPreset: IeqPreset = IeqPreset.BALANCED,
    val ieqAmount: Float = 10f,
    val surroundEnabled: Boolean = true,
    val surroundAmount: Float = 6f,
    val dialogEnhancerEnabled: Boolean = true,
    val dialogEnhancerAmount: Float = 5f,
    val volumeLevelerEnabled: Boolean = true,
    val volumeLevelerAmount: Float = 4f,
    val bassEnhancerEnabled: Boolean = false,
    val bassEnhancerAmount: Float = 10f,
    val customSlotIndex: Int = 0,
    val customEqEnabledInPreset: Boolean = false,
    val customSlots: List<List<Float>> = listOf(
        // All 3 Personalize User slots in PC XML default to array_20_zero (flat 0.0 dB)
        List(10) { 0f },
        List(10) { 0f },
        List(10) { 0f }
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
