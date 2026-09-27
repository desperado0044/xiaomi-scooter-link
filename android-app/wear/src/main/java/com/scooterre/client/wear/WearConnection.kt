package com.scooterre.client.wear

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.SystemClock
import com.scooterre.client.ble.ScooterScanner
import com.scooterre.client.protocol.KnownDevice
import com.scooterre.client.protocol.MiProtocol
import com.scooterre.client.protocol.SecureStore
import com.scooterre.client.protocol.SpecProfiles
import com.scooterre.client.protocol.SpecType
import com.scooterre.client.protocol.encodeValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectPhase { IDLE, SEARCHING, CONNECTING, CONNECTED, FAILED }

data class WearConnectionState(
    val phase: ConnectPhase = ConnectPhase.IDLE,
    val device: KnownDevice? = null,
    val isLocked: Boolean? = null,
    val batteryLevel: Long? = null,
    val remainingKm: Double? = null,
    val hasFindMy: Boolean = false,
)

/**
 * The watch's own, manual, durable direct connection to a scooter - only ever started by an
 * explicit tap (see MainActivity/design notes: the watch must never grab the scooter's single
 * BLE slot on its own). Once connected it stays connected until explicitly stopped, with one
 * reconnect attempt on an unexpected drop, same as the phone's own behavior. Deliberately minimal:
 * just enough properties for the watch's own screen (lock state, battery, remaining range) plus
 * the two actions (lock/unlock, horn/find where the model supports it) - everything else stays
 * the phone's job.
 */
object WearConnection {
    val state = MutableStateFlow(WearConnectionState())

    private var protocol: MiProtocol? = null
    private var scope: CoroutineScope? = null
    private var wanted = false // true while the user wants this connection to stay up

    private const val CONNECT_BUDGET_MS = 20_000L

    fun connect(context: Context, device: KnownDevice) {
        val ltmk = SecureStore(context).loadLtmk(device.mac) ?: run {
            state.value = WearConnectionState(phase = ConnectPhase.FAILED, device = device)
            return
        }
        wanted = true
        scope?.cancel()
        // Switching to a different scooter while one is already connected - the old GATT
        // connection must be explicitly torn down here too, not just the coroutine scope that
        // was watching it, or it leaks (same pattern the phone's connectKnownDevice uses).
        protocol?.dispose()
        protocol = null
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch { runConnection(context.applicationContext, device, ltmk) }
    }

    fun disconnect() {
        wanted = false
        scope?.cancel()
        scope = null
        protocol?.dispose()
        protocol = null
        state.value = WearConnectionState()
    }

    private suspend fun runConnection(context: Context, device: KnownDevice, ltmk: ByteArray) {
        while (wanted) {
            state.value = WearConnectionState(phase = ConnectPhase.SEARCHING, device = device)
            val manager = context.getSystemService(BluetoothManager::class.java)
            val btDevice = manager.adapter.getRemoteDevice(device.mac)
            val scanner = ScooterScanner(context)
            val deadline = SystemClock.elapsedRealtime() + CONNECT_BUDGET_MS
            var connected = false
            while (wanted && SystemClock.elapsedRealtime() < deadline) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                val seen = withTimeoutOrNull(remaining) { scanner.watchForDevice(device.mac).first() }
                if (seen == null) break
                state.value = state.value.copy(phase = ConnectPhase.CONNECTING)
                val p = try {
                    MiProtocol.connect(context, btDevice)
                } catch (e: Exception) {
                    delay(500)
                    continue
                }
                val ok = try {
                    p.login(ltmk)
                } catch (e: Exception) {
                    p.dispose()
                    false
                }
                if (!ok) {
                    delay(500)
                    continue
                }
                protocol = p
                connected = true
                readInitial(device)
                // Waits for this specific connection to die, then loops back to reconnect (once)
                // if the user still wants it connected - same "silent or explicit drop" handling
                // the phone uses.
                p.connectionLost.first()
                protocol = null
                break
            }
            if (!wanted) return
            if (!connected) {
                state.value = state.value.copy(phase = ConnectPhase.FAILED)
                return
            }
            // Connection died while still wanted - one immediate reconnect attempt, then loop.
            state.value = state.value.copy(phase = ConnectPhase.SEARCHING, isLocked = null, batteryLevel = null, remainingKm = null)
        }
    }

    private suspend fun readInitial(device: KnownDevice) {
        val spec = protocol?.requireSpecClient() ?: return
        val profile = SpecProfiles.forModel(device.model)
        val byName = profile.all.associateBy { it.name }
        val hasFindMy = byName.containsKey("A_BLE_FIND_VEHICLE")
        val locked = byName["IS_LOCKED"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val battery = byName["BATTERY_LEVEL"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val remaining = byName["REMAINING_MILEAGE"]?.let { runCatching { spec.get(it) }.getOrNull() }
        state.value = state.value.copy(
            phase = ConnectPhase.CONNECTED,
            hasFindMy = hasFindMy,
            isLocked = (locked?.value as? Long)?.let { it == 1L },
            batteryLevel = battery?.value as? Long,
            // Wire value is hundredths of a km, same as the phone's REMAINING_MILEAGE handling.
            remainingKm = (remaining?.value as? Float)?.let { it * 0.01 },
        )
    }

    /** true if the write succeeded (status 0); the caller is responsible for the battery-level
     * safety check on locking (see MainActivity) - this function just performs the write. */
    suspend fun setLocked(device: KnownDevice, locked: Boolean): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        val property = profile.all.firstOrNull { it.name == "IS_LOCKED" } ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, if (locked) 1L else 0L)) }
        if (status == 0) state.value = state.value.copy(isLocked = locked)
        return status == 0
    }

    suspend fun findMy(device: KnownDevice): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        val property = profile.all.firstOrNull { it.name == "A_BLE_FIND_VEHICLE" } ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, 1L)) }
        return status == 0
    }
}
