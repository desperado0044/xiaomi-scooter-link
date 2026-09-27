package com.scooterre.client.service

import android.content.Context
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.scooterre.client.protocol.DocumentStore
import org.json.JSONArray
import org.json.JSONObject

/** Path prefix - one DataItem per scooter, "/scooterre/documents/<mac without colons>". Must
 * match the watch's own listener service. */
private const val WEAR_DOCS_PATH_PREFIX = "/scooterre/documents/"

/**
 * Pushes one scooter's documents (registration papers, insurance confirmation, ...) to a paired
 * Wear OS companion, so they're shown "im Kontrollfall ohne Handy" - a real check by police or
 * similar, where waiting for the phone isn't an option. Unlike [WearBridge]'s device/settings
 * push (a fire-and-forget message), this uses a Data Layer DataItem: the platform itself
 * guarantees eventual delivery even if the watch is unreachable right now (asleep, out of
 * Bluetooth range, screen off) - no polling or retry logic needed here, the system re-delivers
 * once the watch reconnects. The watch also actively re-reads the current DataItem when its
 * documents screen opens (see DocumentsScreen.kt on :wear), as a second, independent path to the
 * same up-to-date state.
 *
 * A PDF document is rasterized to one JPEG per page before sending (same rendering
 * ui/DocumentViewer.kt uses to display a PDF) - the watch only ever handles plain images, no PDF
 * renderer needed there. Always sends the *complete* current document list for the scooter, not
 * a diff: the watch replaces its whole local cache for that scooter with what it receives, so a
 * deleted document disappears there too without needing a separate "delete" message type.
 */
object WearDocsBridge {
    fun pathFor(mac: String) = WEAR_DOCS_PATH_PREFIX + mac.replace(":", "").uppercase()

    fun pushDocuments(context: Context, mac: String) {
        try {
            val store = DocumentStore(context)
            val docs = store.list(mac)
            val manifest = JSONArray()
            val request = PutDataMapRequest.create(pathFor(mac)).apply {
                docs.forEach { doc ->
                    val pageCount = store.pageCount(mac, doc)
                    manifest.put(
                        JSONObject().apply {
                            put("id", doc.id)
                            put("name", doc.name)
                            put("addedMillis", doc.addedMillis)
                            put("pageCount", pageCount)
                        },
                    )
                    for (page in 0 until pageCount) {
                        val bytes = store.readPageJpeg(mac, doc, page)
                        dataMap.putAsset("page_${doc.id}_$page", Asset.createFromBytes(bytes))
                    }
                }
                dataMap.putString("manifest", manifest.toString())
                dataMap.putLong("updatedAt", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request)
        } catch (e: Exception) {
            // No Play services / no paired watch / a page failed to render - the phone app's own
            // documents feature is unaffected either way, this is best-effort background sync.
            android.util.Log.w("WearDocsBridge", "could not push documents for $mac to the watch", e)
        }
    }
}
