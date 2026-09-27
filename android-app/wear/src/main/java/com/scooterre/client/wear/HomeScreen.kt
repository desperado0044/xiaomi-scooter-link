package com.scooterre.client.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * Home: the phone's relay status (compact) plus one entry per synced scooter - tapping one opens
 * [DeviceScreen]. Kept to just names here on purpose (see the project's Wear OS navigation notes):
 * this screen's job is picking a scooter, not showing everything about it.
 */
@Composable
fun HomeScreen(onOpenDevice: (String) -> Unit, onOpenApp: () -> Unit) {
    val devices by WatchState.devices.collectAsState()
    val status by WatchState.status.collectAsState()

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
                    item { ListHeader { Text("Scooter Link") } }
                    item { RelayCard(status) }

                    if (devices.isEmpty()) {
                        item {
                            Card(onClick = {}) {
                                Text("Noch nicht eingerichtet", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Einrichtung erfolgt am Handy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item { ListHeader { Text("Scooter") } }
                        devices.forEach { device ->
                            item {
                                Button(onClick = { onOpenDevice(device.mac) }) {
                                    Text(device.name ?: device.mac)
                                }
                            }
                        }
                    }

                    item { Button(onClick = onOpenApp) { Text("⚙ App") } }
                }
            }
        }
    }
}

@Composable
private fun RelayCard(status: RelayStatus?) {
    Card(onClick = {}) {
        Text("Vom Handy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == null) {
            Text("Keine Daten", style = MaterialTheme.typography.bodyMedium)
        } else {
            if (status.standby) Text("Ruhezustand", style = MaterialTheme.typography.bodyMedium)
            val line1 = listOfNotNull(
                status.battery.takeIf { it.isNotEmpty() }?.let { "Akku $it" },
                status.rest.takeIf { it.isNotEmpty() }?.let { "Rest $it" },
            ).joinToString(" · ")
            if (line1.isNotEmpty()) Text(line1, style = MaterialTheme.typography.bodyMedium)
            val line2 = listOfNotNull(
                status.trip.takeIf { it.isNotEmpty() }?.let { "Strecke $it" },
                status.rideTime.takeIf { it.isNotEmpty() }?.let { "Fahrzeit $it" },
            ).joinToString(" · ")
            if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
