package com.mwask.bat.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mwask.bat.MainActivity
import com.mwask.bat.PlaybackHost
import com.mwask.bat.PlaybackService
import com.mwask.bat.R
import com.mwask.bat.compose.components.resolveRemoteImageUrl
import com.mwask.bat.compose.widget.WidgetBackgroundStyle
import com.mwask.bat.compose.widget.WidgetOptions
import com.mwask.bat.compose.widget.WidgetOptionsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object WidgetColors {
    val accent = Color(0xFF1ED760)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB3B3B3)
    val surfaceDark = Color(0xFF1A1A1A)
    val playIcon = Color(0xFF000000)
    val trackInactive = Color(0x26FFFFFF)
    val placeholderIcon = Color(0xFF8D8D8D)
}

enum class WidgetVariant(val cornerRadius: Dp, val padding: Dp) {
    COMPACT(cornerRadius = 16.dp, padding = 8.dp),
    MEDIUM(cornerRadius = 20.dp, padding = 12.dp),
    LARGE(cornerRadius = 24.dp, padding = 14.dp),
}

abstract class SpotiBatWidget(private val variant: WidgetVariant) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val bridge = PlaybackHost.bridge
        val live = bridge
            ?.let { WidgetSnapshot.from(it.state, it.progress) }
            ?.takeIf { it.hasTrack }
        val snapshot = WidgetSync.resolve(live, appContext)
        val options = WidgetOptionsStore.load(appContext)
        val artUrl = resolveRemoteImageUrl(snapshot.coverUrl)
        val art = artUrl?.let { url -> runCatching { WidgetArt.cached(url) }.getOrNull() }
        if (art == null && artUrl != null) {
            WidgetArt.prefetch(appContext, artUrl) { WidgetSync.refreshSoon(appContext) }
        }
        val background = resolveBackground(options, artUrl, art)
        provideContent {
            WidgetCard(variant, snapshot, options, art, background)
        }
    }

    private suspend fun resolveBackground(
        options: WidgetOptions,
        artUrl: String?,
        art: Bitmap?,
    ): Color? = when (options.background) {
        WidgetBackgroundStyle.TRANSPARENT -> null
        WidgetBackgroundStyle.DARK -> WidgetColors.surfaceDark
        WidgetBackgroundStyle.TINTED -> {
            if (artUrl == null || art == null) {
                WidgetColors.surfaceDark
            } else {
                withContext(Dispatchers.Default) {
                    runCatching {
                        Color(WidgetArt.tintFor(artUrl, art, WidgetColors.surfaceDark.toArgb()))
                    }.getOrDefault(WidgetColors.surfaceDark)
                }
            }
        }
    }
}

class SpotiBatWidget4x1 : SpotiBatWidget(WidgetVariant.COMPACT)

class SpotiBatWidget3x2 : SpotiBatWidget(WidgetVariant.MEDIUM)

class SpotiBatWidget3x3 : SpotiBatWidget(WidgetVariant.LARGE)

@Composable
private fun WidgetCard(
    variant: WidgetVariant,
    snapshot: WidgetSnapshot,
    options: WidgetOptions,
    art: Bitmap?,
    background: Color?,
) {
    val backgroundModifier = if (background != null) {
        GlanceModifier.background(ColorProvider(background))
    } else {
        GlanceModifier
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .then(backgroundModifier)
            .appWidgetBackground()
            .cornerRadius(variant.cornerRadius)
            .padding(variant.padding),
    ) {
        when (variant) {
            WidgetVariant.COMPACT -> CompactContent(snapshot, options, art)
            WidgetVariant.MEDIUM -> MediumContent(snapshot, options, art)
            WidgetVariant.LARGE -> LargeContent(snapshot, options, art)
        }
    }
}

@Composable
private fun CompactContent(snapshot: WidgetSnapshot, options: WidgetOptions, art: Bitmap?) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(art = art, options = options, size = 40.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        TrackInfo(
            snapshot = snapshot,
            titleSize = 13,
            artistSize = 11,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (options.showLike) {
                LikeButton(snapshot)
                Spacer(modifier = GlanceModifier.width(2.dp))
            }
            SkipButton(previous = true)
            Spacer(modifier = GlanceModifier.width(2.dp))
            PlayButton(snapshot, size = 34.dp, iconSize = 18.dp)
            Spacer(modifier = GlanceModifier.width(2.dp))
            SkipButton(previous = false)
        }
    }
}

