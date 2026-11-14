package com.quackify.compose.shell

import android.content.Context
import androidx.compose.runtime.Composable
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.PlaylistPermissions
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.SearchHit
import com.quackify.compose.format.formatCount
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.share.Share
import com.quackify.compose.sheets.AddToPlaylistSheet
import com.quackify.compose.sheets.ArtistAboutSheet
import com.quackify.compose.sheets.CreditsSheet
import com.quackify.compose.sheets.DiscographySheet
import com.quackify.compose.sheets.EncoreCreateNameDialog
import com.quackify.compose.sheets.PlaylistContributorsSheet
import com.quackify.compose.sheets.ProfileFollowSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

internal class ShellOverlaysEnv(
    val flags: ShellUiFlags,
    val context: Context,
    val local: ShellLocalState,
    val active: ShellActiveEntity,
    val navController: ShellNavController,
    val actions: ShellActions,
    val shellScope: CoroutineScope,
    val playbackState: ComposePlaybackState?,
    val playerListener: ComposePlayerListener?,
    val isLiked: Boolean,
    val isPlayerExpanded: Boolean,
    val setIsPlayerExpanded: (Boolean) -> Unit,
    val pushCurrentState: () -> Unit,
    val setCurrentScreen: (ShellScreen) -> Unit,
    val profileFollowers: List<ProfileUser>,
    val profileFollowing: List<ProfileUser>,
    val profileFollowLoading: Boolean,
    val playlistPermissions: PlaylistPermissions?,
    val contributorsLoading: Boolean,
    val contributorBusyIds: Set<String>,
    val onLoadPlaylistContributors: (() -> Unit)?,
    val onSearchUsers: (suspend (String) -> List<SearchHit>)?,
    val onAddPlaylistContributor: ((String) -> Unit)?,
    val onRemovePlaylistContributor: ((String) -> Unit)?,
    val onRemovePlaylistMember: ((String) -> Unit)?,
    val artistDetail: ArtistDetail?,
    val trackCredits: Credits?,
    val addToPlaylistChoices: List<PlaylistChoice>?,
    val albumTracks: List<TrackItem>?,
    val onPickPlaylist: ((PlaylistChoice, TrackItem) -> Unit)?,
    val onTogglePlaylist: ((PlaylistChoice, TrackItem, Boolean) -> Unit)?,
    val onLoadPlaylistFolder: (suspend (trackUri: String, folderUri: String) -> List<PlaylistChoice>)?,
    val onLoadTrackCount: (suspend (playlistUri: String) -> Int)?,
    val onCreatePlaylistWith: ((TrackItem) -> Unit)?,
    val openArtist: (String, String) -> Unit,
    val openAlbum: (String, String, String) -> Unit,
    val openUserProfile: (String, String) -> Unit,
    val playlistEditDraft: PlaylistEditDraft?,
    val onEditPlaylistDetails: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    val onDismissEditPlaylist: () -> Unit,
    val onDeletePlaylist: (suspend (String) -> Boolean)?,
    val onAddToPlaylist: ((TrackItem) -> Unit)?,
    val libraryFolderChoices: List<Pair<String, String>>,
    val editablePlaylistUris: Set<String>,
    val folderItems: List<LibraryItem>?,
    val onEditPlaylist: ((String) -> Unit)?,
    val onRemoveLibraryItem: ((String) -> Unit)?,
    val onSetLibraryItemPinned: ((String, Boolean) -> Unit)?,
    val onMoveToFolder: ((String, String) -> Unit)?,
    val onCreateFolder: (suspend (String) -> String?)?,
    val onRenameFolder: ((String, String) -> Unit)?,
    val onDeleteFolder: ((String) -> Unit)?,
    val onQueueAdd: (String) -> Unit,
    val onLoadPlaylist: ((String) -> Unit)?,
    val onPlaylistDefaultName: (suspend () -> String)?,
    val onCreatePlaylist: (suspend (name: String, description: String, cover: ByteArray?) -> String?)?,
    val playlistDescription: String,
)

