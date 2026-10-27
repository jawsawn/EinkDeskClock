package com.example.myapplication.screens

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
import androidx.compose.foundation.layout.size
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
 * Calculates ClockState from local system epoch time.
 */
fun calculateCurrentClockState(timeMs: Long = System.currentTimeMillis()): ClockState {
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

    return ClockState(
        hours = hours,
        minutes = minutes,
        month = month,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeekStr,
        moonPhase = moonPhase,
    )
}

/**
 * Canvas composable rendering a single retro 7-segment digit.
 * Renders unlit background segments (`88`) behind active segments for physical LCD look.
 */
@Composable
fun SevenSegmentDigit(
    digitChar: Char,
    modifier: Modifier = Modifier,
    activeColor: Color = LcdActiveColor,
    unlitColor: Color = LcdUnlitColor,
    slantRatio: Float = -0.06f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = w * 0.16f
        val g = t * 0.22f

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
 * Top Screen 24-hour digital time display: `HH MM` with subtle `88 88` unlit background segments
 * and a small bold "TIME" label pinned in the upper-right corner.
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
            .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
    ) {
        // Pinned "TIME" label in upper-right corner
        Text(
            text = "TIME",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = LcdActiveColor
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 4.dp)
        )

        // 24-hour time display: HH MM with 7-segment digits
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(Alignment.Center)
                .padding(top = 18.dp)
        ) {
            SevenSegmentDigit(
                digitChar = hours.getOrElse(0) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.55f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            SevenSegmentDigit(
                digitChar = hours.getOrElse(1) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.55f)
            )

            // Spaced gap between HH and MM
            Spacer(modifier = Modifier.width(24.dp))

            SevenSegmentDigit(
                digitChar = minutes.getOrElse(0) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.55f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            SevenSegmentDigit(
                digitChar = minutes.getOrElse(1) { '0' },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.55f)
            )
        }
    }
}

/**
 * Middle Divider Bar: Solid dark horizontal bar spanning width with high-contrast white text labels:
 * MONTH, DATE, DAY, MOON PHASE.
 */
@Composable
fun MiddleDividerBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LcdActiveColor)
            .padding(vertical = 7.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MONTH",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
            )
            Text(
                text = "DATE",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        Text(
            text = "DAY",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
            ),
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        Text(
            text = "MOON PHASE",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
            ),
            modifier = Modifier.weight(1.2f),
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
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Bottom Screen divided into 3 sections:
 * 1. Date: MM/DD format
 * 2. Day of Week: 3-letter uppercase abbreviation
 * 3. Moon Phase: Visual moon icon alongside short descriptor
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
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Date: MM/DD
        Row(
            modifier = Modifier
                .weight(1.1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            SevenSegmentDigit(
                digitChar = clockState.month.getOrElse(0) { '0' },
                modifier = Modifier
                    .height(30.dp)
                    .width(18.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            SevenSegmentDigit(
                digitChar = clockState.month.getOrElse(1) { '0' },
                modifier = Modifier
                    .height(30.dp)
                    .width(18.dp)
            )

            Text(
                text = "/",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = LcdActiveColor
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            SevenSegmentDigit(
                digitChar = clockState.dayOfMonth.getOrElse(0) { '0' },
                modifier = Modifier
                    .height(30.dp)
                    .width(18.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            SevenSegmentDigit(
                digitChar = clockState.dayOfMonth.getOrElse(1) { '0' },
                modifier = Modifier
                    .height(30.dp)
                    .width(18.dp)
            )
        }

        // Section Divider
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(LcdActiveColor.copy(alpha = 0.25f))
        )

        // 2. Day of Week: 3-letter abbreviation
        Box(
            modifier = Modifier
                .weight(0.7f)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = clockState.dayOfWeek,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = LcdActiveColor
                ),
                textAlign = TextAlign.Center
            )
        }

        // Section Divider
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(LcdActiveColor.copy(alpha = 0.25f))
        )

        // 3. Moon Phase: Visual moon icon + short descriptor
        Row(
            modifier = Modifier
                .weight(1.2f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MoonIcon(
                phaseRatio = clockState.moonPhase.phaseRatio,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = clockState.moonPhase.descriptor,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = LcdActiveColor
                )
            )
        }
    }
}

/**
 * Main DeskClockScreen composable.
 * Uses top-of-minute alignment (`:00` boundary) via coroutine suspend `delay(delayMs)`.
 */
@Composable
fun DeskClockScreen(
    modifier: Modifier = Modifier
) {
    var clockState by remember { mutableStateOf(calculateCurrentClockState()) }

    // Efficient suspended update loop aligning strictly to the top of each minute boundary
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            clockState = calculateCurrentClockState(now)
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
            .background(Color(0xFF111111))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Physical LCD desk clock bezel enclosure with hourly micro-shift
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = shiftX, y = shiftY)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = LcdActiveColor,
            border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF333333)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .background(LcdPanelBackground, RoundedCornerShape(8.dp))
                    .border(2.dp, LcdActiveColor, RoundedCornerShape(8.dp))
            ) {
                // Top Screen: Time HH MM
                TopTimeScreen(
                    hours = clockState.hours,
                    minutes = clockState.minutes,
                    modifier = Modifier.fillMaxWidth()
                )

                // Middle Divider Bar
                MiddleDividerBar()

                // Bottom Screen: Date, Day of Week, Moon Phase
                BottomInfoScreen(
                    clockState = clockState,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 320)
@Composable
fun DeskClockScreenPreview() {
    DeskClockScreen()
}
