package com.mwask.bat.compose.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.mwask.bat.compose.R
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.theme.EncoreAnimation
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.rememberBottomPadding
import com.mwask.bat.compose.widget.WidgetBackgroundStyle
import com.mwask.bat.compose.widget.WidgetOptionsStore
import androidx.compose.ui.res.stringResource

data class AppUpdate(
    val version: String,
    val url: String,
    val notes: String = "",
)

data class SettingsSwitches(
    val musicVideos: Boolean = true,
    val canvas: Boolean = true,
    val otherVideos: Boolean = true,
    val showFollows: Boolean = true,
    val showPlaylists: Boolean = true,
)

data class SettingsLocks(
    val musicVideos: Boolean = false,
    val canvas: Boolean = false,
    val otherVideos: Boolean = false,
)

/** A folder downloaded songs can be kept in, as offered by the settings picker. */
data class StorageOption(
    val id: String,
    val label: String,
    val path: String,
    val availableBytes: Long,
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    switches: SettingsSwitches = SettingsSwitches(),
    locks: SettingsLocks = SettingsLocks(),
    product: String = "",
    country: String = "",
    loading: Boolean = false,
    onToggleMusicVideos: (Boolean) -> Unit = {},
    onToggleCanvas: (Boolean) -> Unit = {},
    onToggleOtherVideos: (Boolean) -> Unit = {},
    onToggleShowFollows: (Boolean) -> Unit = {},
    onToggleShowPlaylists: (Boolean) -> Unit = {},
    update: AppUpdate? = null,
    updateChecking: Boolean = false,
    updateChecked: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    onAppIconChange: (String) -> Unit = {},
    storageOptions: List<StorageOption> = emptyList(),
    selectedStorageId: String = "",
    onSelectStorage: ((StorageOption) -> Unit)? = null,
) {
    val bottomPadding = rememberBottomPadding()
    val context = LocalContext.current
    val account = remember { EncoreAuth.account(context) }
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName.orEmpty()} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }

    val clientPrefs = remember {
        com.mwask.bat.backend.core.store.AppPrefs.prefs(context, "quackify.client")
    }
    var autoplayOnStartup by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnStartup", false))
    }
    var autoplayOnEnd by remember {
        mutableStateOf(clientPrefs.getBoolean("autoplayOnEnd", true))
    }
    var gapless by remember {
        mutableStateOf(clientPrefs.getBoolean("gaplessPlayback", false))
    }
    var appIcon by remember {
        mutableStateOf(clientPrefs.getString("appIcon", "default").orEmpty().ifBlank { "default" })
    }
    var widgetOptions by remember { mutableStateOf(WidgetOptionsStore.load(context)) }
    var category by remember { mutableStateOf<Int?>(null) }
    var storagePicker by remember { mutableStateOf(false) }
    val selectedStorage = storageOptions.firstOrNull { it.id == selectedStorageId }
        ?: storageOptions.firstOrNull()

    BackHandler(enabled = category != null) {
        category = null
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = stringResource(R.string.back),
                onClick = {
                    if (category == null) onBackClick() else category = null
                },
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (category) {
                    0 -> stringResource(R.string.general)
                    1 -> stringResource(R.string.personalization)
                    2 -> stringResource(R.string.more)
                    else -> stringResource(R.string.settings)
                },
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        AnimatedContent(
            targetState = category,
            transitionSpec = {
                val forward = (initialState ?: -1) < (targetState ?: -1)
                (
                    fadeIn(tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)) +
                        slideInHorizontally(
                            tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)
                        ) { full -> if (forward) full / 6 else -full / 6 }
                    ) togetherWith (
                    fadeOut(tween(EncoreAnimation.ScreenExitDuration)) +
                        slideOutHorizontally(tween(EncoreAnimation.ScreenExitDuration)) { full ->
                            if (forward) -full / 8 else full / 8
                        }
                    )
            },
            label = "SettingsCategoryTransition"
        ) { target ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            ) {
                if (target == null) {
                    item {
                        ProfileSummary(account)
                        HorizontalDivider(
                            color = EncoreColors.Card,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    item {
                        SettingsCategoryRow(
                            title = stringResource(R.string.general),
                            description = stringResource(R.string.videos_profile_visibility_and_account),
                            onClick = { category = 0 }
                        )
                        SettingsCategoryRow(
                            title = stringResource(R.string.personalization),
                            description = stringResource(R.string.app_icon_and_home_screen_widgets),
                            onClick = { category = 1 }
                        )
                        SettingsCategoryRow(
                            title = stringResource(R.string.more),
                            description = stringResource(R.string.playback_and_app_information),
                            onClick = { category = 2 }
                        )
                    }
                }

                if (target == 0) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.videos_and_canvas))
                        Text(
                            text = stringResource(R.string.it_may_take_some_time_for_your_experience_to_upd),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.music_videos),
                            subtitle = stringResource(R.string.when_off_music_videos_and_live_performances_play),
                            isChecked = switches.musicVideos,
                            enabled = !locks.musicVideos && !loading,
                            onCheckedChange = onToggleMusicVideos
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.canvas),
                            subtitle = stringResource(R.string.short_looping_visuals_when_a_song_is_playing),
                            isChecked = switches.canvas,
                            enabled = !locks.canvas && !loading,
                            onCheckedChange = onToggleCanvas
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.other_videos),
                            subtitle = stringResource(R.string.video_podcasts_and_videos_from_creators_video_ad),
                            isChecked = switches.otherVideos,
                            enabled = !locks.otherVideos && !loading,
                            onCheckedChange = onToggleOtherVideos
                        )
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.what_others_can_see_on_your_profile))
                        SettingsToggleRow(
                            title = stringResource(R.string.followers_and_following),
                            subtitle = stringResource(R.string.on_your_profile_people_can_see_who_s_following_y),
                            isChecked = switches.showFollows,
                            enabled = !loading,
                            onCheckedChange = onToggleShowFollows
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.public_playlists),
                            subtitle = stringResource(R.string.people_can_see_the_playlists_you_ve_added_to_you),
                            isChecked = switches.showPlaylists,
                            enabled = !loading,
                            onCheckedChange = onToggleShowPlaylists
                        )
                    }

                }
                if (target == 1) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.widget))
                        Text(
                            text = stringResource(R.string.home_screen_widgets_come_in_4x1_3x2_and_3x3_size),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.show_artwork),
                            subtitle = stringResource(R.string.display_the_album_cover_on_the_widgets),
                            isChecked = widgetOptions.showArtwork,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showArtwork = on)
                                WidgetOptionsStore.saveShowArtwork(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.show_like_button),
                            subtitle = stringResource(R.string.keep_the_heart_beside_the_playback_controls),
                            isChecked = widgetOptions.showLike,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showLike = on)
                                WidgetOptionsStore.saveShowLike(context, on)
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.show_progress_bar),
                            subtitle = stringResource(R.string.a_thin_playback_line_on_the_3x2_and_3x3_widgets),
                            isChecked = widgetOptions.showProgress,
                            enabled = true,
                            onCheckedChange = { on ->
                                widgetOptions = widgetOptions.copy(showProgress = on)
                                WidgetOptionsStore.saveShowProgress(context, on)
                            }
                        )
                        Text(
                            text = stringResource(R.string.background),
                            style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsChipOption(
                                label = stringResource(R.string.dark),
                                selected = widgetOptions.background == WidgetBackgroundStyle.DARK,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.DARK)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.DARK)
                                }
                            )
                            SettingsChipOption(
                                label = stringResource(R.string.artwork_tint),
                                selected = widgetOptions.background == WidgetBackgroundStyle.TINTED,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.TINTED)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.TINTED)
                                }
                            )
                            SettingsChipOption(
                                label = stringResource(R.string.transparent),
                                selected = widgetOptions.background == WidgetBackgroundStyle.TRANSPARENT,
                                onClick = {
                                    widgetOptions = widgetOptions.copy(background = WidgetBackgroundStyle.TRANSPARENT)
                                    WidgetOptionsStore.saveBackground(context, WidgetBackgroundStyle.TRANSPARENT)
                                }
                            )
                        }
                    }

                }
                if (target == 0) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.account))
                        val detail = listOfNotNull(
                            product.takeIf { it.isNotBlank() },
                            country.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (detail.isNotBlank()) {
                            SettingsInfoRow(title = stringResource(R.string.plan), value = detail)
                        }
                        Text(
                            text = stringResource(R.string.log_out),
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLogout() }
                                .padding(vertical = 12.dp)
                        )
                    }

                }
                if (target == 2 && storageOptions.isNotEmpty()) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.storage))
                        val selected = selectedStorage ?: StorageOption("", "", "", 0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { storagePicker = true }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.download_location),
                                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = selected.label + " · " + stringResource(
                                        R.string.free_space,
                                        formatStorageSize(selected.availableBytes)
                                    ),
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.encore_icon_chevron_right_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    item {
                        if (storagePicker) {
                            StorageLocationDialog(
                                options = storageOptions,
                                selectedId = selectedStorage?.id.orEmpty(),
                                onDismiss = { storagePicker = false },
                                onSelect = { option ->
                                    storagePicker = false
                                    onSelectStorage?.invoke(option)
                                }
                            )
                        }
                    }
                }

                if (target == 2) {
                    item {
                        SettingsSectionHeader(title = stringResource(R.string.playback))
                        SettingsToggleRow(
                            title = stringResource(R.string.autoplay_on_startup),
                            subtitle = stringResource(R.string.when_you_open_the_app_the_loaded_track_starts_pl),
                            isChecked = autoplayOnStartup,
                            enabled = true,
                            onCheckedChange = { on ->
                                autoplayOnStartup = on
                                clientPrefs.edit().putBoolean("autoplayOnStartup", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.autoplay),
                            subtitle = stringResource(R.string.keep_playing_a_station_when_the_album_or_playlis),
                            isChecked = autoplayOnEnd,
                            enabled = true,
                            onCheckedChange = { on ->
                                autoplayOnEnd = on
                                clientPrefs.edit().putBoolean("autoplayOnEnd", on).apply()
                            }
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.gapless_playback),
                            subtitle = stringResource(R.string.prepares_the_next_track_while_the_current_one_pl),
                            isChecked = gapless,
                            enabled = true,
                            badge = stringResource(R.string.beta),
                            badgeInfo = stringResource(R.string.gapless_warning),
                            onCheckedChange = { on ->
                                gapless = on
                                clientPrefs.edit().putBoolean("gaplessPlayback", on).apply()
                            }
                        )
                    }

                    item {
                        SettingsSectionHeader(title = stringResource(R.string.about))
                        Text(
                            text = stringResource(R.string.spotibat_is_a_native_android_music_client_built),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SettingsInfoRow(
                            title = stringResource(R.string.version),
                            value = version,
                        )
                        val context = LocalContext.current
                        if (update != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Card)
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(update.url))
                                            )
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.update_available),
                                        style = EncoreTheme.typography.bodyMediumBold
                                    )
                                    Text(
                                        text = update.version,
                                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.download),
                                    style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.latest_version),
                                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = when {
                                        updateChecking -> "Checking\u2026"
                                        updateChecked -> stringResource(R.string.up_to_date)
                                        else -> "\u2014"
                                    },
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                            Text(
                                text = stringResource(R.string.check),
                                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EncoreColors.Card)
                                    .clickable { onCheckUpdates() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AboutLinkButton(label = stringResource(R.string.github), iconRes = R.drawable.ic_github_24, url = GITHUB_URL)
                        }
                        Text(
                            text = stringResource(R.string.copyright),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/Pro-mwas1234/SpotiBat"
@Composable
private fun AboutLinkButton(label: String, iconRes: Int, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EncoreColors.Card)
            .clickable {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = EncoreColors.TextPrimary
        )
        Text(
            text = label,
            style = EncoreTheme.typography.bodyMediumBold
        )
    }
}

