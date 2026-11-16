package com.mwask.bat

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mwask.bat.search.decorateSearchTracks
import com.mwask.bat.backend.api.Browse
import com.mwask.bat.backend.api.HomeFeed
import com.mwask.bat.backend.api.Library
import com.mwask.bat.backend.api.Podcasts
import com.mwask.bat.backend.api.Profile
import com.mwask.bat.backend.api.Search
import com.mwask.bat.backend.api.Settings
import com.mwask.bat.backend.api.WhatsNew
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.webview.SilentTokenRefresh
import com.mwask.bat.backend.library.Krespot
import com.mwask.bat.backend.library.PlaylistSource
import com.mwask.bat.backend.model.AlbumDetail
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.BrowseCard
import com.mwask.bat.backend.model.BrowsePage
import com.mwask.bat.backend.model.CollectionDetail
import com.mwask.bat.backend.model.Credits
import com.mwask.bat.backend.model.EpisodeDetail
import com.mwask.bat.backend.model.HomeChip
import com.mwask.bat.backend.model.HomeSection
import com.mwask.bat.backend.model.PlaylistChoice
import com.mwask.bat.backend.model.ProfileContext
import com.mwask.bat.backend.model.ProfileTopContent
import com.mwask.bat.backend.model.ProfileUser
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.backend.model.SearchResults
import com.mwask.bat.backend.model.ShelfKind
import com.mwask.bat.backend.model.ShowDetail
import com.mwask.bat.backend.model.TrackLyrics
import com.mwask.bat.backend.model.UserProfile
import com.mwask.bat.compose.components.UpdateDialog
import com.mwask.bat.compose.components.UpdateInstallPhase
import com.mwask.bat.compose.format.formatCount
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.compose.home.toArtistRelease
import com.mwask.bat.compose.home.toFavoriteArtistsShelf
import com.mwask.bat.compose.home.toQuickAccess
import com.mwask.bat.compose.home.toQuickAccessFallback
import com.mwask.bat.compose.home.toRecentlyPlayedShelf
import com.mwask.bat.compose.home.toShelves
import com.mwask.bat.compose.home.toTrackItem
import com.mwask.bat.compose.library.LibraryItem
import com.mwask.bat.compose.library.LibraryItemType
import com.mwask.bat.compose.player.TimeSyncedLyric
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.screens.ArtistRelease
import com.mwask.bat.compose.screens.CategoryRecommendationItem
import com.mwask.bat.compose.screens.CategoryShelf
import com.mwask.bat.compose.screens.EncoreNotification
import com.mwask.bat.compose.screens.HistoryStats
import com.mwask.bat.compose.screens.LoginScreen
import com.mwask.bat.compose.screens.PathfinderCaptureScreen
import com.mwask.bat.compose.screens.PopularTrack
import com.mwask.bat.compose.screens.ProfileContextCard
import com.mwask.bat.compose.screens.SettingsLocks
import com.mwask.bat.compose.screens.SettingsSwitches
import com.mwask.bat.compose.search.SearchHistoryItem
import com.mwask.bat.compose.shell.EncoreAppShell
import com.mwask.bat.compose.shell.PlaylistEditDraft
import com.mwask.bat.compose.shell.ShellDeepLink
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.util.rememberIsOffline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar



private val log = Logs.of(Logs.Module.MAIN)

internal val LAUNCHER_ALIASES = linkedMapOf(
    "default" to ".LauncherDefault",
    "normal" to ".LauncherNormal",
)

internal fun launcherIconPref(context: Context): String {
    val saved = com.mwask.bat.backend.core.store.AppPrefs.prefs(context, "spotibat.client")
        .getString("appIcon", "default").orEmpty().ifBlank { "default" }
    return if (saved in LAUNCHER_ALIASES) saved else "default"
}

internal fun applyLauncherIcon(context: Context, option: String) {
    val pm = context.packageManager
    val target = LAUNCHER_ALIASES[option] ?: return
    val prefix = MainActivity::class.java.name.substringBeforeLast('.')
    LAUNCHER_ALIASES.forEach { (key, cls) ->
        val component = ComponentName(context, "$prefix$cls")
        val want = if (key == option) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(component) != want) {
            runCatching {
                pm.setComponentEnabledSetting(component, want, PackageManager.DONT_KILL_APP)
            }.onFailure { log.w("launcher icon '$key' -> ${want == PackageManager.COMPONENT_ENABLED_STATE_ENABLED} failed: $it") }
        }
    }
    log.i("launcher icon -> $option")
}

internal fun syncLauncherIcon(context: Context) {
    applyLauncherIcon(context, launcherIconPref(context))
}

