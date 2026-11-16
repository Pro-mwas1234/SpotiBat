package com.mwask.bat.compose.playlist

data class EditableTrackItem(
    val id: String,
    val track: TrackItem
)

sealed interface PlaylistEditAction {
    data class Remove(val index: Int, val item: EditableTrackItem) : PlaylistEditAction
    data class Move(val fromIndex: Int, val toIndex: Int) : PlaylistEditAction
    data class BulkOrder(val previousList: List<EditableTrackItem>, val newList: List<EditableTrackItem>) : PlaylistEditAction
}
