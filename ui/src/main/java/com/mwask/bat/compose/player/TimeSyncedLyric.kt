package com.mwask.bat.compose.player

/**
 */
data class TimeSyncedLyric(
    val startTimeMs: Long,
    val text: String
)

val defaultLyricsList = listOf(
    TimeSyncedLyric(0L, "I'm tryna put you in the worst mood, ah"),
    TimeSyncedLyric(4000L, "P1 cleaner than your church shoes, ah"),
    TimeSyncedLyric(8000L, "Milli point two just to hurt you, ah"),
    TimeSyncedLyric(12000L, "All red Lamb' leanin' on you, ah"),
    TimeSyncedLyric(16000L, "None of these toys on a lease too, ah"),
    TimeSyncedLyric(20000L, "Made your whole year in a week too, yah"),
    TimeSyncedLyric(24000L, "Main bitch out your league too, ah"),
    TimeSyncedLyric(28000L, "Side bitch out of your league too, ah"),
    TimeSyncedLyric(32000L, "House so empty need a centerpiece"),
    TimeSyncedLyric(36000L, "20 racks a table cut from ebony"),
    TimeSyncedLyric(40000L, "Cut that ivory into skinny pieces"),
    TimeSyncedLyric(44000L, "Then she clean it with her face, man I love my baby"),
    TimeSyncedLyric(48000L, "Look what you've done"),
    TimeSyncedLyric(52000L, "I'm a motherfuckin' starboy"),
    TimeSyncedLyric(56000L, "Look what you've done"),
    TimeSyncedLyric(60000L, "I'm a motherfuckin' starboy")
)
