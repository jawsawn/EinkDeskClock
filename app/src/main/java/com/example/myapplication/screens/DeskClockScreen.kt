package com.example.myapplication.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.myapplication.ClockState
import com.example.myapplication.utils.MoonPhaseCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import java.util.Locale

// High-contrast, E-ink-friendly colors (Zero alpha blending to prevent 1-bit/4-bit E-ink dithering)
private val LcdActiveColor = Color(0xFF141414)       // Deep carbon ink black
private val LcdUnlitColor = Color(0xFFDADCD2)        // Subtle solid unlit segment ghosting
private val LcdPanelBackground = Color(0xFFECEEE6)   // Crisp high-contrast reflective LCD / E-ink substrate
private val LcdGridLineColor = Color(0xFF141414)     // Solid crisp rules
private val OuterCasingColor = Color(0xFF141414)     // Matte black outer desk clock bezel
private val OuterTrimColor = Color(0xFF2C2C2C)       // Subtle chamfered bezel edge

// 7-segment digit map: [A, B, C, D, E, F, G]
private val SEGMENT_MAP = mapOf(
    '0' to booleanArrayOf(true, true, true, true, true, true, false),
    '1' to booleanArrayOf(false, true, true, false, false, false, false),
    '2' to booleanArrayOf(true, true, false, true, true, false, true),
    '3' to booleanArrayOf(true, true, true, true, false, false, true),
    '4' to booleanArrayOf(false, true, true, false, false, true, true),
    '5' to booleanArrayOf(true, false, true, true, false, true, true),
    '6' to booleanArrayOf(true, false, true, true, true, true, true),
    '7' to booleanArrayOf(true, true, true, false, false, false, false),
    '8' to booleanArrayOf(true, true, true, true, true, true, true),
    '9' to booleanArrayOf(true, true, true, true, false, true, true),
    '-' to booleanArrayOf(false, false, false, false, false, false, true),
    ' ' to booleanArrayOf(false, false, false, false, false, false, false),
)

/**
 * Reads battery level and charging state without continuous listeners.
 */
fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
    val batteryStatus: Intent? = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

    val pct = if (level >= 0 && scale > 0) {
        (level * 100f / scale.toFloat()).toInt().coerceIn(0, 100)
    } else {
        100
    }

    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

    return Pair(pct, isCharging)
}

/**
 * Calculates ClockState from local system epoch time and battery info.
 */
fun calculateCurrentClockState(
    context: Context,
    timeMs: Long = System.currentTimeMillis()
): ClockState {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timeMs
    }

    val hours = String.format(Locale.US, "%02d", calendar[Calendar.HOUR_OF_DAY])
    val minutes = String.format(Locale.US, "%02d", calendar[Calendar.MINUTE])
    val month = String.format(Locale.US, "%02d", calendar[Calendar.MONTH] + 1)
    val dayOfMonth = String.format(Locale.US, "%02d", calendar[Calendar.DAY_OF_MONTH])

    val dayOfWeekStr = when (calendar[Calendar.DAY_OF_WEEK]) {
        Calendar.MONDAY -> "MON"
        Calendar.TUESDAY -> "TUE"
        Calendar.WEDNESDAY -> "WED"
        Calendar.THURSDAY -> "THU"
        Calendar.FRIDAY -> "FRI"
        Calendar.SATURDAY -> "SAT"
        Calendar.SUNDAY -> "SUN"
        else -> "MON"
    }

    val moonPhase = MoonPhaseCalculator.calculateMoonPhase(timeMs)
    val (batteryLevel, isCharging) = getBatteryInfo(context)

    return ClockState(
        hours = hours,
        minutes = minutes,
        month = month,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeekStr,
        moonPhase = moonPhase,
        batteryLevel = batteryLevel,
        isCharging = isCharging
    )
}

/**
 * Canvas composable rendering a single retro 7-segment digit with mitered segment joints.
 */
