package com.scooterre.client.service

import kotlinx.coroutines.flow.MutableStateFlow

/** What the floating overlay shows: the remaining range, the distance of the ride, or "Standby". */
data class OverlayData(val rest: String = "", val trip: String = "", val battery: String = "", val standby: Boolean = false)

/**
 * Hands the overlay its data. The connection lives in the view model, the overlay window in [ConnectionService];
 * this object is the small shared state between them (both run in the same process).
 */
object OverlayBus {
    val data = MutableStateFlow(OverlayData())

    /** The "overlay" switch in the app settings. */
    val enabled = MutableStateFlow(false)

    /** The app itself is on screen - the overlay then stays hidden, the dashboard shows the same numbers. */
    val appVisible = MutableStateFlow(true)
}