/**
 * The conditional overlays that ride on top of the shell (profile follows,
 * contributors, artist about, discography, add-to-playlist, credits, the
 * playlist creation dialog, playlist edit sheets and the library entity
 * sheets), extracted from EncoreAppShell so that composable stays under the
 * JIT method-size limit.
 */
@Composable
internal fun ShellOverlaysHost(env: ShellOverlaysEnv) {
    ShellOverlaySheets(
        flags = env.flags,
        context = env.context,
        local = env.local,
        active = env.active,
        playbackState = env.playbackState,
        isLiked = env.isLiked,
        profileFollowers = env.profileFollowers,
        profileFollowing = env.profileFollowing,
        profileFollowLoading = env.profileFollowLoading,
        playlistPermissions = env.playlistPermissions,
        contributorsLoading = env.contributorsLoading,
        contributorBusyIds = env.contributorBusyIds,
        onLoadPlaylistContributors = env.onLoadPlaylistContributors,
        onSearchUsers = env.onSearchUsers,
        onAddPlaylistContributor = env.onAddPlaylistContributor,
        onRemovePlaylistContributor = env.onRemovePlaylistContributor,
        onRemovePlaylistMember = env.onRemovePlaylistMember,
        artistDetail = env.artistDetail,
        trackCredits = env.trackCredits,
        addToPlaylistChoices = env.addToPlaylistChoices,
        albumTracks = env.albumTracks,
        onPickPlaylist = env.onPickPlaylist,
        onTogglePlaylist = env.onTogglePlaylist,
        onLoadPlaylistFolder = env.onLoadPlaylistFolder,
        onLoadTrackCount = env.onLoadTrackCount,
        onCreatePlaylistWith = env.onCreatePlaylistWith,
        openArtist = env.openArtist,
        openAlbum = env.openAlbum,
        openUserProfile = env.openUserProfile,
    )

    EncoreCreateNameDialog(
        isOpen = env.flags.isCreatePlaylistOpen,
        title = stringResource(R.string.give_your_playlist_a_name),
        initialName = env.local.playlistCreateName,
        confirmLabel = stringResource(R.string.create),
        cancelLabel = stringResource(R.string.cancel),
        onConfirm = { name ->
            env.flags.isCreatePlaylistOpen = false
            env.shellScope.launch {
                val uri = env.onCreatePlaylist?.invoke(name, "", null)
                if (!uri.isNullOrEmpty()) {
                    env.pushCurrentState()
                    env.active.playlistTitle = name
                    env.active.playlistUri = uri
                    env.setCurrentScreen(ShellScreen.PLAYLIST)
                    if (env.onLoadPlaylist != null) env.onLoadPlaylist.invoke(uri) else env.playerListener?.onLoadPlaylist(uri)
                    if (env.active.folderUri.isNotBlank()) {
                        env.onMoveToFolder?.invoke(uri, env.active.folderUri)
                    }
                }
            }
        },
        onDismiss = { env.flags.isCreatePlaylistOpen = false }
    )

    ShellPlaylistEditSheets(
        editDraft = env.playlistEditDraft,
        onEditSave = env.onEditPlaylistDetails,
        onDismissEdit = env.onDismissEditPlaylist,
        onDeletePlaylist = env.onDeletePlaylist,
        onDeleteAsk = { title, message, onConfirm ->
            env.local.deleteAsk = DeleteAsk(title, message, onConfirm)
        }
    )

    ShellEntitySheets(
        flags = env.flags,
        local = env.local,
        active = env.active,
        navController = env.navController,
        actions = env.actions,
        context = env.context,
        shellScope = env.shellScope,
        libraryFolderChoices = env.libraryFolderChoices,
        editablePlaylistUris = env.editablePlaylistUris,
        playlistDescription = env.playlistDescription,        albumTracks = env.albumTracks,
        folderItems = env.folderItems,
        onEditPlaylist = env.onEditPlaylist,
        onRemoveLibraryItem = env.onRemoveLibraryItem,
        onSetLibraryItemPinned = env.onSetLibraryItemPinned,
        onDeletePlaylist = env.onDeletePlaylist,
        onMoveToFolder = env.onMoveToFolder,
        onCreateFolder = env.onCreateFolder,
        onRenameFolder = env.onRenameFolder,
        onDeleteFolder = env.onDeleteFolder,
        onEditPlaylistDetails = env.onEditPlaylistDetails,
        onCopyEntityLink = { uri -> env.actions.copyEntityLink(uri) },
        onShareEntity = { uri -> env.actions.shareEntity(env.context, uri) },
        onQueueAdd = env.onQueueAdd,
        playerListener = env.playerListener,
    )
}

