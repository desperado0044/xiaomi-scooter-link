package com.scooterre.client.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.scooterre.client.protocol.DeviceRegistry
import com.scooterre.client.protocol.KnownDevice
import com.scooterre.client.protocol.SecureStore
import org.json.JSONObject

/** Paths the phone's WearSync/WearBridge push to - must match the phone app. */
private const val STATUS_PATH = "/scooterre/status"
private const val DEVICE_PATH = "/scooterre/device"
private const val SETTINGS_PATH = "/scooterre/settings"

/**
 * Declared in the manifest with data/message intent-filters, so the system wakes this up on a
 * new item or message even when nothing of ours is running - no background polling of our own.
 */
class StatusListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != STATUS_PATH) continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                WatchState.status.value = RelayStatus(
                    rest = map.getString("rest") ?: "",
                    trip = map.getString("trip") ?: "",
                    battery = map.getString("battery") ?: "",
                    rideTime = map.getString("rideTime") ?: "",
                    standby = map.getBoolean("standby"),
                    updatedAt = map.getLong("updatedAt"),
                )
            }
        } finally {
            dataEvents.release()
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        android.util.Log.i("StatusListener", "onMessageReceived path=${event.path} bytes=${event.data.size}")
        when (event.path) {
            DEVICE_PATH -> handleDevice(event.data)
            SETTINGS_PATH -> handleSettings(event.data)
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("StatusListener", "onCreate")
    }

    private fun handleDevice(payload: ByteArray) {
        try {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val mac = json.getString("mac")
            val ltmk = json.getString("ltmk").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val device = KnownDevice(
                mac = mac,
                model = if (json.isNull("model")) null else json.getString("model"),
                name = if (json.isNull("name")) null else json.getString("name"),
            )
            val registry = DeviceRegistry(this)
            SecureStore(this).saveLtmk(mac, ltmk)
            registry.upsert(device)
            WatchState.devices.value = registry.list()
        } catch (e: Exception) {
            android.util.Log.w("StatusListener", "could not apply the device push from the phone", e)
        }
    }

    private fun handleSettings(payload: ByteArray) {
        try {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val settings = WatchSettings(
                unitsImperial = json.optString("units", "METRIC") == "IMPERIAL",
                languageDe = json.optString("language", "DE") == "DE",
                confirmCritical = json.optBoolean("confirmCritical", false),
                rideTracking = json.optBoolean("rideTracking", true),
            )
            WatchSettingsStore.save(this, settings)
            WatchState.settings.value = settings
        } catch (e: Exception) {
            android.util.Log.w("StatusListener", "could not apply the settings push from the phone", e)
        }
    }
}