@Composable
private fun ProfileSummary(account: EncoreAuth.Account?) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(EncoreColors.Card),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = account?.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                )
            )
            val avatar = account?.imageUrl.orEmpty()
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.not_signed_in),
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            val detail = account?.email?.takeIf { it.isNotBlank() }
                ?: account?.product?.replaceFirstChar { it.uppercase() }.orEmpty()
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = EncoreTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White
        ),
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    badge: String = "",
    badgeInfo: String = "",
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) EncoreColors.TextPrimary else EncoreColors.TextSecondary
                    )
                )
                if (badge.isNotBlank() && badgeInfo.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SettingsBadge(text = badge, info = badgeInfo)
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                )
            }
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EncoreColors.Green,
                uncheckedThumbColor = EncoreColors.TextSecondary,
                uncheckedTrackColor = EncoreColors.Card
            )
        )
    }
}

@Composable
private fun SettingsBadge(text: String, info: String) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        delay(4500)
        open = false
    }
    Box {
        Text(
            text = text,
            style = EncoreTheme.typography.marginal.copy(
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = EncoreColors.PureBlack
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(EncoreColors.Beta)
                .clickable { open = !open }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(-10, 24),
                onDismissRequest = { open = false }
            ) {
                val arrowHeight = with(density) { 7.dp.toPx() }
                val arrowWidth = with(density) { 14.dp.toPx() }
                val arrowColor = EncoreColors.CardElevated.copy(alpha = 0.94f)
                Column {
                    Canvas(
                        modifier = Modifier
                            .padding(start = with(density) { 14.dp })
                            .size(width = 14.dp, height = 7.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, arrowHeight)
                            lineTo(arrowWidth / 2f, 0f)
                            lineTo(arrowWidth, arrowHeight)
                            close()
                        }
                        drawPath(path, arrowColor)
                    }
                    Text(
                        text = info,
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextPrimary),
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(arrowColor)
                            .border(1.dp, EncoreColors.Divider, RoundedCornerShape(8.dp))
                            .clickable { open = false }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIconOption(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) EncoreColors.Green else EncoreColors.Divider,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onClick)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = EncoreTheme.typography.marginal.copy(
                color = if (selected) EncoreColors.Green else EncoreColors.TextSecondary
            )
        )
    }
}

