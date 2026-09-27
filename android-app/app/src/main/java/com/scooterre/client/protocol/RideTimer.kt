package com.scooterre.client.protocol

/**
 * The app's own ride timer - independent of the scooter's own RIDING_TIME, which resets on short stops (confirmed
 * live, 2026-09-27). Starts once riding begins, keeps running through a short stop instead of resetting, and only
 * really ends after [PAUSE_TIMEOUT_MS] of standing still - the same "same outing" gap the Verlauf tab's ride list
 * groups by ([com.scooterre.client.protocol.RideLog.TRIP_GAP_MS]) - or when [reset] is called (a new connection).
 * Purely time-based and entirely separate from the consumption calculation (RideWindow/LiveRideTracker): it does
 * not read or write anything there.
 */
class RideTimer {
    private var startMs: Long? = null
    private var pausedSinceMs: Long? = null

    /** Feeds the current riding state; returns how long the ride has been going, or null while not riding
     * (including once a stop has lasted long enough to count as the ride having ended). */
    fun onReading(riding: Boolean, nowMs: Long): Long? {
        if (riding) {
            if (startMs == null) startMs = nowMs
            pausedSinceMs = null
        } else if (startMs != null) {
            val pausedSince = pausedSinceMs ?: nowMs
            pausedSinceMs = pausedSince
            if (nowMs - pausedSince >= PAUSE_TIMEOUT_MS) {
                reset()
                return null
            }
        }
        return startMs?.let { nowMs - it }
    }

    /** Forgets the running ride - called when the connection ends, so a ride never continues into the next one. */
    fun reset() {
        startMs = null
        pausedSinceMs = null
    }

    companion object {
        const val PAUSE_TIMEOUT_MS = 10 * 60_000L
    }
}

/** "X min" or "Y h ZZ min", rounded to the nearest minute (matches the ride-book/Verlauf cards' own format). */
fun formatRideTimerDuration(ms: Long): String {
    val minutes = ((ms + 30_000) / 60_000).toInt().coerceAtLeast(0)
    return if (minutes >= 60) "%d h %02d min".format(minutes / 60, minutes % 60) else "$minutes min"
}
