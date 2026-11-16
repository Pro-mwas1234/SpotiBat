package com.mwask.bat.compose.widget

import android.content.Context
import com.mwask.bat.backend.core.store.AppPrefs

enum class WidgetBackgroundStyle(val key: String) {
    DARK("dark"),
    TINTED("tinted"),
    TRANSPARENT("transparent");

    companion object {
        fun from(key: String?): WidgetBackgroundStyle =
            entries.firstOrNull { it.key == key } ?: TINTED
    }
}

data class WidgetOptions(
    val showArtwork: Boolean = true,
    val showLike: Boolean = true,
    val showProgress: Boolean = true,
    val background: WidgetBackgroundStyle = WidgetBackgroundStyle.TINTED,
)

object WidgetOptionsStore {

    private const val PREFS = "spotibat.client"
    private const val KEY_ARTWORK = "widgetShowArtwork"
    private const val KEY_LIKE = "widgetShowLike"
    private const val KEY_PROGRESS = "widgetShowProgress"
    private const val KEY_BACKGROUND = "widgetBackground"

    fun load(context: Context): WidgetOptions {
        val prefs = AppPrefs.prefs(context, PREFS)
        return WidgetOptions(
            showArtwork = prefs.getBoolean(KEY_ARTWORK, true),
            showLike = prefs.getBoolean(KEY_LIKE, true),
            showProgress = prefs.getBoolean(KEY_PROGRESS, true),
            background = WidgetBackgroundStyle.from(prefs.getString(KEY_BACKGROUND, null)),
        )
    }

    fun saveShowArtwork(context: Context, value: Boolean) {
        AppPrefs.edit(context, PREFS).putBoolean(KEY_ARTWORK, value).apply()
    }

    fun saveShowLike(context: Context, value: Boolean) {
        AppPrefs.edit(context, PREFS).putBoolean(KEY_LIKE, value).apply()
    }

    fun saveShowProgress(context: Context, value: Boolean) {
        AppPrefs.edit(context, PREFS).putBoolean(KEY_PROGRESS, value).apply()
    }

    fun saveBackground(context: Context, value: WidgetBackgroundStyle) {
        AppPrefs.edit(context, PREFS).putString(KEY_BACKGROUND, value.key).apply()
    }
}