@Composable
private fun SettingsChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = EncoreTheme.typography.bodySmall.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else EncoreColors.TextSecondary
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) EncoreColors.Green else EncoreColors.Card)
            .border(
                width = 1.dp,
                color = if (selected) EncoreColors.Green else EncoreColors.Divider,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = description,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Icon(
            painter = painterResource(R.drawable.encore_icon_chevron_right_24),
            contentDescription = null,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            text = value,
            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
        )
    }
}

/** Picks the folder downloaded songs are kept in. */
@Composable
private fun StorageLocationDialog(
    options: List<StorageOption>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (StorageOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EncoreColors.CardElevated,
        title = { Text(stringResource(R.string.download_location), color = EncoreColors.TextPrimary) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.choose_download_location),
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { option ->
                    val selected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                if (selected) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_folder_24
                            ),
                            contentDescription = null,
                            tint = if (selected) EncoreColors.Green else EncoreColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) EncoreColors.Green else EncoreColors.TextPrimary
                                )
                            )
                            Text(
                                text = stringResource(R.string.free_space, formatStorageSize(option.availableBytes)),
                                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = EncoreColors.TextPrimary)
            }
        }
    )
}

private fun formatStorageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.0f KB".format(bytes / 1024.0)
}

@Preview
@Composable
fun PreviewSettingsScreen() {
    EncoreAppTheme {
        SettingsScreen(onBackClick = {})
    }
}
