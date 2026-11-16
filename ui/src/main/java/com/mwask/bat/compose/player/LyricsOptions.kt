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

    var enabled by mutableStateOf(true)
        private set

    var preview by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val prefs = AppPrefs.prefs(context, PREFS)
        enabled = prefs.getBoolean(KEY_ENABLED, true)
        preview = prefs.getBoolean(KEY_PREVIEW, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_ENABLED, value).apply()
    }

    fun setPreview(context: Context, value: Boolean) {
        preview = value
        AppPrefs.edit(context, PREFS).putBoolean(KEY_PREVIEW, value).apply()
    }
}