/**
 * The conditional overlay sheets that ride on top of the shell (profile
 * follows, playlist contributors, artist about / discography, add-to-playlist
 * and credits). Extracted from EncoreAppShell so that composable stays under
 * the compiler's per-method size limit.
 */
@Composable
internal fun ShellOverlaySheets(
    flags: ShellUiFlags,
    context: Context,
    local: ShellLocalState,
    active: ShellActiveEntity,
    playbackState: ComposePlaybackState?,
    isLiked: Boolean,
    profileFollowers: List<ProfileUser>,
    profileFollowing: List<ProfileUser>,
    profileFollowLoading: Boolean,
    playlistPermissions: PlaylistPermissions?,
    contributorsLoading: Boolean,
    contributorBusyIds: Set<String>,
    onLoadPlaylistContributors: (() -> Unit)?,
    onSearchUsers: (suspend (String) -> List<SearchHit>)?,
    onAddPlaylistContributor: ((String) -> Unit)?,
    onRemovePlaylistContributor: ((String) -> Unit)?,
    onRemovePlaylistMember: ((String) -> Unit)?,
    artistDetail: ArtistDetail?,
    trackCredits: Credits?,
    addToPlaylistChoices: List<PlaylistChoice>?,
    albumTracks: List<TrackItem>?,
    onPickPlaylist: ((PlaylistChoice, TrackItem) -> Unit)?,
    onTogglePlaylist: ((PlaylistChoice, TrackItem, Boolean) -> Unit)?,
    onLoadPlaylistFolder: (suspend (trackUri: String, folderUri: String) -> List<PlaylistChoice>)?,
    onLoadTrackCount: (suspend (playlistUri: String) -> Int)?,
    onCreatePlaylistWith: ((TrackItem) -> Unit)?,
    openArtist: (String, String) -> Unit,
    openAlbum: (String, String, String) -> Unit,
    openUserProfile: (String, String) -> Unit,
) {
    if (flags.isProfileFollowOpen) {
        ProfileFollowSheet(
            isOpen = true,
            onDismiss = { flags.isProfileFollowOpen = false },
            followers = profileFollowers,
            following = profileFollowing,
            loading = profileFollowLoading,
            onArtistClick = { artist, uri ->
                flags.isProfileFollowOpen = false
                openArtist(artist, uri)
            }
        )
    }

    if (flags.isContributorsOpen) {
        PlaylistContributorsSheet(
            isOpen = true,
            onDismiss = { flags.isContributorsOpen = false },
            permissions = playlistPermissions,
            loading = contributorsLoading,
            onRetry = { onLoadPlaylistContributors?.invoke() },
            busyUserIds = contributorBusyIds,
            onSearchUsers = onSearchUsers,
            onAddContributor = { userId -> onAddPlaylistContributor?.invoke(userId) },
            onRemoveAsContributor = { userId -> onRemovePlaylistContributor?.invoke(userId) },
            onRemoveFromPlaylist = { userId -> onRemovePlaylistMember?.invoke(userId) },
            onOpenProfile = { uri, name ->
                flags.isContributorsOpen = false
                openUserProfile(uri, name)
            }
        )
    }

    if (flags.isArtistAboutOpen) {
        ArtistAboutSheet(
            isOpen = flags.isArtistAboutOpen,
            artistName = active.artistName,
            imageUrl = artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() },
            monthlyListeners = artistDetail?.monthlyListeners?.takeIf { it > 0L }
                ?.let { formatCount(it) + stringResource(R.string.monthly_listeners_prefix) }.orEmpty(),
            followers = artistDetail?.followers?.takeIf { it > 0L }
                ?.let { formatCount(it) + stringResource(R.string.followers_suffix) }.orEmpty(),
            worldRank = artistDetail?.worldRank ?: 0,
            biography = artistDetail?.biography.orEmpty(),
            topCities = artistDetail?.topCities.orEmpty(),
            externalLinks = artistDetail?.externalLinks.orEmpty(),
            gallery = artistDetail?.gallery.orEmpty(),
            onDismiss = { flags.isArtistAboutOpen = false },
            onLinkClick = { link -> Share.openUrl(context, link) }
        )
    }

    if (flags.isDiscographyOpen) {
        DiscographySheet(
            isOpen = flags.isDiscographyOpen,
            artistUri = active.artistUri,
            artistName = active.artistName,
            onDismiss = { flags.isDiscographyOpen = false },
            onOpenRelease = { uri, title -> openAlbum(title, active.artistName, uri) }
        )
    }

    if (flags.isAddToPlaylistOpen) {
        val target = local.addTrackTarget
        val targetIsLiked = if (target?.uri == playbackState?.trackUri) isLiked else (target?.saved == true)
        AddToPlaylistSheet(
            isOpen = true,
            trackTitle = target?.title.orEmpty(),
            isTargetLiked = targetIsLiked,
            choices = addToPlaylistChoices.orEmpty(),
            isLoading = addToPlaylistChoices == null,
            onPick = { choice ->
                val target = local.addTrackTarget
                if (target != null) {
                    if (target.uri.startsWith("spotify:album:")) {
                        val rows = albumTracks.orEmpty()
                        rows.forEach { row -> onPickPlaylist?.invoke(choice, row) }
                    } else {
                        onPickPlaylist?.invoke(choice, target)
                    }
                }
            },
            onToggle = { choice, isAdding ->
                val target = local.addTrackTarget
                if (target != null) {
                    if (target.uri.startsWith("spotify:album:")) {
                        val rows = albumTracks.orEmpty()
                        rows.forEach { row ->
                            onTogglePlaylist?.invoke(choice, row, isAdding)
                                ?: if (isAdding) onPickPlaylist?.invoke(choice, row) else Unit
                        }
                    } else {
                        onTogglePlaylist?.invoke(choice, target, isAdding)
                            ?: if (isAdding) onPickPlaylist?.invoke(choice, target) else Unit
                    }
                }
            },
            onLoadFolder = onLoadPlaylistFolder?.let { loader ->
                { folderUri -> loader(local.addTrackTarget?.uri.orEmpty(), folderUri) }
            },
            onLoadTrackCount = onLoadTrackCount,
            onCreateNew = {
                local.addTrackTarget?.let { track -> onCreatePlaylistWith?.invoke(track) }
                flags.isAddToPlaylistOpen = false
            },
            onDismiss = { flags.isAddToPlaylistOpen = false }
        )
    }

    if (flags.isCreditsOpen) {
        CreditsSheet(
            isOpen = true,
            title = local.creditsTrack?.title.orEmpty(),
            subtitle = local.creditsTrack?.artist.orEmpty(),
            imageUrl = local.creditsTrack?.imageUrl,
            credits = trackCredits,
            onDismiss = { flags.isCreditsOpen = false },
            onArtistClick = { uri, name -> openArtist(name, uri) }
        )
    }
}
