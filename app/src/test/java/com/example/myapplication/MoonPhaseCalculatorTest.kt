package com.example.myapplication

import com.example.myapplication.utils.MoonPhaseCalculator
import com.example.myapplication.utils.MoonPhaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseCalculatorTest {

    @Test
    fun testReferenceNewMoonEpoch() {
        // Epoch reference: Jan 11, 2024 at 11:57:00 UTC = 1704974220000L
        val newMoonMs = 1704974220000L
        val phase = MoonPhaseCalculator.calculateMoonPhase(newMoonMs)

        assertEquals(MoonPhaseType.NEW, phase.type)
        assertEquals("NEW", phase.descriptor)
        assertTrue("Ratio should be close to 0.0", phase.phaseRatio < 0.01)
    }

    @Test
    fun testFullMoonPhase() {
        // Full Moon occurs approx 14.76 days after New Moon
        val newMoonMs = 1704974220000L
        val fullMoonMs = newMoonMs + (14.765 * 86400.0 * 1000.0).toLong()
        val phase = MoonPhaseCalculator.calculateMoonPhase(fullMoonMs)

        assertEquals(MoonPhaseType.FULL, phase.type)
        assertEquals("FULL", phase.descriptor)
        assertTrue("Illuminated fraction should be close to 1.0", phase.illuminatedFraction > 0.95)
    }

    @Test
    fun testFirstQuarterPhase() {
        // First Quarter occurs approx 7.38 days after New Moon
        val newMoonMs = 1704974220000L
        val firstQuarterMs = newMoonMs + (7.38 * 86400.0 * 1000.0).toLong()
        val phase = MoonPhaseCalculator.calculateMoonPhase(firstQuarterMs)

        assertEquals(MoonPhaseType.FIRST_QUARTER, phase.type)
        assertEquals("FIRST QTR", phase.descriptor)
    }
}
