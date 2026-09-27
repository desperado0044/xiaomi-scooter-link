package com.scooterre.client.update

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The version that is installed now ("0" if it cannot be read) - shared by :app and :wear. */
fun installedVersionOf(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

/** A newer release: its version, the release page and (if it has one) the APK asset to download.
 * [apkSha256] is GitHub's published digest of that asset, when available. */
data class UpdateInfo(
    val version: String,
    val url: String,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
)

/** Which app is checking - a release can carry both APKs (see the Wear OS companion), and each
 * side must only ever pick up its own, never the other one's (same applicationId + signing
 * certificate, so the other one would otherwise parse as a perfectly valid "update"). */
enum class AssetKind { PHONE, WATCH }

/** Looks up the newest published GitHub release of this project. Public API, no login; drafts and
 * pre-releases are never returned by `/releases/latest`. */
object UpdateChecker {
    private const val REPO = "desperado0044/xiaomi-scooter-link"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** The only place an APK is ever downloaded from. */
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Blocking - call off the main thread. Null on any failure (offline, rate-limited, bad JSON). */
    fun fetchLatest(kind: AssetKind): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "scooter-client-update-check")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                parseRelease(response.body?.string() ?: return null, kind)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** scooter-link-wear-0.2.apk -> "0.2" - the watch's own APK is always named with its own
     * version this way (see the release step), which is what makes a cheap, JSON-only check
     * possible for it at all: unlike the phone, its version has nothing to do with the release
     * tag, and reading the real thing means downloading and opening the APK (see
     * UpdateInstaller.apkVersionName) - too expensive to do on every automatic check. */
    private val WEAR_ASSET_VERSION = Regex("""-(\d+(?:\.\d+)+)\.apk$""", RegexOption.IGNORE_CASE)

    /** Reads a GitHub release JSON. The APK asset is only taken if it really lives under
     * [DOWNLOAD_PREFIX] and matches [kind] - a release can carry both the phone and the watch APK
     * (they share an applicationId and signing certificate, see [AssetKind]'s doc comment), so the
     * "wear" name marker is the only thing telling them apart here. [UpdateInfo.version] is the
     * release tag for [AssetKind.PHONE] (by convention, always equal to the phone's own
     * versionName) but the watch APK's own filename-embedded version for [AssetKind.WATCH] - falls
     * back to the tag if the name doesn't parse, which just means a future automatic check might
     * miss a release, never that it offers a wrong one (the download-time check is still final). */
    fun parseRelease(body: String, kind: AssetKind): UpdateInfo? {
        return try {
            val json = JSONObject(body)
            var apkUrl: String? = null
            var apkSha: String? = null
            var assetVersion: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val isWearAsset = name.contains("wear", ignoreCase = true)
                    val matchesKind = if (kind == AssetKind.WATCH) isWearAsset else !isWearAsset
                    if (name.endsWith(".apk", ignoreCase = true) && matchesKind && url.startsWith(DOWNLOAD_PREFIX)) {
                        apkUrl = url
                        apkSha = normalizeDigest(asset.optString("digest"))
                        assetVersion = WEAR_ASSET_VERSION.find(name)?.groupValues?.get(1)
                        break
                    }
                }
            }
            val tag = json.getString("tag_name").removePrefix("v")
            val version = if (kind == AssetKind.WATCH) assetVersion ?: tag else tag
            UpdateInfo(version, json.getString("html_url"), apkUrl, apkSha)
        } catch (e: Exception) {
            null
        }
    }

    /** "sha256:ABC..." -> "abc..." (lower case hex), or null if it is not a SHA-256 digest. */
    fun normalizeDigest(digest: String?): String? {
        val hex = digest?.removePrefix("sha256:")?.lowercase() ?: return null
        return if (digest.startsWith("sha256:") && Regex("[0-9a-f]{64}").matches(hex)) hex else null
    }

    /** True if [latest] is a higher dotted version than [installed] ("1.10" > "1.9", "v" prefix ignored; "2.6" > "2.6-alpha1"). */
    fun isNewer(latest: String, installed: String): Boolean {
        fun parts(version: String) = version.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return '-' in installed && '-' !in latest
    }
}