@Composable
private fun MediumContent(snapshot: WidgetSnapshot, options: WidgetOptions, art: Bitmap?) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(art = art, options = options, size = 48.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            TrackInfo(
                snapshot = snapshot,
                titleSize = 15,
                artistSize = 12,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (options.showProgress && snapshot.durationMs > 0L) {
            WidgetProgress(snapshot)
            Spacer(modifier = GlanceModifier.height(10.dp))
        }
        ControlsRow(snapshot = snapshot, options = options, playSize = 42.dp)
    }
}

@Composable
private fun LargeContent(snapshot: WidgetSnapshot, options: WidgetOptions, art: Bitmap?) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            art = art,
            options = options,
            size = null,
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        TrackInfo(
            snapshot = snapshot,
            titleSize = 16,
            artistSize = 13,
            center = true,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        if (options.showProgress && snapshot.durationMs > 0L) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            WidgetProgress(snapshot)
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        ControlsRow(snapshot = snapshot, options = options, playSize = 46.dp)
    }
}

@Composable
private fun ControlsRow(snapshot: WidgetSnapshot, options: WidgetOptions, playSize: Dp) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (options.showLike) {
            LikeButton(snapshot)
            Spacer(modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        SkipButton(previous = true)
        Spacer(modifier = GlanceModifier.width(10.dp))
        PlayButton(snapshot, size = playSize, iconSize = 22.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        SkipButton(previous = false)
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun TrackInfo(
    snapshot: WidgetSnapshot,
    titleSize: Int,
    artistSize: Int,
    center: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val openPlayer = openPlayerAction()
    Column(
        modifier = modifier.clickable(openPlayer),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = snapshot.title.ifBlank { "SpotiBat" },
            style = TextStyle(
                color = ColorProvider(WidgetColors.textPrimary),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = snapshot.artist.ifBlank { if (snapshot.hasTrack) "" else "Nothing playing" },
            style = TextStyle(
                color = ColorProvider(WidgetColors.textSecondary),
                fontSize = artistSize.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Artwork(
    art: Bitmap?,
    options: WidgetOptions,
    size: Dp?,
    modifier: GlanceModifier = GlanceModifier,
) {
    val openPlayer = openPlayerAction()
    Box(
        modifier = modifier
            .then(if (size != null) GlanceModifier.size(size) else GlanceModifier)
            .clickable(openPlayer),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null && options.showArtwork) {
            Image(
                provider = ImageProvider(art),
                contentDescription = "Album artwork",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_placeholder_bg)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_media),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.placeholderIcon)),
                )
            }
        }
    }
}

@Composable
private fun LikeButton(snapshot: WidgetSnapshot) {
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .clickable(playbackAction(WidgetPlaybackAction.ACTION_LIKE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (snapshot.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart,
            ),
            contentDescription = if (snapshot.isLiked) "Unlike" else "Like",
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(
                ColorProvider(if (snapshot.isLiked) WidgetColors.accent else WidgetColors.textPrimary),
            ),
        )
    }
}

@Composable
private fun SkipButton(previous: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .clickable(
                playbackAction(
                    if (previous) WidgetPlaybackAction.ACTION_PREVIOUS else WidgetPlaybackAction.ACTION_NEXT,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (previous) R.drawable.ic_widget_skip_previous else R.drawable.ic_widget_skip_next,
            ),
            contentDescription = if (previous) "Previous" else "Next",
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.textPrimary)),
        )
    }
}

@Composable
private fun PlayButton(snapshot: WidgetSnapshot, size: Dp, iconSize: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(R.drawable.widget_play_circle))
            .clickable(playbackAction(WidgetPlaybackAction.ACTION_TOGGLE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            ),
            contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.playIcon)),
        )
    }
}

@Composable
private fun WidgetProgress(snapshot: WidgetSnapshot) {
    LinearProgressIndicator(
        progress = snapshot.progress,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        color = ColorProvider(WidgetColors.accent),
        backgroundColor = ColorProvider(WidgetColors.trackInactive),
    )
}

private fun playbackAction(action: String): Action =
    actionRunCallback<WidgetPlaybackAction>(
        actionParametersOf(WidgetPlaybackAction.ACTION_KEY to action),
    )

@Composable
private fun openPlayerAction(): Action {
    val context = LocalContext.current
    val intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .putExtra(PlaybackService.EXTRA_OPEN_PLAYER, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return actionStartActivity(intent)
}
