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

private const val PREFS_NAME = "wear_update"
private const val KEY_LAST_CHECK = "last_check"
private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/**
 * Update checking/downloading for the watch, mirroring the phone's own split exactly: a cheap,
 * JSON-only automatic check once a day just sets a "there's a newer version" notice
 * ([WatchState.availableUpdateVersion]) - downloading and installing stays a separate, explicit
 * step ("Update suchen" on the App screen). The automatic check trusts the watch APK's filename-
 * embedded version (see [UpdateChecker.parseRelease]); the manual step always re-confirms against
 * the downloaded file's own real version before ever installing it, regardless of what the cheap
 * check said.
 */
object WearUpdate {
    /** Called once at app start (see WearApplication) - a no-op unless a day has passed since the
     * last check. Network I/O, but just one small JSON request, never a download. */
    suspend fun autoCheckIfDue(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest(AssetKind.WATCH) } ?: return
        val installed = installedVersionOf(context)
        WatchState.availableUpdateVersion.value = latest.version.takeIf { UpdateChecker.isNewer(it, installed) }
    }

    /** "Update suchen": always does the real, accurate check via a download, regardless of what an
     * earlier automatic check found - that one exists only to give an earlier heads-up. */
    suspend fun checkAndInstall(context: Context, onStatus: (String) -> Unit) {
        onStatus("Suche Update...")
        val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest(AssetKind.WATCH) }
        if (latest == null) {
            onStatus("Update-Prüfung fehlgeschlagen")
            return
        }
        val installed = installedVersionOf(context)
        if (latest.apkUrl == null) {
            onStatus("Keine Uhr-APK im aktuellen Release gefunden")
            return
        }
        onStatus("Lade Update...")
        val result = withContext(Dispatchers.IO) {
            UpdateInstaller.downloadAndVerify(context, latest) { percent -> onStatus("Lade Update... $percent%") }
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
                // The file's own embedded version is the only fully authoritative answer (see the
                // class doc comment) - checked here regardless of what triggered this download.
                val downloadedVersion = UpdateInstaller.apkVersionName(context, result.file)
                if (downloadedVersion == null || !UpdateChecker.isNewer(downloadedVersion, installed)) {
                    result.file.delete()
                    WatchState.availableUpdateVersion.value = null
                    onStatus("Aktuell (Version $installed)")
                    return
                }
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
