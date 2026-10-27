package com.example.myapplication

import androidx.compose.runtime.Immutable
import com.example.myapplication.utils.MoonPhase

/**
 * Immutable state representation for the ultra-low-power E-ink desk clock.
 */
@Immutable
data class ClockState(
    val hours: String,        // 2-digit 24-hour time "00".."23"
    val minutes: String,      // 2-digit minutes "00".."59"
    val month: String,        // 2-digit month "01".."12"
    val dayOfMonth: String,   // 2-digit day of month "01".."31"
    val dayOfWeek: String,    // 3-letter day abbreviation "MON", "TUE", etc.
    val moonPhase: MoonPhase, // Calculated mathematical moon phase
    val batteryLevel: Int,    // Battery percentage 0..100
    val isCharging: Boolean,  // True if plugged in / charging
)
