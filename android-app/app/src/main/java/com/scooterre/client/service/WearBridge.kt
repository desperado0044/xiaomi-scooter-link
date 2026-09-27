package com.scooterre.client.service

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.scooterre.client.ui.Lang
import com.scooterre.client.ui.UnitSystem
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** Message paths - must match the watch's own listener service. */
const val WEAR_PATH_DEVICE = "/scooterre/device"
const val WEAR_PATH_SETTINGS = "/scooterre/settings"

/**
 * One-shot pushes to a paired Wear OS companion, if any: the scooter's key/MAC/name ("Mit Uhr
 * verbinden") and the small set of app-level settings the watch also offers standalone. Both are
 * fire-and-forget messages, not a lingering Data Layer item - once the watch has persisted them
 * into its own SecureStore/DeviceRegistry, the message itself is gone. A phone without Play
 * services, or with no watch paired, just gets a harmless no-op (caught, logged, nothing else).
 */
object WearBridge {
    fun pushDevice(context: Context, mac: String, model: String?, name: String?, ltmk: ByteArray, onResult: (Boolean) -> Unit = {}) {
        val json = JSONObject().apply {
            put("mac", mac)
            put("model", model ?: JSONObject.NULL)
            put("name", name ?: JSONObject.NULL)
            put("ltmk", ltmk.joinToString("") { "%02x".format(it) })
        }
        send(context, WEAR_PATH_DEVICE, json, onResult)
    }

    fun pushSettings(context: Context, units: UnitSystem, language: Lang, confirmCritical: Boolean, rideTracking: Boolean) {
        val json = JSONObject().apply {
            put("units", units.name)
            put("language", language.name)
            put("confirmCritical", confirmCritical)
            put("rideTracking", rideTracking)
        }
        send(context, WEAR_PATH_SETTINGS, json)
    }

    private fun send(context: Context, path: String, json: JSONObject, onResult: (Boolean) -> Unit = {}) {
        try {
            val payload = json.toString().toByteArray(StandardCharsets.UTF_8)
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes ->
                    android.util.Log.i("WearBridge", "connectedNodes for $path: " + nodes.map { it.displayName + "/" + it.id + "/nearby=" + it.isNearby })
                    if (nodes.isEmpty()) {
                        onResult(false)
                        return@addOnSuccessListener
                    }
                    var remaining = nodes.size
                    var anyOk = false
                    nodes.forEach { node ->
                        Wearable.getMessageClient(context).sendMessage(node.id, path, payload)
                            .addOnSuccessListener {
                                android.util.Log.i("WearBridge", "sendMessage $path -> ${node.displayName} ok")
                                anyOk = true
                                remaining--
                                if (remaining == 0) onResult(anyOk)
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.w("WearBridge", "sendMessage $path -> ${node.displayName} failed", e)
                                remaining--
                                if (remaining == 0) onResult(anyOk)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("WearBridge", "connectedNodes failed", e)
                    onResult(false)
                }
        } catch (e: Exception) {
            android.util.Log.w("WearBridge", "could not reach a paired watch", e)
            onResult(false)
        }
    }
}
