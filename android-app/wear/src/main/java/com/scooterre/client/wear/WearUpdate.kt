package com.scooterre.client.wear

import android.content.Context
import com.scooterre.client.update.AssetKind
import com.scooterre.client.update.UpdateChecker
import com.scooterre.client.update.UpdateDownloadResult
import com.scooterre.client.update.UpdateInstaller
import com.scooterre.client.update.UpdateProblem
import com.scooterre.client.update.installedVersionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Update suchen": no daily auto-check like the phone (the watch is used far less often, and a
 * manual look is enough here) - checks GitHub, downloads+verifies the watch APK if there is a
 * newer one, and hands it to the system installer, which still asks the user to confirm. */
object WearUpdate {
    suspend fun checkAndInstall(context: Context, onStatus: (String) -> Unit) {
        onStatus("Suche Update...")
        val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest(AssetKind.WATCH) }
        if (latest == null) {
            onStatus("Update-Prüfung fehlgeschlagen")
            return
        }
        val installed = installedVersionOf(context)
        if (!UpdateChecker.isNewer(latest.version, installed)) {
            onStatus("Aktuell (Version $installed)")
            return
        }
        if (latest.apkUrl == null) {
            onStatus("Neue Version ${latest.version}, aber keine Uhr-APK im Release")
            return
        }
        onStatus("Lade Update ${latest.version}...")
        val result = withContext(Dispatchers.IO) {
            UpdateInstaller.downloadAndVerify(context, latest) { percent -> onStatus("Lade Update ${latest.version}... $percent%") }
        }
        when (result) {
            is UpdateDownloadResult.Failed -> onStatus(
                when (result.problem) {
                    UpdateProblem.DOWNLOAD -> "Herunterladen fehlgeschlagen"
                    UpdateProblem.HASH -> "Datei beschädigt (Prüfsumme falsch)"
                    UpdateProblem.SIGNER -> "Signatur passt nicht"
                    UpdateProblem.NOT_APK -> "Keine gültige APK"
                },
            )
            is UpdateDownloadResult.Ok -> {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    onStatus("Bitte Installationsrecht erteilen, dann erneut versuchen")
                    UpdateInstaller.openInstallPermissionSettings(context)
                    return
                }
                UpdateInstaller.install(context, result.file)
                onStatus("Installation gestartet")
            }
        }
    }
}
