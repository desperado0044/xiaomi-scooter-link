package com.scooterre.client.wear

import android.content.Context

private const val PREFS_NAME = "wear_settings"
private const val KEY_UNITS_IMPERIAL = "units_imperial"
private const val KEY_LANGUAGE_DE = "language_de"
private const val KEY_CONFIRM_CRITICAL = "confirm_critical"
private const val KEY_RIDE_TRACKING = "ride_tracking"

/** Persists [WatchSettings] locally on the watch, so they survive a restart and work standalone -
 * not shared storage with the phone, just mirrored into it by StatusListenerService/settings UI. */
object WatchSettingsStore {
    fun load(context: Context): WatchSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WatchSettings(
            unitsImperial = prefs.getBoolean(KEY_UNITS_IMPERIAL, false),
            languageDe = prefs.getBoolean(KEY_LANGUAGE_DE, true),
            confirmCritical = prefs.getBoolean(KEY_CONFIRM_CRITICAL, false),
            rideTracking = prefs.getBoolean(KEY_RIDE_TRACKING, true),
        )
    }

    fun save(context: Context, settings: WatchSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_UNITS_IMPERIAL, settings.unitsImperial)
            .putBoolean(KEY_LANGUAGE_DE, settings.languageDe)
            .putBoolean(KEY_CONFIRM_CRITICAL, settings.confirmCritical)
            .putBoolean(KEY_RIDE_TRACKING, settings.rideTracking)
            .apply()
    }
}
