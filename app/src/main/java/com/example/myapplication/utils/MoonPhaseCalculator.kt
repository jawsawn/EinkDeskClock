package com.example.myapplication.utils

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos

/**
 * The 8 standard astronomical moon phases with required short text descriptors.
 */
@Immutable
enum class MoonPhaseType(val descriptor: String) {
    NEW("NEW"),
    WAXING_CRESCENT("WAX CRES"),
    FIRST_QUARTER("FIRST QTR"),
    WAXING_GIBBOUS("WAX GIB"),
    FULL("FULL"),
    WANING_GIBBOUS("WAN GIB"),
    LAST_QUARTER("LAST QTR"),
    WANING_CRESCENT("WAN CRES"),
}

/**
 * Holds mathematical moon phase calculation results.
 * @param type Enum phase with short descriptor ("NEW", "WAX CRES", "FIRST QTR", etc.).
 * @param phaseRatio Normalized position in synodic month [0.0, 1.0) where 0.0 is New Moon and 0.5 is Full Moon.
 * @param illuminatedFraction Percentage of surface illumination [0.0, 1.0].
 * @param ageInDays Moon age in days [0.0, 29.53058867).
 */
@Immutable
data class MoonPhase(
    val type: MoonPhaseType,
    val phaseRatio: Double,
    val illuminatedFraction: Double,
    val ageInDays: Double,
) {
    val descriptor: String get() = type.descriptor
}

/**
 * Zero-library mathematical moon phase calculator using local epoch time.
 */
object MoonPhaseCalculator {
    // Mean synodic month period in days
    private const val SYNODIC_MONTH_DAYS = 29.53058867
    private const val SYNODIC_MONTH_MS = SYNODIC_MONTH_DAYS * 86400.0 * 1000.0

    // Reference epoch: Known New Moon on Jan 11, 2024 at 11:57:00 UTC (1704974220000 ms)
    private const val REFERENCE_NEW_MOON_MS = 1704974220000L

    /**
     * Calculates moon phase parameters for a given epoch timestamp [timeMs].
     */
    fun calculateMoonPhase(timeMs: Long = System.currentTimeMillis()): MoonPhase {
        val diffMs = timeMs - REFERENCE_NEW_MOON_MS
        var ratio = (diffMs % SYNODIC_MONTH_MS) / SYNODIC_MONTH_MS
        if (ratio < 0.0) {
            ratio += 1.0
        }

        val ageInDays = ratio * SYNODIC_MONTH_DAYS

        // Approximate illumination fraction using phase angle theta
        val theta = 2.0 * PI * ratio
        val illuminatedFraction = (1.0 - cos(theta)) / 2.0

        // Classify into 8 phases based on 0.125 cycle slice windows centered around principal phases
        val type = when {
            ratio < 0.0625 || ratio >= 0.9375 -> MoonPhaseType.NEW
            ratio < 0.1875 -> MoonPhaseType.WAXING_CRESCENT
            ratio < 0.3125 -> MoonPhaseType.FIRST_QUARTER
            ratio < 0.4375 -> MoonPhaseType.WAXING_GIBBOUS
            ratio < 0.5625 -> MoonPhaseType.FULL
            ratio < 0.6875 -> MoonPhaseType.WANING_GIBBOUS
            ratio < 0.8125 -> MoonPhaseType.LAST_QUARTER
            else -> MoonPhaseType.WANING_CRESCENT
        }

        return MoonPhase(
            type = type,
            phaseRatio = ratio,
            illuminatedFraction = illuminatedFraction,
            ageInDays = ageInDays,
        )
    }
}
