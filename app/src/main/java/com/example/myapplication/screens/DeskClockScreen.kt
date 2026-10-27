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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ClockState
import com.example.myapplication.utils.MoonPhaseCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import java.util.Locale

// High-contrast, E-ink-friendly colors
private val LcdActiveColor = Color(0xFF1A1A1A)
private val LcdUnlitColor = Color(0x121A1A1A) // ~0.07 alpha background segment
private val LcdPanelBackground = Color(0xFFDCDED5) // Classic LCD light gray/green panel tint

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
 * Canvas composable rendering a single retro 7-segment digit.
 */
@Composable
fun SevenSegmentDigit(
    digitChar: Char,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor,
    slantRatio: Float = -0.05f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = w * 0.13f
        val g = t * 0.18f

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
 * Top Screen 24-hour time display: `HH MM` with "TIME" pinned in upper-right corner.
 */
@Composable
fun TopTimeScreen(
    hours: String,
    minutes: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Pinned "TIME" label in upper-right corner
        Text(
            text = "TIME",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = LcdActiveColor
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
        )

        // 24-hour time display: HH MM centered with elegant proportional height
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.82f)
                .align(Alignment.Center)
                .padding(top = 16.dp)
        ) {
            SevenSegmentDigit(
                digitChar = hours.getOrElse(0) { '0' },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.52f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            SevenSegmentDigit(
                digitChar = hours.getOrElse(1) { '0' },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.52f)
            )

            // Gap between HH and MM
            Spacer(modifier = Modifier.width(28.dp))

            SevenSegmentDigit(
                digitChar = minutes.getOrElse(0) { '0' },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.52f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            SevenSegmentDigit(
                digitChar = minutes.getOrElse(1) { '0' },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.52f)
            )
        }
    }
}

/**
 * Middle Divider Bar: Solid dark bar with high-contrast white labels:
 * Grid 1&2 (2.0f): DATE MONTH | Grid 3 (1.0f): DAY | Grid 4 (1.0f): MOON PHASE | Grid 5 (1.0f): BATTERY
 */
@Composable
fun MiddleDividerBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LcdActiveColor)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grid 1 & 2: DATE MONTH (2.0 Grid Units)
        Row(
            modifier = Modifier.weight(2.0f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DATE",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            )
            Text(
                text = "MONTH",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            )
        }

        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        // Grid 3: DAY (1.0 Grid Unit)
        Text(
            text = "DAY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White
            ),
            modifier = Modifier.weight(1.0f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        // Grid 4: MOON PHASE (1.0 Grid Unit)
        Text(
            text = "MOON PHASE",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White
            ),
            modifier = Modifier.weight(1.0f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        // Grid 5: BATTERY (1.0 Grid Unit)
        Text(
            text = "BATTERY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White
            ),
            modifier = Modifier.weight(1.0f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Visual moon phase symbol on Canvas in LCD dark/unlit style.
 */
@Composable
fun MoonIcon(
    phaseRatio: Double,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f - 2.dp.toPx()
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

        // Dark outer ring
        drawCircle(
            color = activeColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

/**
 * Bottom Screen filling each grid cell completely:
 * Grid 1 & 2 (2.0f): Date DD / MM (Day then Month)
 * Grid 3 (1.0f): Day of Week (Huge 76sp font filling 100% of Grid 3)
 * Grid 4 (1.0f): Moon Icon filling top of Grid 4 + Name on bottom
 * Grid 5 (1.0f): Flat Battery Percentage text (Huge 52sp font, no outline icon)
 */
@Composable
fun BottomInfoScreen(
    clockState: ClockState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grid 1 & 2: Date Section DD/MM (Occupies 2 Grid Units = 2.0f)
        Row(
            modifier = Modifier
                .weight(2.0f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Day Tens
            SevenSegmentDigit(
                digitChar = clockState.dayOfMonth.getOrElse(0) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.85f)
                    .aspectRatio(0.50f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Day Units
            SevenSegmentDigit(
                digitChar = clockState.dayOfMonth.getOrElse(1) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.85f)
                    .aspectRatio(0.50f)
            )

            Text(
                text = "/",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = LcdActiveColor
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Month Tens
            SevenSegmentDigit(
                digitChar = clockState.month.getOrElse(0) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.85f)
                    .aspectRatio(0.50f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            // Month Units
            SevenSegmentDigit(
                digitChar = clockState.month.getOrElse(1) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.85f)
                    .aspectRatio(0.50f)
            )
        }

        // Section Divider 1
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight(0.85f)
                .background(LcdActiveColor.copy(alpha = 0.3f))
        )

        // Grid 3: Day of Week Section (Huge 76sp font filling 100% of Grid 3)
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = clockState.dayOfWeek,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 76.sp,
                    color = LcdActiveColor
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section Divider 2
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight(0.85f)
                .background(LcdActiveColor.copy(alpha = 0.3f))
        )

        // Grid 4: Moon Phase Section (Expanded Moon Icon filling top of Grid 4 + Name on bottom)
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MoonIcon(
                phaseRatio = clockState.moonPhase.phaseRatio,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            )
            Text(
                text = clockState.moonPhase.descriptor,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LcdActiveColor
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section Divider 3
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight(0.85f)
                .background(LcdActiveColor.copy(alpha = 0.3f))
        )

        // Grid 5: Flat Battery Percentage Section (Huge 52sp font, no outline icon)
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (clockState.isCharging) "${clockState.batteryLevel}%⚡" else "${clockState.batteryLevel}%",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 52.sp,
                    color = LcdActiveColor
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
            .background(Color(0xFF111111)) // Ambient outer dark background
            .padding(horizontal = 16.dp, vertical = 12.dp) // Outer margin from physical screen edges
            .offset(x = shiftX, y = shiftY),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = LcdActiveColor,
            border = BorderStroke(4.dp, Color(0xFF333333)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp) // Inner LCD frame bezel padding
                    .background(LcdPanelBackground, RoundedCornerShape(10.dp))
                    .border(2.5.dp, LcdActiveColor, RoundedCornerShape(10.dp))
            ) {
                // Top Screen: Time HH MM
                TopTimeScreen(
                    hours = clockState.hours,
                    minutes = clockState.minutes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.8f)
                )

                // Middle Divider Bar
                MiddleDividerBar()

                // Bottom Screen: Date (DD/MM) | Day | Moon Phase | Battery
                BottomInfoScreen(
                    clockState = clockState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun DeskClockScreenPreview() {
    DeskClockScreen()
}
