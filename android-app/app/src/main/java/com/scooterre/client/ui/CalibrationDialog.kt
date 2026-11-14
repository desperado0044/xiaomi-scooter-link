package com.scooterre.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.scooterre.client.protocol.CalibrationScale
import com.scooterre.client.viewmodel.UiState

/** The scale-calibration dialog (see [CalibrationScale], [com.scooterre.client.viewmodel.
 * ScooterViewModel.openCalibration]): one text field per FLOAT property this app has no confirmed
 * scale for, each pre-labelled with the raw value the dashboard already read - opening it never
 * triggers its own BLE round trip. Submitting opens a pre-filled GitHub issue; see
 * [openGithubIssue]'s own doc comment for why there is no in-app auto-post. */
@Composable
fun CalibrationDialog(state: UiState, onReferenceChange: (String, String) -> Unit, onDismiss: () -> Unit) {
    val calibration = state.calibration ?: return
    val s = strings(state.language)
    val rs = reportStrings(state.language)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rs.calibrationTitle) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(rs.calibrationIntro, style = MaterialTheme.typography.bodyMedium)
                if (calibration.candidates.isEmpty()) {
                    Text(rs.calibrationNoCandidates, style = MaterialTheme.typography.bodyMedium)
                } else {
                    calibration.candidates.forEach { (name, raw) ->
                        Column {
                            Text("${propertyName(name, state.language)} (raw=$raw)", style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = calibration.references[name] ?: "",
                                onValueChange = { onReferenceChange(name, it) },
                                label = { Text(rs.calibrationFieldHint) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = calibration.candidates.isNotEmpty(),
                onClick = {
                    val entries = calibration.candidates
                        .map { (name, raw) -> Triple(name, raw, calibration.references[name] ?: "") }
                        .filter { it.third.isNotBlank() }
                    val body = diagnosticsReport(context, state) + "\n\n" + CalibrationScale.report(state.activeModel, entries)
                    openGithubIssue(
                        context, uriHandler,
                        "Compatibility: scale factors for ${state.activeModel ?: "unknown model"}",
                        body, rs.reportCopiedPasteHint,
                    )
                    onDismiss()
                },
            ) { Text(rs.reportOnGithubButton) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.closeButton) } },
    )
}
