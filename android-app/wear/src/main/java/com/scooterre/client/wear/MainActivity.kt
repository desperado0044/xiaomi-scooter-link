package com.scooterre.client.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * Entry point / device selection: "Mit Handy verbinden" (relay, the normal case - shows whatever
 * the phone last sent) plus one entry per scooter the phone has pushed a key for. Tapping a
 * scooter is meant to start a direct, watch-only connection to it when the phone is out of reach -
 * not built yet, so it just says so for now instead of pretending to do something.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SelectionScreen()
        }
    }
}

@Composable
private fun SelectionScreen() {
    val devices by WatchState.devices.collectAsState()
    val status by WatchState.status.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) {
                TransformingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { ListHeader { Text("Scooter Link") } }
                    item {
                        Button(onClick = { notice = null }) { Text("Mit Handy verbinden") }
                    }
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
                            item {
                                Button(onClick = { notice = "Direktverbindung folgt noch" }) {
                                    Text(device.name ?: device.mac)
                                }
                            }
                        }
                    }
                    notice?.let { text -> item { Text(text) } }
                }
            }
        }
    }
}
