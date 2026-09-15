package com.mwask.bat.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.mwask.bat.searchEngine.GenericSearchEngine
import com.mwask.bat.searchEngine.SearchableFieldExtractor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import com.mwask.bat.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwask.bat.offline.OfflinePlayback
import com.mwask.bat.offline.OfflineSong
import com.mwask.bat.offline.OfflineStore
import com.mwask.bat.service.OfflineAudioFocus
import com.mwask.bat.service.OfflineMediaService
import com.mwask.bat.ui.components.SettingsDrawer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier,
    openPlayer: Boolean = false,
    onOpenPlayerHandled: () -> Unit = {},
    prefs: SharedPreferences,
    materialYou: Boolean,
    onMaterialYouChange: (Boolean) -> Unit,
    amoledTheme: Boolean,
    onAmoledThemeChange: (Boolean) -> Unit,
    hideTopBar: Boolean,
    onHideTopBarChange: (Boolean) -> Unit,
    landscapeMode: Boolean,
    onLandscapeModeChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    paletteSeed: String?,
    onPaletteSeedChange: (String?) -> Unit,
    onConnectionModeChange: (String) -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onSaveProfile: (String, String) -> Unit,
    onLoadProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onClearCache: () -> Unit,
    onClearData: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settingsDrawerOpen by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }

    var songs by remember { mutableStateOf<List<OfflineSong>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var playerSong by remember { mutableStateOf<OfflineSong?>(null) }
    var pendingDelete by remember { mutableStateOf<OfflineSong?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var scrubMs by remember { mutableIntStateOf(-1) }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Single source of truth for every playback surface (mini bar, full-screen
    // player, media notification). The MediaPlayer engine below is the sole
    // writer via publishState(); the UI only renders this snapshot.
    val pb by OfflinePlayback.state.collectAsStateWithLifecycle()
    val uiPlaying = pb.hasTrack && pb.playing
    val uiPosition = if (scrubMs >= 0) scrubMs else pb.positionMs.toInt()
    val uiDuration = pb.durationMs.toInt()

    // Notification tap (or an already-running launch) opens the full player.
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            if (OfflinePlayback.state.value.hasTrack) showFullPlayer = true
            onOpenPlayerHandled()
        }
    }

    val mediaPlayer = remember { MediaPlayer() }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<OfflineSong>?>(null) }
    var coverRestore by remember { mutableStateOf<String?>(null) }

    val searchEngine = remember { GenericSearchEngine<OfflineSong>(maxResult = 100) }
    val songExtractor = remember {
        SearchableFieldExtractor<OfflineSong> { song ->
            arrayOf(song.title, song.artist, song.album, song.ytAlbum, song.ytArtist)
        }
    }

    LaunchedEffect(searchQuery, songs) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResults = null
        } else {
            delay(300.milliseconds)
            searchResults = withContext(Dispatchers.Default) {
                searchEngine.filter(songs, q, songExtractor)
            }
        }
    }
    val visibleSongs = searchResults ?: songs

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    fun publishState() {
        val song = playerSong
        OfflinePlayback.publish(
            OfflinePlayback.Snapshot(
                hasTrack = song != null,
                title = song?.title.orEmpty(),
                artist = song?.artist.orEmpty(),
                album = song?.album.orEmpty(),
                coverPath = song?.coverFile?.absolutePath,
                playing = isPlaying,
                positionMs = positionMs.toLong(),
                durationMs = durationMs.toLong(),
            )
        )
    }

    // ---- Audio focus: pause for calls, duck past notification sounds ----
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val audioFocus = remember {
        OfflineAudioFocus(
            audioManager = audioManager,
            onPause = {
                runCatching {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                        publishState()
                    }
                }
            },
            onDuck = { target ->
                if (target >= 0) {
                    runCatching {
                        mediaPlayer.seekTo(target.toInt())
                        positionMs = target.toInt()
                        publishState()
                    }
                }
            },
            onUnduck = { restore ->
                runCatching {
                    mediaPlayer.seekTo(restore.toInt())
                    positionMs = restore.toInt()
                    publishState()
                }
            }
        )
    }

    fun syncService() {
        if (playerSong == null) return
        publishState()
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OfflineMediaService::class.java)
            )
        }
    }

    fun play(index: Int) {
        playAt(mediaPlayer, context, songs, index, { currentIndex = it }, { playerSong = it }, { isPlaying = it }, { durationMs = it }, { positionMs = it })
        if (isPlaying && !audioFocus.request()) {
            // Focus denied (e.g. during a call): don't play over it.
            runCatching { mediaPlayer.pause() }
            isPlaying = false
        }
        syncService()
    }

    fun togglePlayPause() {
        runCatching {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else if (durationMs > 0) {
                if (audioFocus.request()) {
                    mediaPlayer.start()
                    isPlaying = true
                }
            }
        }
        syncService()
    }

    fun step(delta: Int) {
        if (songs.isEmpty()) return
        val next = ((currentIndex + delta) % songs.size + songs.size) % songs.size
        play(next)
    }

    fun seekTo(position: Long) {
        if (durationMs <= 0) return
        runCatching { mediaPlayer.seekTo(position.toInt()) }
        positionMs = position.toInt()
        publishState()
    }

    fun stopAndClear() {
        runCatching {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
            mediaPlayer.reset()
        }
        audioFocus.abandon()
        isPlaying = false
        currentIndex = -1
        positionMs = 0
        durationMs = 0
        OfflinePlayback.clear()
        runCatching { context.stopService(Intent(context, OfflineMediaService::class.java)) }
    }

    fun performDelete(song: OfflineSong) {
        val index = songs.indexOfFirst { it.id == song.id && it.uri == song.uri }
        if (index == -1) return
        if (index == currentIndex) {
            stopAndClear()
            showFullPlayer = false
        } else if (index < currentIndex) {
            currentIndex -= 1
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { OfflineStore.deleteSong(context, song) }
            songs = songs.filterNot { it.id == song.id && it.uri == song.uri }
            if (!ok) {
                Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = settingsDrawerOpen || showFullPlayer || searchQuery.isNotBlank()) {
        when {
            showFullPlayer -> showFullPlayer = false
            settingsDrawerOpen -> settingsDrawerOpen = false
            else -> searchQuery = ""
        }
    }

    // ---- Headset events: auto-open offline mode and/or resume playback ----
    val headsetReceiver = remember {
        object : BroadcastReceiver() {
            private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

            // Resume a paused track shortly after the device settles. Plug
            // events can fire more than once; re-checking inside the delayed
            // runnable avoids toggling straight back into pause.
            private fun resumeIfEnabled(prefKey: String) {
                if (!prefs.getBoolean(prefKey, false)) return
                mainHandler.postDelayed({
                    val s = OfflinePlayback.state.value
                    if (s.hasTrack && !s.playing) togglePlayPause()
                }, 600)
            }

            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action ?: return) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        if (intent.getIntExtra("state", 0) != 1) return
                        if (prefs.getBoolean("HpAutoOffline", false)) {
                            // Wired: wait for the plug event to settle.
                            mainHandler.postDelayed({ enterOfflineViaHeadset(context) }, 800)
                        }
                        resumeIfEnabled("HpAutoResume")
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val isAudio = dev?.bluetoothClass
                            ?.hasService(android.bluetooth.BluetoothClass.Service.AUDIO) ?: true
                        if (!isAudio) return
                        if (prefs.getBoolean("HpAutoOffline", false)) {
                            enterOfflineViaHeadset(context)
                        }
                        resumeIfEnabled("BtAutoResume")
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(headsetReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(headsetReceiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(headsetReceiver) }
        }
    }

    // Remote intents (notification buttons, media/headset session) arrive as
    // commands and are executed here, on the engine that owns MediaPlayer.
    DisposableEffect(Unit) {
        val job = OfflinePlayback.collectCommands(scope) { cmd ->
            when (cmd) {
                OfflinePlayback.Command.PlayPause -> togglePlayPause()
                OfflinePlayback.Command.Next -> step(1)
                OfflinePlayback.Command.Prev -> step(-1)
                OfflinePlayback.Command.Stop -> stopAndClear()
                is OfflinePlayback.Command.Seek -> seekTo(cmd.positionMs)
            }
        }
        onDispose {
            job.cancel()
            audioFocus.abandon()
            runCatching { mediaPlayer.release() }
            runCatching { context.stopService(Intent(context, OfflineMediaService::class.java)) }
        }
    }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setOnCompletionListener {
            val index = currentIndex
            if (index in 0 until songs.lastIndex) {
                play(index + 1)
            } else {
                isPlaying = false
                positionMs = 0
                publishState()
            }
        }
        onDispose { }
    }

    // Audio read permission (READ_MEDIA_AUDIO on 13+, READ_EXTERNAL_STORAGE on 12-):
    // without it MediaStore hides rows the app didn't write itself.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            scope.launch {
                songs = withContext(Dispatchers.IO) { OfflineStore.loadSongs(context) }
            }
        }
    }

    LaunchedEffect(Unit) {
        val needsPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needsPerm) {
            permLauncher.launch(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            )
        }
        var list = withContext(Dispatchers.IO) { OfflineStore.loadSongs(context) }
        // Self-heal: files on disk but no MediaStore row (copied via USB, old
        // package, backup restore) are invisible to loadSongs() until scanned.
        if (list.isEmpty()) {
            withContext(Dispatchers.IO) { OfflineStore.rescanFolder(context) }
            delay(1200)
            list = withContext(Dispatchers.IO) { OfflineStore.loadSongs(context) }
        }
        songs = list
        loading = false
    }

    // One-time self-heal: songs downloaded before a reinstall/clear-data lost
    // their covers (they live in the app's private storage). The Spotify track
    // id is embedded in each filename, so art is re-fetched from Spotify's
    // unauthenticated oEmbed endpoint once per missing track.
    LaunchedEffect(loading, songs.size) {
        if (loading || songs.isEmpty() || coverRestore != null) return@LaunchedEffect
        val missingIds = songs.filter { it.coverFile == null }.map { it.id }
        if (missingIds.isEmpty()) return@LaunchedEffect
        val total = missingIds.size
        coverRestore = "Restoring 0/$total album covers…"
        val restored = withContext(Dispatchers.IO) {
            OfflineStore.restoreMissingCovers(context, missingIds) { done, t ->
                coverRestore = "Restoring $done/$t album covers…"
            }
        }
        coverRestore = if (restored > 0) {
            "Restored $restored album cover${if (restored == 1) "" else "s"}"
        } else {
            null
        }
        if (restored > 0) {
            songs = withContext(Dispatchers.IO) { OfflineStore.loadSongs(context) }
        }
        delay(2500)
        coverRestore = null
    }

    LaunchedEffect(isPlaying, currentIndex) {
        while (isPlaying) {
            runCatching { positionMs = mediaPlayer.currentPosition }
            publishState()
            delay(500.milliseconds)
        }
    }

    SettingsDrawer(
        visible = settingsDrawerOpen,
        onClose = { settingsDrawerOpen = false },
        prefs = prefs,
        materialYou = materialYou,
        onMaterialYouChange = onMaterialYouChange,
        amoledThemeState = amoledTheme,
        onAmoledThemeChange = onAmoledThemeChange,
        hideTopBar = hideTopBar,
        onHideTopBarChange = onHideTopBarChange,
        landscapeMode = landscapeMode,
        onLandscapeModeChange = onLandscapeModeChange,
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChange = onKeepScreenOnChange,
        paletteSeed = paletteSeed,
        onPaletteSeedChange = onPaletteSeedChange,
        onConnectionModeChange = onConnectionModeChange,
        onOfflineModeChange = onOfflineModeChange,
        onSaveProfile = onSaveProfile,
        onLoadProfile = onLoadProfile,
        onDeleteProfile = onDeleteProfile,
        onClearCache = onClearCache,
        onClearData = onClearData,
        onDebugToggle = {},
        blockServiceWorker = prefs.getBoolean("BlockServiceWorker", true),
        onBlockServiceWorkerChange = { enabled ->
            prefs.edit().putBoolean("BlockServiceWorker", enabled).apply()
        }
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                if (!hideTopBar) {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SpotiBat",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { settingsDrawerOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onExit) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Exit offline mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = when {
                            loading -> "Loading…"
                            searchQuery.isNotBlank() -> {
                                val n = visibleSongs.size
                                if (n == 0) {
                                    "No results for \"${searchQuery.trim()}\""
                                } else {
                                    "$n result${if (n == 1) "" else "s"} for \"${searchQuery.trim()}\""
                                }
                            }
                            songs.isEmpty() -> "No downloads yet"
                            else -> {
                                val n = songs.size
                                val base = "$n song${if (n == 1) "" else "s"} available offline"
                                coverRestore?.let { suffix -> "$base — $suffix" } ?: base
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = if (hideTopBar) 60.dp else 12.dp, bottom = 8.dp)
                    )

                    if (!loading && songs.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            placeholder = { Text("Search your downloads") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    when {
                        loading -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        searchQuery.isNotBlank() && visibleSongs.isEmpty() -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No results",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Nothing in your downloads matches \"${searchQuery.trim()}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        songs.isEmpty() -> Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Nothing here yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Download songs with the download button\nin the player, then come back",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 130.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(visibleSongs, key = { "${it.id}-${it.uri}" }) { song ->
                                val index = songs.indexOfFirst { it.id == song.id && it.uri == song.uri }
                                OfflineSongRow(
                                    song = song,
                                    isCurrent = index == currentIndex,
                                    onClick = {
                                        if (index == currentIndex) {
                                            if (durationMs > 0 || mediaPlayer.isPlaying) {
                                                togglePlayPause()
                                            } else {
                                                play(index)
                                            }
                                        } else {
                                            play(index)
                                        }
                                    },
                                    onTitleClick = { showFullPlayer = true },
                                    onDelete = { pendingDelete = song }
                                )
                            }
                        }
                    }
                }

                if (showFullPlayer) {
                    playerSong?.let { song ->
                        FullNowPlayingOverlay(
                            song = song,
                            playing = uiPlaying,
                            positionMs = uiPosition,
                            durationMs = uiDuration,
                            scrubMs = scrubMs,
                            queuePosition = currentIndex + 1,
                            queueSize = songs.size,
                            onScrub = { scrubMs = it },
                            onScrubFinished = {
                                if (scrubMs >= 0) seekTo(scrubMs.toLong())
                                scrubMs = -1
                            },
                            onTogglePlay = { togglePlayPause() },
                            onPrev = { step(-1) },
                            onNext = { step(1) },
                            onClose = { showFullPlayer = false }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = pb.hasTrack && !showFullPlayer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220)) + fadeIn(tween(220)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)) + fadeOut(tween(180))
                ) {
                    val song = playerSong
                    if (song != null) {
                        NowPlayingBar(
                            modifier = Modifier.clickable { showFullPlayer = true },
                            song = song,
                            playing = uiPlaying,
                            positionMs = uiPosition,
                            durationMs = uiDuration,
                            scrubMs = scrubMs,
                            onScrub = { scrubMs = it },
                            onScrubFinished = {
                                if (scrubMs >= 0) seekTo(scrubMs.toLong())
                                scrubMs = -1
                            },
                            onTogglePlay = { togglePlayPause() },
                            onPrev = { step(-1) },
                            onNext = { step(1) }
                        )
                    }
                }

                if (hideTopBar) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(44.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .clickable { showQuickMenu = !showQuickMenu },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_playstore),
                                contentDescription = "Quick actions",
                                tint = Color.Unspecified,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (showQuickMenu) {
                            Popup(
                                alignment = Alignment.TopCenter,
                                offset = IntOffset(0, with(LocalDensity.current) { 64.dp.toPx() }.toInt()),
                                onDismissRequest = { showQuickMenu = false }
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(modifier = Modifier.width(220.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showQuickMenu = false
                                                    settingsDrawerOpen = true
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = "Settings",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showQuickMenu = false
                                                    onExit()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                text = "Exit Offline Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
    }

    val songToDelete = pendingDelete
    if (songToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = "Delete Song?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove \"${songToDelete.title}\" by ${songToDelete.artist.ifBlank { "Unknown artist" }} from your downloads?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    performDelete(songToDelete)
                }) {
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
            }
        }
    }

