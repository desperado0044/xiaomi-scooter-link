package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutCheckTest {
    private val batteryLevelProperty = SpecProperty(1, 2, "BATTERY_LEVEL", SpecType.UINT8)
    private val ridingModeProperty = SpecProperty(1, 1, "RIDING_MODE", SpecType.UINT8)
    private val totalMileageProperty = SpecProperty(2, 6, "TOTAL_MILEAGE", SpecType.FLOAT)

    private fun ok(property: SpecProperty, raw: ByteArray) = SpecReadResult(property, raw, status = 0)
    private fun rejected(property: SpecProperty) = SpecReadResult(property, ByteArray(0), status = 0xf05f)
    private fun timedOut(property: SpecProperty) = SpecReadResult(property, ByteArray(0), status = -1)

    private fun floatBytes(value: Float): ByteArray {
        val bits = value.toRawBits()
        return byteArrayOf((bits and 0xFF).toByte(), ((bits shr 8) and 0xFF).toByte(), ((bits shr 16) and 0xFF).toByte(), ((bits shr 24) and 0xFF).toByte())
    }

    @Test
    fun plausibleRealReadingsPass() {
        assertEquals(LayoutCheck.Outcome.PLAUSIBLE, LayoutCheck.batteryLevel(ok(batteryLevelProperty, encodeValue(SpecType.UINT8, 100))))
        assertEquals(LayoutCheck.Outcome.PLAUSIBLE, LayoutCheck.ridingMode(ok(ridingModeProperty, encodeValue(SpecType.UINT8, 3))))
        assertEquals(LayoutCheck.Outcome.PLAUSIBLE, LayoutCheck.totalMileage(ok(totalMileageProperty, floatBytes(33880.0f))))
    }

    @Test
    fun outOfRangeNumbersAreImplausibleEvenThoughTheReadSucceeded() {
        // A generic MIoT device-info byte reinterpreted as UINT8/FLOAT - "succeeds" but isn't a real reading.
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.batteryLevel(ok(batteryLevelProperty, encodeValue(SpecType.UINT8, 200))))
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.ridingMode(ok(ridingModeProperty, encodeValue(SpecType.UINT8, 200))))
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.totalMileage(ok(totalMileageProperty, floatBytes(1e30f))))
    }

    @Test
    fun nonFiniteOrNegativeMileageIsImplausible() {
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.totalMileage(ok(totalMileageProperty, floatBytes(Float.NaN))))
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.totalMileage(ok(totalMileageProperty, floatBytes(-5.0f))))
    }

    @Test
    fun explicitRejectionIsImplausible() {
        assertEquals(LayoutCheck.Outcome.IMPLAUSIBLE, LayoutCheck.batteryLevel(rejected(batteryLevelProperty)))
    }

    @Test
    fun timeoutAndMissingResultAreInconclusiveNotImplausible() {
        assertEquals(LayoutCheck.Outcome.INCONCLUSIVE, LayoutCheck.batteryLevel(timedOut(batteryLevelProperty)))
        assertEquals(LayoutCheck.Outcome.INCONCLUSIVE, LayoutCheck.batteryLevel(null))
    }
}
