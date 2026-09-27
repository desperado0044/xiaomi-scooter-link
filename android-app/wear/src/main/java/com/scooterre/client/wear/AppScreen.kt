package com.scooterre.client.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

/** App-level actions not tied to any one scooter - just the updater for now; the settings the
 * watch also offers standalone (units, language, ...) land here later. */
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availableVersion by WatchState.availableUpdateVersion.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }

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
                    item { ListHeader { Text("App") } }
                    availableVersion?.let { version ->
                        item {
                            Text(
                                "Update verfügbar: $version",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    item {
                        Button(onClick = { scope.launch { WearUpdate.checkAndInstall(context) { status = it } } }) {
                            Text("Update suchen")
                        }
                    }
                    status?.let { text -> item { Text(text, style = MaterialTheme.typography.labelSmall) } }
                }
            }
        }
    }
}
