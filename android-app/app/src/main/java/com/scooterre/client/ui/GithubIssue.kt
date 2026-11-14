package com.scooterre.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import java.net.URLEncoder

private const val ISSUE_URL_BASE = "https://github.com/desperado0044/xiaomi-scooter-link/issues/new"

// GitHub issue URLs get unreliable well before a browser's own URL length limit - keep issues
// openable on every device rather than finding the exact ceiling.
private const val MAX_URL_BODY_CHARS = 4000

/** Opens GitHub's own "new issue" page with the title and body pre-filled via query parameters.
 * No server and no token are involved: the person's own browser and GitHub login do the actual
 * posting (an embedded write token was considered for a one-tap report and explicitly rejected -
 * see project memory: "keine zusätzlichen Risiken"). If the body is too long to fit a URL, it is
 * put on the clipboard instead and the issue page opens with just the title, so the person only
 * has to paste. */
fun openGithubIssue(context: Context, uriHandler: UriHandler, title: String, body: String, pasteHint: String) {
    val encodedTitle = URLEncoder.encode(title, "UTF-8")
    if (body.length <= MAX_URL_BODY_CHARS) {
        uriHandler.openUri("$ISSUE_URL_BASE?title=$encodedTitle&body=${URLEncoder.encode(body, "UTF-8")}")
    } else {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("scooter report", body))
        Toast.makeText(context, pasteHint, Toast.LENGTH_LONG).show()
        uriHandler.openUri("$ISSUE_URL_BASE?title=$encodedTitle")
    }
}