@Composable
fun SevenSegmentDigit(
    digitChar: Char,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor,
    slantRatio: Float = -0.04f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = (w * 0.145f).coerceAtMost(h * 0.085f)
        val g = t * 0.16f

        val activeMap = SEGMENT_MAP[digitChar] ?: BooleanArray(7) { false }

        val shearMatrix = Matrix().apply {
            values[1] = slantRatio
        }

        withTransform({
            transform(shearMatrix)
        }) {
            // Segment A (Top horizontal)
            val pathA = Path().apply {
                moveTo(t / 2 + g, g)
                lineTo(w - t / 2 - g, g)
                lineTo(w - t - g, t)
                lineTo(t + g, t)
                close()
            }
            drawPath(pathA, if (activeMap[0]) activeColor else unlitColor)

            // Segment B (Top right vertical)
            val pathB = Path().apply {
                moveTo(w - g, t / 2 + g)
                lineTo(w - g, h / 2 - g / 2)
                lineTo(w - t, h / 2 - t / 2 - g)
                lineTo(w - t, t + g)
                close()
            }
            drawPath(pathB, if (activeMap[1]) activeColor else unlitColor)

            // Segment C (Bottom right vertical)
            val pathC = Path().apply {
                moveTo(w - g, h / 2 + g / 2)
                lineTo(w - g, h - t / 2 - g)
                lineTo(w - t, h - t - g)
                lineTo(w - t, h / 2 + t / 2 + g)
                close()
            }
            drawPath(pathC, if (activeMap[2]) activeColor else unlitColor)

            // Segment D (Bottom horizontal)
            val pathD = Path().apply {
                moveTo(t / 2 + g, h - g)
                lineTo(w - t / 2 - g, h - g)
                lineTo(w - t - g, h - t)
                lineTo(t + g, h - t)
                close()
            }
            drawPath(pathD, if (activeMap[3]) activeColor else unlitColor)

            // Segment E (Bottom left vertical)
            val pathE = Path().apply {
                moveTo(g, h / 2 + g / 2)
                lineTo(t, h / 2 + t / 2 + g)
                lineTo(t, h - t - g)
                lineTo(g, h - t / 2 - g)
                close()
            }
            drawPath(pathE, if (activeMap[4]) activeColor else unlitColor)

            // Segment F (Top left vertical)
            val pathF = Path().apply {
                moveTo(g, t / 2 + g)
                lineTo(t, t + g)
                lineTo(t, h / 2 - t / 2 - g)
                lineTo(g, h / 2 - g / 2)
                close()
            }
            drawPath(pathF, if (activeMap[5]) activeColor else unlitColor)

            // Segment G (Middle horizontal)
            val pathG = Path().apply {
                moveTo(t + g, h / 2 - t / 2)
                lineTo(w - t - g, h / 2 - t / 2)
                lineTo(w - t / 2 - g, h / 2)
                lineTo(w - t - g, h / 2 + t / 2)
                lineTo(t + g, h / 2 + t / 2)
                lineTo(t / 2 + g, h / 2)
                close()
            }
            drawPath(pathG, if (activeMap[6]) activeColor else unlitColor)
        }
    }
}

/**
 * Classic slanted LCD colon (`:`) separator between HH and MM.
 */
@Composable
fun SevenSegmentColon(
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    slantRatio: Float = -0.04f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val dotSize = (w * 0.56f).coerceAtMost(h * 0.11f)
        val halfDot = dotSize / 2f
        val cx = w / 2f
        val topCy = h * 0.34f
        val botCy = h * 0.66f

        val shearMatrix = Matrix().apply {
            values[1] = slantRatio
        }

        withTransform({
            transform(shearMatrix)
        }) {
            val corner = CornerRadius(dotSize * 0.18f, dotSize * 0.18f)
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - halfDot, topCy - halfDot),
                size = Size(dotSize, dotSize),
                cornerRadius = corner
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - halfDot, botCy - halfDot),
                size = Size(dotSize, dotSize),
                cornerRadius = corner
            )
        }
    }
}

/**
 * Classic slanted LCD slash (`/`) separator for Date / Month.
 */
@Composable
fun LcdSlashSeparator(
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val padY = h * 0.14f

        val path = Path().apply {
            moveTo(w * 0.72f, padY)
            lineTo(w * 0.96f, padY)
            lineTo(w * 0.28f, h - padY)
            lineTo(w * 0.04f, h - padY)
            close()
        }
        drawPath(path, activeColor)
    }
}

/**
 * Top Section Header Bar: Crisp dark bar with "TIME" and "24H" indicator.
 */
