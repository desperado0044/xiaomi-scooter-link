package com.scooterre.client.wear

import com.scooterre.client.protocol.KnownDevice
import kotlinx.coroutines.flow.MutableStateFlow

/** Same shape as the phone's OverlayData - what got relayed over the Data Layer, plus when. */
data class RelayStatus(
    val rest: String = "",
    val trip: String = "",
    val battery: String = "",
    val rideTime: String = "",
    val standby: Boolean = false,
    val updatedAt: Long = 0L,
)

/** The small set of app-level settings the watch also offers standalone - mirrors the phone's. */
data class WatchSettings(
    val unitsImperial: Boolean = false,
    val languageDe: Boolean = true,
    val confirmCritical: Boolean = false,
    val rideTracking: Boolean = true,
)

/** Holds what was relayed/pushed from the phone. Loaded from disk at process start (see
 * WearApplication), then kept current by StatusListenerService without needing MainActivity
 * to be running. */
object WatchState {
    val status = MutableStateFlow<RelayStatus?>(null)

    /** Every scooter the phone has pushed a key for - can be more than one (see DeviceRegistry). */
    val devices = MutableStateFlow<List<KnownDevice>>(emptyList())
    val settings = MutableStateFlow(WatchSettings())

    /** Set by the daily automatic check (see WearUpdate) - a version string if it found one newer
     * than what's installed, null otherwise. Downloading/installing stays a separate, explicit step
     * ("Update suchen" on the App screen), exactly like the phone's own daily-check-then-manual-
     * download split. */
    val availableUpdateVersion = MutableStateFlow<String?>(null)
}
