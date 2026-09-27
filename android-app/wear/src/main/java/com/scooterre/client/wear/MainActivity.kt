package com.scooterre.client.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.scooterre.client.protocol.DeviceRegistry

/**
 * Skeleton entry point - proves the module builds, installs on the watch and can see the
 * shared BLE/crypto/protocol code in :core. No relay/direct-connect logic yet; that comes next.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlaceholderScreen()
        }
    }
}

@Composable
private fun PlaceholderScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val devices = remember { DeviceRegistry(context).list() }
    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) {
                TransformingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { ListHeader { Text("Scooter Link") } }
                    item {
                        Text(
                            if (devices.isEmpty()) "Noch nicht eingerichtet"
                            else "Gekoppelt: " + (devices.first().name ?: devices.first().mac)
                        )
                    }
                }
            }
        }
    }
}