@Composable
fun TopHeaderBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LcdActiveColor)
            .padding(vertical = 5.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "QUARTZ // E-INK CHRONOGRAPH",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = LcdPanelBackground
            )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(LcdPanelBackground, RoundedCornerShape(2.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "24H",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = LcdActiveColor
                    )
                )
            }
            Text(
                text = "TIME",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = LcdPanelBackground
                )
            )
        }
    }
}

/**
 * Top Screen 24-hour time display: `HH : MM` responsive to available width and height.
 */
@Composable
fun TopTimeScreen(
    hours: String,
    minutes: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth
        val availH = maxHeight

        // Total width in terms of digitHeight:
        // 4 digits * 0.52 + 2 inner gaps * 0.06 + colon width 0.16 + 2 colon gaps * 0.055 = 2.47 * digitHeight
        val maxDigitHByWidth = availW / 2.55f
        val digitH = min(availH * 0.90f, maxDigitHByWidth)
        val digitW = digitH * 0.52f
        val innerGap = digitH * 0.055f
        val colonW = digitH * 0.16f
        val colonGap = digitH * 0.055f

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SevenSegmentDigit(
                digitChar = hours.getOrElse(0) { '0' },
                modifier = Modifier
                    .width(digitW)
                    .height(digitH)
            )
            Spacer(modifier = Modifier.width(innerGap))
            SevenSegmentDigit(
                digitChar = hours.getOrElse(1) { '0' },
                modifier = Modifier
                    .width(digitW)
                    .height(digitH)
            )

            Spacer(modifier = Modifier.width(colonGap))
            SevenSegmentColon(
                modifier = Modifier
                    .width(colonW)
                    .height(digitH)
            )
            Spacer(modifier = Modifier.width(colonGap))

            SevenSegmentDigit(
                digitChar = minutes.getOrElse(0) { '0' },
                modifier = Modifier
                    .width(digitW)
                    .height(digitH)
            )
            Spacer(modifier = Modifier.width(innerGap))
            SevenSegmentDigit(
                digitChar = minutes.getOrElse(1) { '0' },
                modifier = Modifier
                    .width(digitW)
                    .height(digitH)
            )
        }
    }
}

/**
 * Middle Divider Bar: Solid dark bar with high-contrast labels aligned strictly with the 5-unit bottom grid:
 * Grid 1&2 (1.85f): DATE / MONTH | Grid 3 (1.05f): DAY | Grid 4 (1.05f): MOON PHASE | Grid 5 (1.05f): BATTERY
 */
