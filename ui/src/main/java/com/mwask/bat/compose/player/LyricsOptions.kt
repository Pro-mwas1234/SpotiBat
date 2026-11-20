package com.mwask.bat.compose.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mwask.bat.backend.core.store.AppPrefs

object LyricsOptions {

    private const val PREFS = "quackify.client"

    private const val KEY_ENABLED = "lyricsEnabled"

    private const val KEY_PREVIEW = "lyricsPreviewEnabled"

    private const val KEY_INLINE = "lyricsInlineUnderPlayer"

    private const val KEY_SYNC_OFFSET = "lyricsSyncOffsetMs"

    /** How far ahead of the reported position the highlight runs, tunable in the lyrics view. */
    private const val DEFAULT_SYNC_OFFSET_MS = 250

    private const val MAX_SYNC_OFFSET_MS = 2_000

    private const val MIN_SYNC_OFFSET_MS = -2_000

    var enabled by mutableStateOf(true)
        private set

    var preview by mutableStateOf(true)
        private set

    /** Show the synced line under the artwork instead of the footer lyrics button. */
    var inlineUnderPlayer by mutableStateOf(false)
        private set

    /** Positive values light the words earlier, negative later. */
    var syncOffsetMs by mutableStateOf(DEFAULT_SYNC_OFFSET_MS)
        private set

    fun load(context: Context) {
        val prefs = AppPrefs.prefs(context, PREFS)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preview = prefs.getBoolean(KEY_PREVIEW, true)
        inlineUnderPlayer = prefs.getBoolean(KEY_INLINE, false)
        syncOffsetMs = prefs.getInt(KEY_SYNC_OFFSET, DEFAULT_SYNC_OFFSET_MS)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_ENABLED, value).apply()
    }

    fun setPreview(context: Context, value: Boolean) {
        preview = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_PREVIEW, value).apply()
    }

    fun setInlineUnderPlayer(context: Context, value: Boolean) {
        inlineUnderPlayer = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_INLINE, value).apply()
    }

    fun setSyncOffsetMs(context: Context, value: Int) {
        syncOffsetMs = value.coerceIn(MIN_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
        AppPrefs.edit(context, PREFS).putInt(KEY_SYNC_OFFSET, syncOffsetMs).apply()
    }

    fun resetSyncOffsetMs(context: Context) = setSyncOffsetMs(context, DEFAULT_SYNC_OFFSET_MS)
}
