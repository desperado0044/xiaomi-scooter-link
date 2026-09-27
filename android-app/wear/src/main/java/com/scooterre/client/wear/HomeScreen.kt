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
        val line1 = status?.let {
            listOfNotNull(
                it.battery.takeIf { b -> b.isNotEmpty() }?.let { b -> "Akku $b" },
                it.rest.takeIf { r -> r.isNotEmpty() }?.let { r -> "Rest $r" },
            ).joinToString(" · ")
        }.orEmpty()
        val line2 = status?.let {
            listOfNotNull(
                it.trip.takeIf { t -> t.isNotEmpty() }?.let { t -> "Strecke $t" },
                it.rideTime.takeIf { rt -> rt.isNotEmpty() }?.let { rt -> "Fahrzeit $rt" },
            ).joinToString(" · ")
        }.orEmpty()
        when {
            // Never received anything from the phone at all (e.g. before the first "Mit Uhr
            // verbinden"/relay push ever arrives) - genuinely different from the phone having
            // pushed an explicit "not connected right now" below.
            status == null -> Text("Noch keine Daten vom Handy", style = MaterialTheme.typography.bodyMedium)
            status.standby -> Text("Ruhezustand", style = MaterialTheme.typography.bodyMedium)
            // WearSync pushes this empty RelayStatus explicitly when the phone disconnects from a
            // scooter (see its stop()) - previously showed nothing at all here, which read as
            // broken rather than as "nothing to relay right now".
            line1.isEmpty() && line2.isEmpty() -> Text("Handy nicht mit Scooter verbunden", style = MaterialTheme.typography.bodyMedium)
            else -> {
                if (line1.isNotEmpty()) Text(line1, style = MaterialTheme.typography.bodyMedium)
                if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
