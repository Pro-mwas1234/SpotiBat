package com.mwask.bat.compose.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.SHARE)

object Share {

    fun url(uri: String): String {
        val clean = uri.substringBefore("?").trim()
        if (clean.isBlank()) return "https://open.spotify.com"
        if (clean == "spotify:collection:tracks" || clean.endsWith(":collection")) {
            return "https://open.spotify.com/collection/tracks"
        }
        val parts = clean.removePrefix("spotify:").split(":")
        if (parts.size >= 4 && parts[0] == "user") {
            return "https://open.spotify.com/${parts[2]}/${parts[3]}"
        }
        if (parts.size >= 2) return "https://open.spotify.com/${parts[0]}/${parts[1]}"
        return "https://open.spotify.com"
    }

    fun copy(context: Context, uri: String, label: String = "Link copied") {
        val link = url(uri)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager == null) {
            log.w("copy: no clipboard service")
            return
        }
        manager.setPrimaryClip(ClipData.newPlainText(label, link))
        log.i("copied $link")
    }

    fun open(context: Context, uri: String) {
        openUrl(context, url(uri))
    }

    fun openUrl(context: Context, link: String) {
        val target = link.trim()
        if (target.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        log.i("open $target")
    }

    fun send(context: Context, uri: String, title: String, subtitle: String = "") {
        val link = url(uri)
        val body = if (subtitle.isBlank()) link else "$title\n$subtitle\n$link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(intent, "Share")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        log.i("share $link")
    }
}
