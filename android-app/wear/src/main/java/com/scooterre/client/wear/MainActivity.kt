package com.scooterre.client.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

private const val MIN_BATTERY_TO_LOCK = 40

/**
 * Entry point / device selection: "Mit Handy verbinden" (relay, the normal case - shows whatever
 * the phone last sent) plus one entry per scooter the phone has pushed a key for. Tapping a
 * scooter starts/stops the watch's own direct connection to it (manual, durable - see
 * WearConnection's doc comment for why it never connects on its own).
 */
class MainActivity : ComponentActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureBluetoothPermissions()
        setContent {
            SelectionScreen()
        }
    }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }
}

private fun watchBatteryPercent(context: Context): Int {
    val bm = context.getSystemService(BatteryManager::class.java)
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
}

@Composable
private fun SelectionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by WatchState.devices.collectAsState()
    val status by WatchState.status.collectAsState()
    val connection by WearConnection.state.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) {
                TransformingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { ListHeader { Text("Scooter Link") } }
                    item { Button(onClick = { notice = null }) { Text("Mit Handy verbinden") } }
                    val s = status
                    if (s == null) {
                        item { Text("Keine Daten vom Handy") }
                    } else {
                        if (s.standby) item { Text("Ruhezustand") }
                        if (s.battery.isNotEmpty()) item { Text("Akku: " + s.battery) }
                        if (s.rest.isNotEmpty()) item { Text("Rest: " + s.rest) }
                        if (s.trip.isNotEmpty()) item { Text("Strecke: " + s.trip) }
                        if (s.rideTime.isNotEmpty()) item { Text("Fahrzeit: " + s.rideTime) }
                    }

                    if (devices.isEmpty()) {
                        item { Text("Noch nicht eingerichtet") }
                        item { Text("Einrichtung erfolgt am Handy") }
                    } else {
                        item { ListHeader { Text("Scooter") } }
                        devices.forEach { device ->
                            val connectedToThis = connection.device?.mac == device.mac
                            item {
                                val isActive = connectedToThis &&
                                    connection.phase in listOf(ConnectPhase.SEARCHING, ConnectPhase.CONNECTING, ConnectPhase.CONNECTED)
                                Button(onClick = {
                                    notice = null
                                    if (isActive) WearConnection.disconnect() else WearConnection.connect(context, device)
                                }) {
                                    Text((device.name ?: device.mac) + connectionSuffix(connectedToThis, connection.phase))
                                }
                            }
                            if (connectedToThis && connection.phase == ConnectPhase.CONNECTED) {
                                item {
                                    connection.batteryLevel?.let { Text("Akku: $it %") }
                                }
                                item {
                                    connection.remainingKm?.let { Text("Rest: ${"%.1f".format(it)} km") }
                                }
                                item {
                                    // Unknown state (isLocked == null, e.g. the initial read failed)
                                    // is treated the same as "unlocked" here - consistently for both
                                    // the label and what the tap actually does, so they never disagree.
                                    val isLocked = connection.isLocked == true
                                    Button(onClick = {
                                        scope.launch {
                                            if (!isLocked) {
                                                // Locking is blocked below MIN_BATTERY_TO_LOCK so a dying
                                                // watch (with no phone around) can never lock you out.
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
                                if (connection.hasFindMy) {
                                    item {
                                        Button(onClick = {
                                            scope.launch { if (!WearConnection.findMy(device)) notice = "Hupe fehlgeschlagen" }
                                        }) { Text("Hupe/Suchen") }
                                    }
                                }
                            } else if (connectedToThis && connection.phase == ConnectPhase.FAILED) {
                                item { Text("Nicht erreichbar") }
                            }
                        }
                    }
                    notice?.let { text -> item { Text(text) } }

                    item { ListHeader { Text("App") } }
                    item {
                        Button(onClick = {
                            scope.launch { WearUpdate.checkAndInstall(context) { updateStatus = it } }
                        }) { Text("Update suchen") }
                    }
                    updateStatus?.let { text -> item { Text(text) } }
                }
            }
        }
    }
}

private fun connectionSuffix(connectedToThis: Boolean, phase: ConnectPhase): String {
    if (!connectedToThis) return ""
    return when (phase) {
        ConnectPhase.SEARCHING -> " (suche...)"
        ConnectPhase.CONNECTING -> " (verbinde...)"
        ConnectPhase.CONNECTED -> " (verbunden)"
        ConnectPhase.FAILED -> ""
        ConnectPhase.IDLE -> ""
    }
}