private fun playAt(
    mediaPlayer: MediaPlayer,
    context: android.content.Context,
    songs: List<OfflineSong>,
    index: Int,
    setCurrentIndex: (Int) -> Unit,
    setPlayerSong: (OfflineSong) -> Unit,
    setPlaying: (Boolean) -> Unit,
    setDuration: (Int) -> Unit,
    setPosition: (Int) -> Unit,
) {
    val song = songs.getOrNull(index) ?: return
    runCatching {
        mediaPlayer.reset()
        mediaPlayer.setDataSource(context, song.uri)
        mediaPlayer.prepare()
        mediaPlayer.start()
        setCurrentIndex(index)
        setPlayerSong(song)
        setPlaying(true)
        setDuration(mediaPlayer.duration)
        setPosition(0)
    }.onFailure {
        setPlaying(false)
        OfflinePlayback.publish(OfflinePlayback.Snapshot())
    }
}

@Composable
private fun OfflineSongRow(
    song: OfflineSong,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onTitleClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        SongCover(song = song, size = 52.dp, corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTitleClick)
                    .padding(4.dp)
            )
            val subtitle = buildString {
                append(song.artist.ifBlank { "Unknown artist" })
                song.album.ifBlank { "" }.takeIf { it.isNotBlank() }?.let {
                    append(" • $it")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val metaLine = buildString {
                if (song.explicit) append("Explicit")
                song.durationSec?.let {
                    if (it > 0) {
                        if (isNotEmpty()) append(" • ")
                        append(formatSeconds(it))
                    }
                }
                song.videoId?.let {
                    if (isNotEmpty()) append(" • ")
                    append("YouTube")
                }
            }
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SongCover(song: OfflineSong, size: Dp, corner: Dp) {
    val context = LocalContext.current
    var bitmap by remember(song.id, song.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.id, song.uri) {
        bitmap = withContext(Dispatchers.IO) { decodeCover(context, song) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

private fun decodeCover(context: android.content.Context, song: OfflineSong): Bitmap? {
    song.coverFile?.let { file ->
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 256)
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()?.let { return it }
    }
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.uri)
            retriever.embeddedPicture?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}

@Composable
private fun FullNowPlayingOverlay(
    song: OfflineSong,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    scrubMs: Int,
    queuePosition: Int,
    queueSize: Int,
    onScrub: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: collapse chevron + label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Large cover art
            FullPlayerCover(song = song)

            Spacer(Modifier.height(32.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            val artist = song.artist.ifBlank { "Unknown artist" }
            val album = song.album.ifBlank { "" }.takeIf { it.isNotBlank() }
            Text(
                text = if (album != null) "$artist • $album" else artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(24.dp))

            Slider(
                value = (if (scrubMs >= 0) scrubMs else positionMs)
                    .toFloat()
                    .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { onScrub(it.toInt()) },
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatTime(if (scrubMs >= 0) scrubMs else positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.width(20.dp))
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (queueSize > 0) "$queuePosition of $queueSize" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun FullPlayerCover(song: OfflineSong) {
    val context = LocalContext.current
    var bitmap by remember(song.id, song.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.id, song.uri) {
        bitmap = withContext(Dispatchers.IO) { decodeCover(context, song) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(96.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingBar(
    modifier: Modifier = Modifier,
    song: OfflineSong,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    scrubMs: Int,
    onScrub: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongCover(song = song, size = 44.dp, corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPrev) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = (if (scrubMs >= 0) scrubMs else positionMs)
                    .toFloat()
                    .coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { onScrub(it.toInt()) },
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
            Row {
                Text(
                    text = formatTime(if (scrubMs >= 0) scrubMs else positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Headset plugged in while the streaming app is up -> hand over to offline mode. */
private fun enterOfflineViaHeadset(context: Context) {
    val prefs = context.getSharedPreferences("spotiBat_prefs", Context.MODE_PRIVATE)
    if (prefs.getBoolean("OfflineMode", false)) return
    prefs.edit()
        .putBoolean("OfflineMode", true)
        .putBoolean("ServiceOn", false)
        .apply()
    context.startActivity(
        Intent(context, com.mwask.bat.ui.SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    )
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun formatSeconds(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
