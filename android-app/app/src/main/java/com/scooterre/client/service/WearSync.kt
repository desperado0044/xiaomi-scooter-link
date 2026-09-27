package com.scooterre.client.service

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Data-item path the watch's StatusListenerService listens for - keep this in sync with :wear. */
const val WEAR_STATUS_PATH = "/scooterre/status"

/**
 * Pushes [OverlayBus.data] (rest, trip, battery, ride time, standby) to a paired Wear OS
 * companion, if any is present - a no-op, cheap check when there is none. Sampled instead of
 * pushed on every update: the read schedule can update this several times a minute, but the
 * watch only needs a fresh-enough number, not every intermediate reading (battery/network cost
 * on both ends). Started/stopped alongside [ConnectionService], so it only runs while actually
 * connected to a scooter - never a background poll of its own.
 */
class WearSync(private val context: Context, private val scope: CoroutineScope) {
    private var job: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        job = scope.launch {
            OverlayBus.data.sample(SAMPLE_MS).collect { push(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Tells the watch the connection ended, so it doesn't keep showing a stale number.
        push(OverlayData())
    }

    private fun push(data: OverlayData) {
        try {
            val request = PutDataMapRequest.create(WEAR_STATUS_PATH).apply {
                dataMap.putString("rest", data.rest)
                dataMap.putString("trip", data.trip)
                dataMap.putString("battery", data.battery)
                dataMap.putString("rideTime", data.rideTime)
                dataMap.putBoolean("standby", data.standby)
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
        } catch (e: Exception) {
            // No Play services / no paired watch / whatever else - the phone app works the same either way.
            android.util.Log.w("WearSync", "could not push status to the watch", e)
        }
    }

    companion object {
        private const val SAMPLE_MS = 4_000L
    }
}
