package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideTimerTest {
    private val min = 60_000L

    @Test
    fun noRideBeforeRidingStarts() {
        val t = RideTimer()
        assertNull(t.onReading(riding = false, nowMs = 0))
    }

    @Test
    fun durationGrowsWhileRiding() {
        val t = RideTimer()
        t.onReading(riding = true, nowMs = 0)
        assertEquals(5 * min, t.onReading(riding = true, nowMs = 5 * min))
    }

    @Test
    fun aShortStopDoesNotResetTheTimer() {
        val t = RideTimer()
        t.onReading(riding = true, nowMs = 0)
        t.onReading(riding = false, nowMs = 5 * min) // stops for a bit
        assertEquals(8 * min, t.onReading(riding = false, nowMs = 8 * min))
        assertEquals(12 * min, t.onReading(riding = true, nowMs = 12 * min)) // rides on
    }

    @Test
    fun aLongStopEndsTheRide() {
        val t = RideTimer()
        t.onReading(riding = true, nowMs = 0)
        t.onReading(riding = false, nowMs = 5 * min)
        assertNull(t.onReading(riding = false, nowMs = 5 * min + RideTimer.PAUSE_TIMEOUT_MS))
    }

    @Test
    fun ridingAgainAfterTheRideEndedStartsAFreshOne() {
        val t = RideTimer()
        t.onReading(riding = true, nowMs = 0)
        t.onReading(riding = false, nowMs = 5 * min) // the stop begins
        val end = 5 * min + RideTimer.PAUSE_TIMEOUT_MS
        assertNull(t.onReading(riding = false, nowMs = end)) // long enough now: ride ends here
        t.onReading(riding = true, nowMs = end) // the fresh ride starts (duration 0 the instant it's noticed)
        assertEquals(2 * min, t.onReading(riding = true, nowMs = end + 2 * min))
    }

    @Test
    fun resetForgetsTheRunningRide() {
        val t = RideTimer()
        t.onReading(riding = true, nowMs = 0)
        t.reset()
        assertNull(t.onReading(riding = false, nowMs = 1 * min))
    }
}
