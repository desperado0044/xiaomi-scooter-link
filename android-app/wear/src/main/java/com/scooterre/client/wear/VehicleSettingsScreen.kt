package com.scooterre.client.wear

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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.scooterre.client.protocol.SpecProfiles
import kotlinx.coroutines.launch

/**
 * The vehicle settings the connected model's profile has: a toggle per BOOL setting (e.g.
 * Rücklicht, Bremslicht-Verknüpfung), and a cycle-button per UINT8 "fixed value set" setting
 * (Rekuperation, Ambientebeleuchtung, Streckeneinheit). RIDING_MODE is deliberately excluded - it
 * has a physical control on the scooter itself (confirmed by the user). Region-sensitive BOOL
 * settings (Tempomat, Rücklicht) ask for confirmation before being turned on, same as the phone's
 * RegionWarningDialog. Only reachable while actually connected - there is nothing to show or
 * change otherwise.
 */
@Composable
fun VehicleSettingsScreen(mac: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by WatchState.devices.collectAsState()
    val connection by WearConnection.state.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    // Set while a BOOL setting in the model's regionSensitiveProperties is about to be turned ON -
    // same legal-notice-before-enabling gate as the phone's RegionWarningDialog (CRUISE_IS_ON,
    // TAIL_LIGHT_IS_ON), just as a full-screen confirm instead of a dialog (this codebase's
    // established Wear pattern - see DeviceScreen's DisconnectConfirmScreen).
    var pendingRegionConfirm by remember { mutableStateOf<String?>(null) }

    val device = devices.firstOrNull { it.mac == mac }
    val connected = device != null && connection.device?.mac == mac && connection.phase == ConnectPhase.CONNECTED
    val profile = device?.let { SpecProfiles.forModel(it.model) }

    if (pendingRegionConfirm != null && device != null) {
        val name = pendingRegionConfirm!!
        RegionWarningConfirmScreen(
            warningText = regionWarningTextDe(name),
            onConfirm = {
                pendingRegionConfirm = null
                scope.launch {
                    if (!WearConnection.setBoolSetting(device, name, true)) notice = "Ändern fehlgeschlagen"
                }
            },
            onCancel = { pendingRegionConfirm = null },
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
                    item { ListHeader { Text("Fahrzeug") } }
                    if (device == null || profile == null || !connected) {
                        item { Text("Nicht verbunden", style = MaterialTheme.typography.bodyMedium) }
                    } else if (connection.boolSettings.isEmpty() && connection.cycleSettings.isEmpty()) {
                        item { Text("Keine Einstellungen verfügbar", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        connection.boolSettings.forEach { (name, value) ->
                            item {
                                Button(
                                    colors = if (value) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                                    onClick = {
                                        val turningOn = !value
                                        if (turningOn && name in profile.regionSensitiveProperties) {
                                            pendingRegionConfirm = name
                                        } else {
                                            scope.launch {
                                                if (!WearConnection.setBoolSetting(device, name, turningOn)) {
                                                    notice = "Ändern fehlgeschlagen"
                                                }
                                            }
                                        }
                                    },
                                ) { Text(vehicleSettingLabel(name) + ": " + if (value) "An" else "Aus") }
                            }
                        }
                        connection.cycleSettings.forEach { (name, value) ->
                            val options = profile.cycleValues[name]
                            if (options != null && options.isNotEmpty()) {
                                item {
                                    Button(
                                        colors = ButtonDefaults.filledTonalButtonColors(),
                                        onClick = {
                                            val currentIndex = options.indexOf(value).let { if (it < 0) 0 else it }
                                            val next = options[(currentIndex + 1) % options.size]
                                            scope.launch {
                                                if (!WearConnection.setCycleSetting(device, name, next)) {
                                                    notice = "Ändern fehlgeschlagen"
                                                }
                                            }
                                        },
                                    ) { Text(vehicleSettingLabel(name) + ": " + cycleValueLabelDe(name, value)) }
                                }
                            }
                        }
                    }
                    notice?.let { text ->
                        item { Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/** Full-screen legal-notice confirm before turning a region-sensitive BOOL setting ON - same
 * proven pattern as DeviceScreen's DisconnectConfirmScreen (a dedicated Wear Compose Material3
 * AlertDialog was avoided here since it's unverified against this project's pinned compose-material3
 * 1.5.0 version; this full-screen swap is already known to work). Swipe-to-dismiss cancels, same as
 * tapping "Nein". */
@Composable
private fun RegionWarningConfirmScreen(warningText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
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
                    item { ListHeader { Text("Rechtlicher Hinweis") } }
                    item { Text(warningText, style = MaterialTheme.typography.bodyMedium) }
                    item { Button(onClick = onConfirm) { Text("Ich bestätige, aktivieren") } }
                    item { Button(colors = ButtonDefaults.filledTonalButtonColors(), onClick = onCancel) { Text("Abbrechen") } }
                }
            }
        }
    }
}

/** German-only, watch-sized copy of the phone's regionWarning() text (ui/Strings.kt on :app) - not
 * shared via :core since it's UI wording, not a protocol fact; kept word-for-word identical to the
 * phone's German text so the two apps never disagree on what they're asking the user to confirm. */
private fun regionWarningTextDe(propertyName: String): String = when (propertyName) {
    "CRUISE_IS_ON" -> "Der Tempomat ist nicht in jedem Land offiziell freigeschaltet (z.B. in " +
        "Deutschland nicht). Diese App kann nicht wissen, wo du unterwegs bist oder was dort " +
        "erlaubt ist - das musst du selbst prüfen. Mit dem Aktivieren bestätigst du, dass du die " +
        "Verantwortung dafür übernimmst."
    "TAIL_LIGHT_IS_ON" -> "Ein funktionierendes, eingeschaltetes Rücklicht ist in praktisch allen " +
        "EU-Ländern beim Fahren im Straßenverkehr gesetzlich vorgeschrieben. Schalte es nur aus, " +
        "wenn der Scooter gerade nicht im Verkehr genutzt wird. Mit dem Aktivieren bestätigst du, " +
        "dass du die Verantwortung dafür übernimmst."
    else -> "Mit dem Aktivieren bestätigst du, dass du die Verantwortung dafür übernimmst."
}

/** German-only labels for the watch's cycle-setting buttons - mirrors the phone's cycleLabel()
 * (ui/Strings.kt), German side only, for the three cycle properties the watch actually shows
 * (RIDING_MODE is excluded here, see EXCLUDED_VEHICLE_SETTINGS). */
private fun cycleValueLabelDe(propertyName: String, value: Long): String = when (propertyName) {
    "ENERGY_RECOVERY" -> mapOf(30L to "Schwach", 60L to "Mittel", 90L to "Stark")[value] ?: value.toString()
    "ATMOSPHERE_LIGHT" -> mapOf(0L to "Aus", 1L to "An", 2L to "Aktiv")[value] ?: value.toString()
    "MILEAGE_UNIT" -> mapOf(1L to "km", 0L to "mi")[value] ?: value.toString()
    else -> value.toString()
}

/** Short, watch-sized labels for the property names this screen can show - not the phone's full
 * translation table (ui/Strings.kt), just enough to not show raw property constants here. Falls
 * back to the raw name for anything not listed, rather than guessing a translation. */
private fun vehicleSettingLabel(name: String): String = when (name) {
    "TAIL_LIGHT_IS_ON" -> "Rücklicht"
    "A_BRAKE_LIGHT_LINKAGE" -> "Bremslicht"
    "A_TCS_SWITCH", "TCS" -> "Traktionskontrolle"
    "A_HEADLIGHT_SWITCH" -> "Scheinwerfer"
    "A_DEVICE_FOUND_SWITCH", "A_BLE_FIND_SWICH" -> "Auffindbar"
    "A_ENVIRONMENTAL_LIGHTS" -> "Umgebungslicht"
    "A_E_ABS" -> "ABS"
    "A_AUTO_LOCK" -> "Auto-Sperre"
    "A_AUTOMATIC_SHUTDOWN" -> "Auto-Abschaltung"
    "A_ABNORMAL_REPORTING" -> "Störungsmeldung"
    "A_CHARGING" -> "Laden"
    "CRUISE_IS_ON" -> "Tempomat"
    "ASR_IS_ON" -> "ASR"
    "AUTO_LIGHT" -> "Auto-Licht"
    "INTELLIGENT_DOWNHILL" -> "Bergabfahrhilfe"
    "HILL_PARKING" -> "Berg-Parkbremse"
    "ENERGY_RECOVERY" -> "Rekuperation"
    "ATMOSPHERE_LIGHT" -> "Ambientebeleuchtung"
    "MILEAGE_UNIT" -> "Streckeneinheit"
    else -> name
}
