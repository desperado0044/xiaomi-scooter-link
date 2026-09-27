package com.scooterre.client.wear

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

private const val MIN_BATTERY_TO_LOCK = 40

private fun watchBatteryPercent(context: Context): Int {
    val bm = context.getSystemService(BatteryManager::class.java)
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
}

/** One scooter: connect/disconnect (manual, durable direct connection - see WearConnection's own
 * doc comment) plus its live values and actions once connected.
 *
 * Back/swipe-to-dismiss while actively connected asks first ("Scooter trennen?") instead of just
 * leaving the connection running invisibly in the background - "No" cancels the back navigation
 * and stays right here, "Yes" disconnects and returns to the selection screen. Not connected (or
 * mid-attempt only briefly): back behaves normally, nothing to confirm. */
@Composable
fun DeviceScreen(mac: String, onDisconnectedBack: () -> Unit, onOpenSettings: (String) -> Unit, onOpenDocuments: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by WatchState.devices.collectAsState()
    val connection by WearConnection.state.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmingDisconnect by remember { mutableStateOf(false) }

    val device = devices.firstOrNull { it.mac == mac }
    if (device == null) {
        // Removed from the phone's device list between opening this screen and now - rare, but
        // not a crash: nothing to show or act on any more.
        MaterialTheme { AppScaffold { ScreenScaffold { Text("Nicht mehr verfügbar") } } }
        return
    }

    val connectedToThis = connection.device?.mac == device.mac
    val isActive = connectedToThis &&
        connection.phase in listOf(ConnectPhase.SEARCHING, ConnectPhase.CONNECTING, ConnectPhase.CONNECTED)

    BackHandler(enabled = isActive) { confirmingDisconnect = true }

    if (confirmingDisconnect) {
        DisconnectConfirmScreen(
            onConfirm = {
                WearConnection.disconnect()
                confirmingDisconnect = false
                onDisconnectedBack()
            },
            onCancel = { confirmingDisconnect = false },
        )
        return
    }

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { ListHeader { Text(device.name ?: device.mac) } }

                    item {
                        Button(
                            onClick = {
                                notice = null
                                if (isActive) WearConnection.disconnect() else WearConnection.connect(context, device)
                            },
                            colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            Text(connectionLabel(connectedToThis, connection.phase))
                        }
                    }

                    item {
                        // Always reachable, regardless of connection state - the whole point is
                        // showing registration papers etc. "im Kontrollfall ohne Handy", which has
                        // nothing to do with whether the scooter itself is currently BLE-connected.
                        Button(
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            onClick = { onOpenDocuments(device.mac) },
                        ) { Text("Dokumente") }
                    }

                    if (connectedToThis && connection.phase == ConnectPhase.CONNECTED) {
                        item {
                            Card(onClick = {}) {
                                connection.batteryLevel?.let { Text("Akku: $it %", style = MaterialTheme.typography.bodyMedium) }
                                connection.remainingKm?.let {
                                    Text("Rest: ${"%.1f".format(it)} km", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        item {
                            val isLocked = connection.isLocked == true
                            Button(onClick = {
                                scope.launch {
                                    if (!isLocked) {
                                        // Blocked below MIN_BATTERY_TO_LOCK so a dying watch (with
                                        // no phone around) can never lock the scooter and then be
                                        // unable to undo it.
                                        val battery = watchBatteryPercent(context)
                                        if (battery < MIN_BATTERY_TO_LOCK) {
                                            notice = "Sperren nicht möglich: Uhr-Akku unter $MIN_BATTERY_TO_LOCK%"
                                        } else if (!WearConnection.setLocked(device, true)) {
                                            notice = "Sperren fehlgeschlagen"
                                        }
                                    } else {
                                        if (!WearConnection.setLocked(device, false)) notice = "Entsperren fehlgeschlagen"
                                    }
                                }
                            }) {
                                Text(if (isLocked) "Entsperren" else "Sperren")
                            }
                        }
                        item {
                            // Directly under Sperren/Entsperren, per design: a plain, always-usable
                            // toggle - no lock-state gating (LOCK_WARNING is a standing preference
                            // that's re-applied on every lock, not a one-shot action; see
                            // WearConnectionState.alarmArmed's doc comment).
                            val armed = connection.alarmArmed == true
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (!WearConnection.setAlarm(device, !armed)) notice = "Alarm ändern fehlgeschlagen"
                                    }
                                },
                                colors = if (armed) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                            ) {
                                Text(if (armed) "Alarm: An" else "Alarm: Aus")
                            }
                        }
                        if (connection.hasFindMy) {
                            item {
                                Button(
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    onClick = { scope.launch { if (!WearConnection.findMy(device)) notice = "Hupe fehlgeschlagen" } },
                                ) { Text("Hupe/Suchen") }
                            }
                        }
                        item {
                            Button(
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                onClick = { onOpenSettings(device.mac) },
                            ) { Text("Fahrzeug-Einstellungen") }
                        }
                    } else if (connectedToThis && connection.phase == ConnectPhase.FAILED) {
                        item { Text("Nicht erreichbar", style = MaterialTheme.typography.bodyMedium) }
                    }
                    notice?.let { text ->
                        item { Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectConfirmScreen(onConfirm: () -> Unit, onCancel: () -> Unit) {
    // Also answers the physical back/swipe gesture while this is up - swiping away here must mean
    // "cancel", the same as tapping "Nein", never a silent third outcome.
    BackHandler(onBack = onCancel)
    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Text("Scooter trennen?", style = MaterialTheme.typography.titleMedium) }
                    item { Button(onClick = onConfirm) { Text("Ja") } }
                    item { Button(colors = ButtonDefaults.filledTonalButtonColors(), onClick = onCancel) { Text("Nein") } }
                }
            }
        }
    }
}

private fun connectionLabel(connectedToThis: Boolean, phase: ConnectPhase): String {
    if (!connectedToThis) return "Verbinden"
    return when (phase) {
        ConnectPhase.SEARCHING -> "Suche..."
        ConnectPhase.CONNECTING -> "Verbinde..."
        ConnectPhase.CONNECTED -> "Trennen"
        ConnectPhase.FAILED -> "Erneut verbinden"
        ConnectPhase.IDLE -> "Verbinden"
    }
}
