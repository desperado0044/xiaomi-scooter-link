package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class RideBookTest {
    private fun rec(d: Int, km: Int) = RideRecord(d, km, 180, 200)

    @Test
    fun parsesRecordsAndSkipsEmptyOnes() {
        val raw = "0240004401060160" + "0000000000000000"
        val r = RideBook.parse(raw)
        assertEquals(listOf(RideRecord(240, 44, 106, 160)), r)
        assertEquals(24.0, r[0].minutes, 1e-9)
        assertEquals(4.4, r[0].km, 1e-9)
    }

    @Test
    fun parsesTwoRecordsInOneSlot() {
        assertEquals(2, RideBook.parse("0240004401060160" + "0020000501800360").size)
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(emptyList<RideRecord>(), RideBook.parse("not a ride log!!!"))
        assertEquals(emptyList<RideRecord>(), RideBook.parse(""))
    }

    @Test
    fun onlyRecordsNotSeenBeforeAreNew() {
        val a = rec(100, 30)
        val b = rec(200, 50)
        val c = rec(300, 70)
        assertEquals(listOf(c), RideBook.newRecords(listOf(a, b), listOf(b, c, a)))
    }

    @Test
    fun identicalRidesCountTwice() {
        val a = rec(100, 30)
        assertEquals(listOf(a), RideBook.newRecords(listOf(a), listOf(a, a)))
    }

    @Test
    fun nothingNewWhenNothingChanged() {
        val a = rec(100, 30)
        assertEquals(emptyList<RideRecord>(), RideBook.newRecords(listOf(a), listOf(a)))
    }

    // Minute 0 is deliberately never used here: RideBookEntry treats savedMs<=0 as "no time known" (see its own
    // doc comment), so a real dated entry always starts at least one minute after some epoch.
    private fun at(min: Long, d: Int, km: Int) = RideBookEntry(1_000_000L + min * 60_000L, rec(d, km))

    @Test
    fun growingSnapshotsOfOneRideCollapseToTheLastOne() {
        val entries = listOf(at(0, 10, 2), at(1, 50, 5), at(2, 90, 8), at(3, 90, 9))
        assertEquals(listOf(at(3, 90, 9)), RideBook.mergeGrowthFragments(entries))
    }

    @Test
    fun twoRealRidesFarApartStaySeparate() {
        val entries = listOf(at(0, 90, 9), at(45, 90, 9))
        assertEquals(entries, RideBook.mergeGrowthFragments(entries))
    }

    @Test
    fun gapsUpToHalfAnHourStillMerge() {
        // Confirmed live, 2026-09-27: fragments of one real ride can be up to ~22 min apart (the read schedule
        // only ever picks ONE overdue value at a time among ~50, so a re-read of the ride-log slots can be
        // delayed well past its own 60s interval by contention with everything else that's also due).
        val entries = listOf(at(0, 30, 12), at(29, 71, 225))
        assertEquals(listOf(at(29, 71, 225)), RideBook.mergeGrowthFragments(entries))
    }

    @Test
    fun aShrinkBreaksTheCluster() {
        val entries = listOf(at(0, 90, 9), at(1, 30, 3))
        assertEquals(entries, RideBook.mergeGrowthFragments(entries))
    }

    @Test
    fun undatedEntriesAreLeftAlone() {
        val undated = RideBookEntry(0, rec(50, 5))
        assertEquals(listOf(undated, undated), RideBook.mergeGrowthFragments(listOf(undated, undated)))
    }
}