@Composable
fun MiddleDividerBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LcdActiveColor)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grid 1 & 2: DATE / MONTH
        Row(
            modifier = Modifier.weight(1.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DATE",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = LcdPanelBackground
                )
            )
            Text(
                text = "MONTH",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = LcdPanelBackground
                )
            )
        }

        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(LcdPanelBackground)
        )

        // Grid 3: DAY
        Text(
            text = "DAY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = LcdPanelBackground
            ),
            modifier = Modifier.weight(1.05f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(LcdPanelBackground)
        )

        // Grid 4: MOON PHASE
        Text(
            text = "MOON PHASE",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.0.sp,
                color = LcdPanelBackground
            ),
            modifier = Modifier.weight(1.05f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(LcdPanelBackground)
        )

        // Grid 5: BATTERY
        Text(
            text = "BATTERY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = LcdPanelBackground
            ),
            modifier = Modifier.weight(1.05f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Visual moon phase symbol on Canvas in crisp LCD dark/unlit style.
 */
@Composable
fun MoonIcon(
    phaseRatio: Double,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.5.dp.toPx()
        val radius = (size.minDimension / 2f - strokeWidth).coerceAtLeast(1f)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Draw unlit background moon disk
        drawCircle(
            color = unlitColor,
            radius = radius,
            center = Offset(cx, cy)
        )

        val path = Path()
        val r = (phaseRatio % 1.0 + 1.0) % 1.0

        if (r < 0.5) {
            // Waxing phase (illumination on right)
            val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
            path.moveTo(cx, cy - radius)
            path.arcTo(rect, -90f, 180f, forceMoveTo = false)

            val wFactor = (1.0 - 4.0 * r).toFloat() * radius
            val innerRect = Rect(
                left = if (wFactor >= 0) cx - wFactor else cx + wFactor,
                top = cy - radius,
                right = if (wFactor >= 0) cx + wFactor else cx - wFactor,
                bottom = cy + radius
            )
            val sweep = if (wFactor >= 0) -180f else 180f
            path.arcTo(innerRect, 90f, sweep, forceMoveTo = false)
            path.close()
            drawPath(path, activeColor)
        } else {
            // Waning phase (illumination on left)
            val rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
            path.moveTo(cx, cy - radius)
            path.arcTo(rect, -90f, -180f, forceMoveTo = false)

            val wFactor = (4.0 * r - 3.0).toFloat() * radius
            val innerRect = Rect(
                left = if (wFactor >= 0) cx - wFactor else cx + wFactor,
                top = cy - radius,
                right = if (wFactor >= 0) cx + wFactor else cx - wFactor,
                bottom = cy + radius
            )
            val sweep = if (wFactor >= 0) 180f else -180f
            path.arcTo(innerRect, 90f, sweep, forceMoveTo = false)
            path.close()
            drawPath(path, activeColor)
        }

        // Crisp dark outer ring
        drawCircle(
            color = activeColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Segmented LCD Battery Bar indicator (4 bars + terminal nub) for authentic hardware look.
 */
@Composable
fun LcdBatteryBar(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val strokeW = (h * 0.11f).coerceIn(2f, 5f)
        val nubW = w * 0.06f
        val bodyW = w - nubW - strokeW
        val bodyH = h - strokeW

        // Outer battery casing
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeW)
        )

        // Positive terminal nub on right
        val nubH = bodyH * 0.45f
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(strokeW / 2f + bodyW, (h - nubH) / 2f),
            size = Size(nubW, nubH),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )

        // 4 internal LCD segments
        val pad = strokeW * 1.6f
        val innerW = bodyW - pad * 2f
        val innerH = bodyH - pad * 2f
        val gap = innerW * 0.05f
        val segW = (innerW - gap * 3f) / 4f
        val litSegments = when {
            batteryLevel >= 80 -> 4
            batteryLevel >= 55 -> 3
            batteryLevel >= 30 -> 2
            batteryLevel >= 10 -> 1
            else -> 0
        }

        for (i in 0 until 4) {
            val segX = strokeW / 2f + pad + i * (segW + gap)
            val segY = strokeW / 2f + pad
            drawRect(
                color = if (i < litSegments) activeColor else unlitColor,
                topLeft = Offset(segX, segY),
                size = Size(segW, innerH)
            )
        }
    }
}

/**
 * Bottom Screen filling each grid cell cleanly with responsive sizing:
 * Grid 1 & 2 (1.85f): Date DD / MM (7-Segment Digits + LCD Slash)
 * Grid 3 (1.05f): Day of Week (Framed LCD badge + responsive bold monospace text)
 * Grid 4 (1.05f): Moon Phase Icon + Phase Descriptor Pill
 * Grid 5 (1.05f): Segmented LCD Battery Bar + Numeric Percentage
 */
