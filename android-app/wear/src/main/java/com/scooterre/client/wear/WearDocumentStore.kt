package com.scooterre.client.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Path prefix :app's WearDocsBridge pushes to - one DataItem per scooter, must match the phone. */
const val DOCS_PATH_PREFIX = "/scooterre/documents/"

/** One document as cached on the watch: always plain JPEG pages (a PDF on the phone is rasterized
 * to JPEG before being sent - see :app's WearDocsBridge - so the watch never needs a PDF renderer). */
data class WearDocument(val id: String, val name: String, val pageCount: Int, val addedMillis: Long)

private const val PREFS_NAME = "wear_documents"

/** Canonical form used as the [WatchState.documents] map key and everywhere else a MAC identifies
 * a scooter's document cache - matches :app's WearDocsBridge/DocumentStore's own directory-name
 * normalization exactly, so a colon-MAC (from KnownDevice) and the no-colon form the phone's push
 * path already uses always resolve to the same key. */
fun normalizeMac(mac: String): String = mac.replace(":", "").uppercase()

/**
 * The watch's own, local, durable copy of each scooter's documents - deliberately a full local
 * cache, not something fetched on demand, since the whole point of this feature is showing
 * registration papers etc. "im Kontrollfall ohne Handy" (a real check, without the phone). Layout
 * mirrors the phone's DocumentStore: `files/documents/<mac without colons>/<id>/page_N.jpg`, plus
 * one JSON list per scooter in SharedPreferences.
 */
class WearDocumentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(mac: String) = "docs_" + normalizeMac(mac)
    private fun scooterDir(mac: String) = File(context.filesDir, "documents/" + normalizeMac(mac))
    private fun docDir(mac: String, id: String) = File(scooterDir(mac), id)

    fun list(mac: String): List<WearDocument> {
        val raw = prefs.getString(key(mac), null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WearDocument(o.getString("id"), o.getString("name"), o.getInt("pageCount"), o.getLong("added"))
        }
    }

    fun pageFile(mac: String, doc: WearDocument, page: Int): File = File(docDir(mac, doc.id), "page_$page.jpg")

    private fun save(mac: String, docs: List<WearDocument>) {
        val array = JSONArray()
        docs.forEach { d ->
            array.put(JSONObject().put("id", d.id).put("name", d.name).put("pageCount", d.pageCount).put("added", d.addedMillis))
        }
        prefs.edit().putString(key(mac), array.toString()).apply()
    }

    /**
     * Replaces the entire local cache for [mac] with what the phone just sent - the phone always
     * pushes its complete, current document list (see WearDocsBridge's doc comment), so a full
     * replace here is what makes a deletion on the phone disappear here too, without needing a
     * separate "delete" message type. [pageBytes] fetches one page's JPEG bytes (from the Data
     * Layer asset) - a document is kept only if every one of its pages was fetched successfully,
     * so a mid-transfer failure never leaves a half-written document behind.
     */
    fun applyManifest(mac: String, manifest: List<WearDocument>, pageBytes: (docId: String, page: Int) -> ByteArray?) {
        scooterDir(mac).deleteRecursively()
        val kept = mutableListOf<WearDocument>()
        manifest.forEach { doc ->
            val dir = docDir(mac, doc.id).apply { mkdirs() }
            var ok = true
            for (page in 0 until doc.pageCount) {
                val bytes = pageBytes(doc.id, page)
                if (bytes == null) {
                    ok = false
                    break
                }
                File(dir, "page_$page.jpg").writeBytes(bytes)
            }
            if (ok) kept += doc else dir.deleteRecursively()
        }
        save(mac, kept)
    }
}

/**
 * Parses one documents DataMap (from either StatusListenerService's passive onDataChanged, or
 * DocumentsScreen's active pull on open - see its doc comment for why both exist) and applies it
 * via [WearDocumentStore.applyManifest], updating [WatchState.documents] to match. Shared by both
 * call sites so there is exactly one place that understands the wire format :app's WearDocsBridge
 * produces.
 */
fun applyDocumentsPush(context: Context, mac: String, map: DataMap) {
    try {
        val manifest = JSONArray(map.getString("manifest") ?: "[]")
        val docs = (0 until manifest.length()).map { i ->
            val o = manifest.getJSONObject(i)
            WearDocument(o.getString("id"), o.getString("name"), o.getInt("pageCount"), o.getLong("addedMillis"))
        }
        val dataClient = Wearable.getDataClient(context)
        val store = WearDocumentStore(context)
        store.applyManifest(mac, docs) { docId, page ->
            val asset = map.getAsset("page_${docId}_$page") ?: return@applyManifest null
            runCatching { Tasks.await(dataClient.getFdForAsset(asset)).inputStream.use { it.readBytes() } }.getOrNull()
        }
        WatchState.documents.value = WatchState.documents.value + (normalizeMac(mac) to store.list(mac))
    } catch (e: Exception) {
        android.util.Log.w("WearDocumentStore", "could not apply a documents push for $mac", e)
    }
}
