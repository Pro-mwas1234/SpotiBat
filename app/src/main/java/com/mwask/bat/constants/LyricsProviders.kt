package com.mwask.bat.constants

/**
 * Lyrics providers ported from YumaPlayer/ArchiveTune (GPL-3.0).
 * The order below is Yuma's default priority order.
 */
enum class PreferredLyricsProvider(val displayName: String, val prefsKey: String) {
    BETTER_LYRICS("BetterLyrics", "lyrics_provider_betterlyrics"),
    YOULY_PLUS("YouLyPlus", "lyrics_provider_youlyplus"),
    LRCLIB("LrcLib", "lyrics_provider_lrclib"),
    KUGOU("KuGou", "lyrics_provider_kugou"),
    SIMPMUSIC("SimpMusic", "lyrics_provider_simpmusic"),
    UNISON("Unison", "lyrics_provider_unison"),
}

/** Yuma's default provider priority, most reliable first. */
val DefaultLyricsProviderOrder = listOf(
    PreferredLyricsProvider.BETTER_LYRICS,
    PreferredLyricsProvider.YOULY_PLUS,
    PreferredLyricsProvider.LRCLIB,
    PreferredLyricsProvider.KUGOU,
    PreferredLyricsProvider.SIMPMUSIC,
    PreferredLyricsProvider.UNISON,
)

/**
 * SimpMusic indexes YouTube video ids only. Spotify tracks carry no video id,
 * so the provider is skipped unless one is ever known for the playing track.
 */
val VideoIdOnlyLyricsProviders = setOf(PreferredLyricsProvider.SIMPMUSIC)

object LyricsProviderKeys {
    const val ProviderOrder = "lyrics_provider_order"

    /** Prefs file shared with the rest of the client settings. */
    const val PrefsName = "quackify.client"
}