@Composable
fun BottomInfoScreen(
    clockState: ClockState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grid 1 & 2: Date Section DD / MM
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.85f)
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val cellW = maxWidth
            val cellH = maxHeight
            val maxDigitHByW = cellW / 2.48f
            val digitH = min(cellH * 0.78f, maxDigitHByW)
            val digitW = digitH * 0.50f
            val smallGap = digitH * 0.05f
            val slashW = digitH * 0.22f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Day Tens
                SevenSegmentDigit(
                    digitChar = clockState.dayOfMonth.getOrElse(0) { '0' },
                    modifier = Modifier
                        .width(digitW)
                        .height(digitH)
                )
                Spacer(modifier = Modifier.width(smallGap))
                // Day Units
                SevenSegmentDigit(
                    digitChar = clockState.dayOfMonth.getOrElse(1) { '0' },
                    modifier = Modifier
                        .width(digitW)
                        .height(digitH)
                )

                Spacer(modifier = Modifier.width(smallGap * 1.4f))
                LcdSlashSeparator(
                    modifier = Modifier
                        .width(slashW)
                        .height(digitH)
                )
                Spacer(modifier = Modifier.width(smallGap * 1.4f))

                // Month Tens
                SevenSegmentDigit(
                    digitChar = clockState.month.getOrElse(0) { '0' },
                    modifier = Modifier
                        .width(digitW)
                        .height(digitH)
                )
                Spacer(modifier = Modifier.width(smallGap))
                // Month Units
                SevenSegmentDigit(
                    digitChar = clockState.month.getOrElse(1) { '0' },
                    modifier = Modifier
                        .width(digitW)
                        .height(digitH)
                )
            }
        }

        // Solid Section Divider 1
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(LcdGridLineColor)
        )

        // Grid 3: Day of Week Section (Responsive font sizing that never wraps or clips)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val cellW = maxWidth
            val cellH = maxHeight
            val fontSizeSp = with(density) {
                val byWidth = (cellW.toPx() * 0.44f).toSp()
                val byHeight = (cellH.toPx() * 0.48f).toSp()
                if (byWidth.value < byHeight.value) byWidth else byHeight
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.78f)
                    .border(2.dp, LcdActiveColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clockState.dayOfWeek,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = fontSizeSp,
                        letterSpacing = 1.sp,
                        color = LcdActiveColor
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Solid Section Divider 2
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(LcdGridLineColor)
        )

        // Grid 4: Moon Phase Section (Moon Icon on top + Inverted Stamp Label on bottom)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val cellW = maxWidth
            val cellH = maxHeight
            val labelFontSp = with(density) {
                (min(cellW * 0.135f, cellH * 0.14f)).toSp()
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MoonIcon(
                    phaseRatio = clockState.moonPhase.phaseRatio,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(LcdActiveColor, RoundedCornerShape(4.dp))
                        .padding(vertical = 3.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = clockState.moonPhase.descriptor,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = labelFontSp,
                            letterSpacing = 0.5.sp,
                            color = LcdPanelBackground
                        ),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Solid Section Divider 3
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(LcdGridLineColor)
        )

        // Grid 5: Battery Section (7-Segment/Monospace Numeric + Segmented LCD Bar)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val cellW = maxWidth
            val cellH = maxHeight
            val barHeight = min(cellH * 0.28f, 28.dp)
            val pctFontSp = with(density) {
                val byW = (cellW.toPx() * 0.30f).toSp()
                val byH = (cellH.toPx() * 0.34f).toSp()
                if (byW.value < byH.value) byW else byH
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LcdBatteryBar(
                    batteryLevel = clockState.batteryLevel,
                    isCharging = clockState.isCharging,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(barHeight)
                )

                Text(
                    text = if (clockState.isCharging) "${clockState.batteryLevel}% CHG" else "${clockState.batteryLevel}%",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = if (clockState.isCharging) pctFontSp * 0.72f else pctFontSp,
                        color = LcdActiveColor
                    ),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Main DeskClockScreen composable.
 */
@Composable
fun DeskClockScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var clockState by remember { mutableStateOf(calculateCurrentClockState(context)) }

    // Efficient suspended update loop aligning strictly to the top of each minute boundary
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            clockState = calculateCurrentClockState(context, now)
            // Milliseconds remaining until next minute boundary :00.000 ms
            val delayMs = 60000L - (now % 60000L)
            delay(delayMs)
        }
    }

    // Hourly micro pixel-shift (-1.5dp to +1.5dp) to prevent static segment E-ink image retention / burn-in
    val hourInt = clockState.hours.toIntOrNull() ?: 0
    val shiftX = (((hourInt % 3) - 1) * 1.5f).dp
    val shiftY = ((((hourInt / 3) % 3) - 1) * 1.5f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OuterCasingColor)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .offset(x = shiftX, y = shiftY),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            color = OuterCasingColor,
            border = BorderStroke(3.dp, OuterTrimColor),
            shadowElevation = 0.dp // Zero shadow elevation for crisp E-ink rendering
        ) {
            val innerShape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp) // Inner LCD frame bezel padding
                    .clip(innerShape)
                    .background(LcdPanelBackground, innerShape)
                    .border(2.5.dp, LcdActiveColor, innerShape)
            ) {
                // Top Header Bar
                TopHeaderBar()

                // Top Screen: Time HH : MM
                TopTimeScreen(
                    hours = clockState.hours,
                    minutes = clockState.minutes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.85f)
                )

                // Middle Divider Bar
                MiddleDividerBar()

                // Bottom Screen: Date (DD/MM) | Day | Moon Phase | Battery
                BottomInfoScreen(
                    clockState = clockState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.05f)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun DeskClockScreenPreview() {
    DeskClockScreen()
}
