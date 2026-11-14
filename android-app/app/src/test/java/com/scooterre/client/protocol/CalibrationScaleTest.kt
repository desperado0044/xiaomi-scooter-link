package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationScaleTest {
    @Test
    fun recognizesTheConfirmedTimes100Pattern() {
        // raw 5417 (packed), owner reads 54.17 V in Xiaomi Home - the pattern every confirmed model uses.
        val guess = CalibrationScale.guess(raw = 5417.0, reference = 54.17)
        assertEquals(0.01, guess.scale)
    }

    @Test
    fun recognizesAnUnscaledProperty() {
        val guess = CalibrationScale.guess(raw = 42.0, reference = 42.0)
        assertEquals(1.0, guess.scale)
    }

    @Test
    fun reportsUnclearInsteadOfGuessingOutsideTheCandidateList() {
        // ratio 3.7 matches none of the fixed candidates within tolerance.
        val guess = CalibrationScale.guess(raw = 10.0, reference = 37.0)
        assertNull(guess.scale)
        assertTrue(kotlin.math.abs(guess.ratio - 3.7) < 0.0001)
    }

    @Test
    fun zeroRawNeverGuessesAScale() {
        assertNull(CalibrationScale.guess(raw = 0.0, reference = 12.0).scale)
    }

    @Test
    fun parsesLooselyTypedReferenceValues() {
        assertEquals(54.17, CalibrationScale.parseReference("54,17")!!, 0.0001)
        assertEquals(54.17, CalibrationScale.parseReference("54.17 V")!!, 0.0001)
        assertEquals(60.5, CalibrationScale.parseReference("~60.5 km")!!, 0.0001)
        assertNull(CalibrationScale.parseReference("unknown"))
        assertNull(CalibrationScale.parseReference(""))
    }

    @Test
    fun reportCoversUnparsableAndMissingEntries() {
        val report = CalibrationScale.report(
            "xiaomi.scooter.6",
            listOf(
                Triple("A_VOLTAGE", 5417f, "54.17"),
                Triple("A_MILEAGE_TOTAL", 1234f, "not a number"),
            ),
        )
        assertTrue(report, report.contains("A_VOLTAGE: raw=5417.0 reference=54.17"))
        assertTrue(report, report.contains("guessed scale=x0.01"))
        assertTrue(report, report.contains("A_MILEAGE_TOTAL: raw=1234.0 reference=\"not a number\" (could not parse as a number)"))
    }

    @Test
    fun reportNotesWhenNothingWasEntered() {
        val report = CalibrationScale.report("xiaomi.scooter.6", emptyList())
        assertTrue(report, report.contains("No reference value entered."))
    }
}
