package com.quackify.compose.shell

import android.content.Context
import androidx.compose.runtime.Composable
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.sheets.DeleteConfirmDialog
import com.quackify.compose.sheets.FolderOptionsSheet
import com.quackify.compose.sheets.FolderSheet
import com.quackify.compose.sheets.EntityOptionsSheet
import com.quackify.compose.sheets.EncoreCreateNameDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

@Composable
internal fun ShellEntitySheets(
    flags: ShellUiFlags,
    local: ShellLocalState,
    active: ShellActiveEntity,
    navController: ShellNavController,
    actions: ShellActions,
    context: Context,
    shellScope: CoroutineScope,
    libraryFolderChoices: List<Pair<String, String>>,
    editablePlaylistUris: Set<String>,
    playlistDescription: String,
    albumTracks: List<TrackItem>?,
    folderItems: List<LibraryItem>?,
    onEditPlaylist: ((String) -> Unit)?,
    onRemoveLibraryItem: ((String) -> Unit)?,
    onSetLibraryItemPinned: ((String, Boolean) -> Unit)?,
    onDeletePlaylist: (suspend (String) -> Boolean)?,
    onMoveToFolder: ((String, String) -> Unit)?,
    onCreateFolder: (suspend (String) -> String?)?,
    onRenameFolder: ((String, String) -> Unit)?,
    onDeleteFolder: ((String) -> Unit)?,
    onEditPlaylistDetails: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onCopyEntityLink: (String) -> Unit,
    onShareEntity: (String) -> Unit,
    onQueueAdd: (String) -> Unit,
    playerListener: ComposePlayerListener?,
) {
    local.libraryItemOptions?.let { entity ->
        val editable = entity.uri in editablePlaylistUris
        val removable = isCollectionUri(entity.uri).not()
        val deletePlaylistAskTitle = stringResource(R.string.delete_playlist_title)
        val deletePlaylistAskSuffix = stringResource(R.string.will_be_deleted_from_library)
        EntityOptionsSheet(
            entity = entity,
            isOpen = true,
            onDismiss = { local.libraryItemOptions = null },
            canEditPlaylist = editable,
            onEditPlaylist = { onEditPlaylist?.invoke(entity.uri) },
            canPin = entity.pinnable,
            isPinned = entity.pinned,
            onPinToggle = { onSetLibraryItemPinned?.invoke(entity.uri, !entity.pinned) },
            isSaved = if (removable) true else null,
            onSaveToggle = { if (removable) onRemoveLibraryItem?.invoke(entity.uri) },
            canMoveToFolder = entity.uri.startsWith("spotify:playlist:"),
            onMoveToFolder = {
                local.folderDraft = FolderDraft(
                    mode = FolderMode.PICKER,
                    folderUri = entity.uri,
                    title = entity.title,
                    folders = libraryFolderChoices,
                )
            },
            canDeletePlaylist = editable,
            onDeletePlaylist = {
                val uri = entity.uri
                val title = entity.title
                local.libraryItemOptions = null
                local.deleteAsk = DeleteAsk(
                    title = deletePlaylistAskTitle,
                    message = "\"$title\"" + deletePlaylistAskSuffix,
                    onConfirm = {
                        shellScope.launch {
                            onDeletePlaylist?.invoke(uri)
                        }
                    }
                )
            },
            onCopyLink = { onCopyEntityLink(entity.uri) },
            onAddToQueue = when {
                entity.uri.startsWith("spotify:episode:") -> {
                    { onQueueAdd(entity.uri) }
                }
                entity.uri.startsWith("spotify:album:") -> {
                    {
                        val rows = albumTracks.orEmpty()
                        if (rows.isEmpty()) {
                            onQueueAdd(entity.uri)
                        } else {
                            rows.forEach { row -> onQueueAdd(row.uri) }
                        }
                    }
                }
                else -> null
            },
            onShare = { onShareEntity(entity.uri) }
        )
    }

    local.folderDraft?.let { draft ->
        if (draft.mode == FolderMode.PICKER) {
            FolderSheet(
                draft = draft,
                canRemoveFromFolder = local.activeEntityInsideFolder,
                onDismiss = { local.folderDraft = null },
                onSubmit = {},
                onPick = { folderUri ->
                    local.folderDraft = null
                    onMoveToFolder?.invoke(draft.folderUri, folderUri)
                }
            )
        } else {
            EncoreCreateNameDialog(
                isOpen = true,
                title = if (draft.mode == FolderMode.CREATE) "Give your folder a name" else stringResource(R.string.rename_folder),
                initialName = if (draft.mode == FolderMode.CREATE) stringResource(R.string.new_folder) else draft.title,
                confirmLabel = if (draft.mode == FolderMode.CREATE) stringResource(R.string.create) else stringResource(R.string.save),
                cancelLabel = stringResource(R.string.cancel),
                onConfirm = { name ->
                    val currentDraft = local.folderDraft
                    local.folderDraft = null
                    if (currentDraft?.mode == FolderMode.CREATE) {
                        shellScope.launch {
                            onCreateFolder?.invoke(name)
                        }
                    } else if (currentDraft?.mode == FolderMode.RENAME) {
                        if (currentDraft.folderUri == active.folderUri) {
                            active.folderTitle = name
                        }
                        onRenameFolder?.invoke(currentDraft.folderUri, name)
                    }
                },
                onDismiss = { local.folderDraft = null }
            )
        }
    }

    local.activeFolderOptionsItem?.let { folderItem ->
        val deleteFolderAskTitle = stringResource(R.string.delete_folder_title)
        val deleteFolderAskSuffix = stringResource(R.string.will_be_deleted_playlists_stay)
        FolderOptionsSheet(
            isOpen = true,
            folderTitle = folderItem.title,
            playlistSubtitle = folderItem.subtitle.ifBlank { stringResource(R.string.zero_playlists) },
            isNested = local.activeFolderOptionsNested,
            isPinned = folderItem.isPinned,
            canPin = folderItem.isPinnable,
            onPinToggle = { onSetLibraryItemPinned?.invoke(folderItem.uri, !folderItem.isPinned) },
            onMoveToFolder = {
                val targetUri = folderItem.uri
                val targetTitle = folderItem.title
                local.activeFolderOptionsItem = null
                local.folderDraft = FolderDraft(
                    mode = FolderMode.PICKER,
                    folderUri = targetUri,
                    title = targetTitle,
                    folders = libraryFolderChoices.filter { it.first != targetUri }
                )
            },
            onRenameFolder = {
                val targetUri = folderItem.uri
                val targetTitle = folderItem.title
                local.activeFolderOptionsItem = null
                local.folderDraft = FolderDraft(
                    mode = FolderMode.RENAME,
                    folderUri = targetUri,
                    title = targetTitle
                )
            },
            onMoveToLibrary = {
                val targetUri = folderItem.uri
                local.activeFolderOptionsItem = null
                onMoveToFolder?.invoke(targetUri, "")
            },
            onDeleteFolder = {
                val targetUri = folderItem.uri
                local.activeFolderOptionsItem = null
                local.deleteAsk = DeleteAsk(
                    title = deleteFolderAskTitle,
                    message = "\"${folderItem.title}\"" + deleteFolderAskSuffix,
                    onConfirm = {
                        onDeleteFolder?.invoke(targetUri)
                        if (navController.currentScreen == ShellScreen.FOLDER && active.folderUri == targetUri) {
                            navController.handleBack()
                        }
                    }
                )
            },
            onDismiss = { local.activeFolderOptionsItem = null }
        )
    }

    EncoreCreateNameDialog(
        isOpen = flags.isRenamePlaylistDialogOpen,
        title = stringResource(R.string.give_your_playlist_a_name),
        initialName = active.playlistTitle,
        confirmLabel = stringResource(R.string.save),
        cancelLabel = stringResource(R.string.cancel),
        onConfirm = { newTitle ->
            flags.isRenamePlaylistDialogOpen = false
            val uri = active.playlistUri
            if (uri.isNotBlank()) {
                active.playlistTitle = newTitle
                shellScope.launch {
                    onEditPlaylistDetails?.invoke(uri, newTitle, playlistDescription, null, false)
                }
            }
        },
        onDismiss = { flags.isRenamePlaylistDialogOpen = false }
    )

    local.deleteAsk?.let { ask ->
        DeleteConfirmDialog(
            title = ask.title,
            message = ask.message,
            onConfirm = ask.onConfirm,
            onDismiss = { local.deleteAsk = null }
        )
    }
}
