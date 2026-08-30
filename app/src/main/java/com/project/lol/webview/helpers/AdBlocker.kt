package com.project.lol.webview.helpers

import java.util.Locale

fun isAnalyticsDomain(url: String): Boolean {
    return url.contains("doubleclick.net") ||
            url.contains("googlesyndication.com") ||
            url.contains("fastly-insights.com") ||
            url.contains("sentry.io") ||
            url.contains("t.6sc.co") ||
            url.contains("tracker.samplicio.us") ||
            url.contains("adsrvr.org") ||
            url.contains("aet.spotify.com") ||
            url.contains("retargeting-pixels") ||
            url.contains("spotify.com/gabo-receiver-service/public/v3/events")
            // workbox-window REMOVED: Spotify now loads it as a lazy webpack chunk
            // (chunk 6201) during web-player init. Blocking it caused ChunkLoadError
            // -> React error boundary -> "Something wrong with page". The service
            // worker itself is already neutralized by WorkerNeutralize injecting
            // before Spotify's code runs, so this block was redundant anyway.
}

@Suppress("unused")
fun isAdAudioUrl(url: String): Boolean {
    return url.contains("akamaized.net/audio/") ||
            url.contains("scdn.co/audio/") ||
            url.contains("scdn.co/mp3-ad/") ||
            url.contains("spotifycdn.com/audio/") ||
            url.contains("amillionads.com") ||
            url.contains("2mdn.net") ||
            url.contains("adxcel.com") ||
            url.contains("adstudio-assets.scdn.co")
}

fun isPowerHogUrl(url: String): Boolean {
    if (url.contains("canvasset.scdn.co")) return true
    if (url.contains("video-ak.spotify.com")) return true
    if (url.contains("video-provider.net")) return true
    if (url.contains("/audio/")) return false
    val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return path.endsWith(".mp4") || path.endsWith(".m4s") || path.endsWith(".m3u8") || path.endsWith(".webm")
}

fun matchAdCdn(url: String): String? {
    if (url.contains("scdn.co/mp3-ad/")) return "scdn.co/mp3-ad/"
    if (url.contains("mp3ad.scdn.co")) return "mp3ad.scdn.co"
    if (url.contains("amillionads.com")) return "amillionads.com"
    if (url.contains("2mdn.net")) return "2mdn.net"
    if (url.contains("adxcel.com")) return "adxcel.com"
    if (url.contains("adstudio-assets.scdn.co")) return "adstudio-assets.scdn.co"
    if (url.contains("audio-ads.spotify.com")) return "audio-ads.spotify.com"
    if (url.contains("ads-akp.spotify.com")) return "ads-akp.spotify.com"
    if (url.contains("ads-fa.spotify.com")) return "ads-fa.spotify.com"
    if (url.contains("adeventtracker.spotify.com")) return "adeventtracker.spotify.com"
    if (url.contains("pixel-static.spotify.com")) return "pixel-static.spotify.com"
    if (url.contains("pixel.spotify.com")) return "pixel.spotify.com"
    if (url.contains("adstudio.spotify.com")) return "adstudio.spotify.com"
    if (url.contains("ads.spotify.com")) return "ads.spotify.com"
    return null
}