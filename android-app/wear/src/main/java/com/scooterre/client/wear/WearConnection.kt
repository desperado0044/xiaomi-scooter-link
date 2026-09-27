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
    // Theft alarm (LOCK_WARNING): a standing preference, not a one-shot trigger - once armed, it
    // re-activates automatically every time the scooter is locked and deactivates automatically
    // on unlock (confirmed live 2026-09-27). Shown as a plain toggle at all times, same as
    // isLocked - whether writing it while unlocked actually sticks was never tested, so nothing
    // here should assume an answer either way; the read-back after a write is what tells the
    // truth, not a guess baked into when the control is enabled.
    val alarmArmed: Boolean? = null,
    val batteryLevel: Long? = null,
    val remainingKm: Double? = null,
    val hasFindMy: Boolean = false,
    // Every BOOL-typed vehicle setting the model's profile has (e.g. TAIL_LIGHT_IS_ON,
    // A_BRAKE_LIGHT_LINKAGE), keyed by property name. Deliberately BOOL-only for now: a UINT8
    // "settings" property (RIDING_MODE, ENERGY_RECOVERY, ...) has model-specific enum meanings that
    // live in the phone's own string tables (ui/Strings.kt) - showing/cycling those correctly here
    // too is a later step, not a guess worth shipping now.
    val boolSettings: Map<String, Boolean> = emptyMap(),
    // UINT8 "cycle" settings (ENERGY_RECOVERY, ATMOSPHERE_LIGHT, MILEAGE_UNIT - RIDING_MODE is
    // deliberately excluded, see EXCLUDED_VEHICLE_SETTINGS) - fixed value sets, current raw value
    // per property name. Allowed values themselves come from the model's own
    // SpecProfile.cycleValues (shared with the phone via :core, not duplicated).
    val cycleSettings: Map<String, Long> = emptyMap(),
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

    // IS_LOCKED is handled separately (see setLocked), not part of the vehicle-settings screen.
    // BLUETOOTH_SEARCH_ON's actual real-world effect was never confirmed anywhere in this project
    // (no explanatory hint in the phone app either) - tapping it live showed no observable change,
    // so it's left out here rather than offering a toggle whose effect isn't actually known.
    // RIDING_MODE is excluded from the watch specifically (not from the phone) - it has a physical
    // control on the scooter itself, confirmed by the user directly, so it doesn't need a
    // duplicate control here.
    private val EXCLUDED_VEHICLE_SETTINGS = setOf("IS_LOCKED", "BLUETOOTH_SEARCH_ON", "RIDING_MODE")

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
        // BLUETOOTH_CAR_SEARCH is this model family's real "find my scooter" trigger (write-only -
        // confirmed live 2026-09-19, see :core's SpecClient.kt - beeps/flashes on SET); the base
        // 5 Pro/5 Max table has no A_BLE_FIND_VEHICLE at all, that's Family-A-only. Checking for
        // either here is what makes this one Hupe/Suchen button work across both families.
        val hasFindMy = byName.containsKey("A_BLE_FIND_VEHICLE") || byName.containsKey("BLUETOOTH_CAR_SEARCH")
        val locked = byName["IS_LOCKED"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val alarm = byName["LOCK_WARNING"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val battery = byName["BATTERY_LEVEL"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val remaining = byName["REMAINING_MILEAGE"]?.let { runCatching { spec.get(it) }.getOrNull() }
        val boolSettings = profile.tabSettings
            .filter { it !in EXCLUDED_VEHICLE_SETTINGS }
            .mapNotNull { name -> byName[name]?.takeIf { it.type == SpecType.BOOL } }
            .mapNotNull { property ->
                val result = runCatching { spec.get(property) }.getOrNull() ?: return@mapNotNull null
                val value = (result.value as? Long)?.let { it == 1L } ?: return@mapNotNull null
                property.name to value
            }
            .toMap()
        // UINT8 cycle settings (ENERGY_RECOVERY, ATMOSPHERE_LIGHT, MILEAGE_UNIT) - see
        // WearConnectionState.cycleSettings's doc comment.
        val cycleSettings = profile.tabSettings
            .filter { it !in EXCLUDED_VEHICLE_SETTINGS && it in profile.cycleProperties }
            .mapNotNull { name -> byName[name] }
            .mapNotNull { property ->
                val result = runCatching { spec.get(property) }.getOrNull() ?: return@mapNotNull null
                val value = result.value as? Long ?: return@mapNotNull null
                property.name to value
            }
            .toMap()
        state.value = state.value.copy(
            phase = ConnectPhase.CONNECTED,
            hasFindMy = hasFindMy,
            isLocked = (locked?.value as? Long)?.let { it == 1L },
            alarmArmed = (alarm?.value as? Long)?.let { it == 1L },
            batteryLevel = battery?.value as? Long,
            // Wire value is hundredths of a km, same as the phone's REMAINING_MILEAGE handling.
            remainingKm = (remaining?.value as? Float)?.let { it * 0.01 },
            boolSettings = boolSettings,
            cycleSettings = cycleSettings,
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

    /** true if the write succeeded (status 0) - see [WearConnectionState.alarmArmed]'s doc comment
     * for what this actually does and what is and isn't confirmed about it. */
    suspend fun setAlarm(device: KnownDevice, armed: Boolean): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        val property = profile.all.firstOrNull { it.name == "LOCK_WARNING" } ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, if (armed) 1L else 0L)) }
        if (status == 0) state.value = state.value.copy(alarmArmed = armed)
        return status == 0
    }

    /** Writes one BOOL vehicle setting (see [WearConnectionState.boolSettings]) and re-reads it to
     * confirm, same pattern as [setLocked]. */
    suspend fun setBoolSetting(device: KnownDevice, name: String, value: Boolean): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        val property = profile.all.firstOrNull { it.name == name } ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, if (value) 1L else 0L)) }
        if (status == 0) state.value = state.value.copy(boolSettings = state.value.boolSettings + (name to value))
        return status == 0
    }

    /** Writes one UINT8 cycle setting (see [WearConnectionState.cycleSettings]) and re-reads it to
     * confirm, same pattern as [setBoolSetting]. The caller picks `value` from the model's own
     * `SpecProfile.cycleValues[name]` - this function does not validate it against that list, same
     * as the phone's own CycleButtons (trusts the caller to only offer allowed values). */
    suspend fun setCycleSetting(device: KnownDevice, name: String, value: Long): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        val property = profile.all.firstOrNull { it.name == name } ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.UINT8, value)) }
        if (status == 0) state.value = state.value.copy(cycleSettings = state.value.cycleSettings + (name to value))
        return status == 0
    }

    suspend fun findMy(device: KnownDevice): Boolean {
        val spec = protocol?.requireSpecClient() ?: return false
        val profile = SpecProfiles.forModel(device.model)
        // Family A models have A_BLE_FIND_VEHICLE; the base 5 Pro/5 Max family has
        // BLUETOOTH_CAR_SEARCH instead (write-only, no GET) - see readInitial's hasFindMy comment.
        val property = profile.all.firstOrNull { it.name == "A_BLE_FIND_VEHICLE" }
            ?: profile.all.firstOrNull { it.name == "BLUETOOTH_CAR_SEARCH" }
            ?: return false
        val status = withContext(Dispatchers.IO) { spec.set(property, encodeValue(SpecType.BOOL, 1L)) }
        return status == 0
    }
}
