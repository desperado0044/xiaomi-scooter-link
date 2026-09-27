package com.scooterre.client.protocol

/** Which part of the dashboard is on screen - decides which values are read how often. */
enum class PollTab { OVERVIEW, RIDE, BATTERY, SETTINGS, VEHICLE, IDENTIFICATION, RIDE_LOG, OTHER }

/**
 * How often each value is read while connected. The scooter answers one request at a time in about
 * 0.15 s (measured), so reading all ~50 values in a loop kept the link busy with values that never
 * change - and made the ones that matter wait. Instead every value has its own interval, from how
 * fast it can change:
 *  - serial numbers, dates, firmware: once per connection ([intervalMs] returns null);
 *  - ride state, charge, riding mode: every 2.5 s, always (they drive the ride recording); the scooter's sleep flag too,
 *    and while the scooter sleeps nothing else is read at all (its values are frozen) until it wakes up;
 *  - odometer and trip values: every 2.5 s while riding, every 10 s parked - on the overview and ride
 *    tab, otherwise once a minute;
 *  - lock, charging, faults, temperatures and the like: every 20 s;
 *  - settings, battery health, tyre maintenance: once a minute;
 *  - battery voltage/current/power: every 3 s while the battery tab is open.
 * On top of that a value is read at once when its tab is opened, when the riding state, mode or charge
 * changes (range values), and after the app itself changed it (see the set functions).
 * Estimates of how fast each value changes, not measurements.
 */
object PollPlan {
    const val FAST_MS = 2_500L
    const val PARKED_MS = 10_000L
    const val MEDIUM_MS = 20_000L
    const val SLOW_MS = 60_000L
    const val BATTERY_LIVE_MS = 3_000L
    const val DEFAULT_MS = 15_000L
    const val RETRY_MS = 1_500L

    private val once = setOf("PRODUCTION_DATE", "ACTIVATION_DATE", "SCOOTER_SN", "BATTERY_SN", "FIRMWARE_VERSION", "BMS_FIRMWARE_VERSION")
    private val always = setOf("IS_RIDING", "RIDING_MODE", "BATTERY_LEVEL", "FAKE_SHUTDOWN_STATUS")
    // The trip values split in two: the range overlay needs the rest and the odometer even with the app in the
    // background (that's the whole point of it) - the other three are only ever shown on screen, so they have no
    // reason to be read fast while nobody can see them (measured cause of a real battery drain, 2026-09-26/27: the
    // last-open tab stayed "remembered" after backgrounding, so these kept polling every 2.5-10s for nothing).
    private val restTrip = setOf("REMAINING_MILEAGE", "CURRENT_MILEAGE")
    private val dashboardOnlyTrip = setOf("RIDING_TIME", "AVERAGE_SPEED", "HIGHEST_SPEED")
    private val medium = setOf(
        "IS_LOCKED", "ENERGY_RECOVERY", "IS_CHARGING", "FAULT", "BATTERY_STATUS", "BATTERY_TEMPERATURE",
        "SCOOTER_TEMPERATURE", "LOCK_WARNING", "CRUISE_IS_ON", "TAIL_LIGHT_IS_ON",
    )
    private val batteryLive = setOf("REMAINING_BATTERY", "VOLTAGE", "CURRENT", "POWER")
    private val slow = setOf(
        "SOH", "NUMBER_OF_CYCLES", "MORE_BATTERY_INFO", "MORE_BATTERY_INFO_2", "TIRE_MAINTENANCE", "ASR_IS_ON",
        "AUTO_LIGHT", "TCS", "INTELLIGENT_DOWNHILL", "HILL_PARKING", "ATMOSPHERE_LIGHT", "BLUETOOTH_SEARCH_ON",
        "MILEAGE_UNIT",
    )

    /** The values the overview shows (plus what the ride recording needs). */
    val OVERVIEW_NAMES = listOf(
        "BATTERY_LEVEL", "RIDING_MODE", "IS_RIDING", "TOTAL_MILEAGE", "REMAINING_MILEAGE", "REMAINING_MILEAGE_ALGORITHM",
        "IS_CHARGING", "ENERGY_RECOVERY", "IS_LOCKED", "CURRENT_MILEAGE", "RIDING_TIME", "AVERAGE_SPEED", "HIGHEST_SPEED",
    )

    fun isLogSlot(name: String) = name.startsWith("LOG_")

    /**
     * Milliseconds until [name] is due again, or null for "only once per connection". [stillScale] stretches the
     * intervals of values that only matter while parked (the refresh-rate setting: 1 = fastest); what the ride
     * recording and the connection watchdog rely on is never stretched. [appVisible] is false while the app is in
     * the background (screen showing something else) - [overlayActive] additionally true only then, and only while
     * the floating overlay is actually on screen, since that is the one thing in the background still worth reading
     * for.
     */
    fun intervalMs(name: String, riding: Boolean, tab: PollTab, stillScale: Double = 1.0, appVisible: Boolean = true, overlayActive: Boolean = false): Long? {
        fun s(ms: Long) = (ms * stillScale).toLong()
        val rideOrParked = if (riding) FAST_MS else s(PARKED_MS)
        val tripTabOnScreen = appVisible && (tab == PollTab.OVERVIEW || tab == PollTab.RIDE)
        return when {
            name in once -> null
            name in always -> FAST_MS
            name == "TOTAL_MILEAGE" -> if (riding) FAST_MS else PARKED_MS
            name in restTrip -> if (tripTabOnScreen || overlayActive) rideOrParked else s(SLOW_MS)
            name in dashboardOnlyTrip -> if (tripTabOnScreen) rideOrParked else s(SLOW_MS)
            name == "REMAINING_MILEAGE_ALGORITHM" ->
                if (tripTabOnScreen || (appVisible && tab == PollTab.BATTERY) || overlayActive) rideOrParked else s(SLOW_MS)
            name in batteryLive -> if (appVisible && tab == PollTab.BATTERY) BATTERY_LIVE_MS else s(SLOW_MS)
            name in medium -> s(MEDIUM_MS)
            name in slow || isLogSlot(name) -> s(SLOW_MS)
            else -> s(DEFAULT_MS)
        }
    }
}
