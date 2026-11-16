/*
 * SpotiBat — playlist contributors state holder.
 *
 * Owns the member list of the open playlist: loads it on demand when the
 * contributors sheet opens and applies add/remove mutations, refreshing the
 * list after every successful change. Kept outside MainActivity so the shell
 * composable stays small enough for the compiler's method size limit.
 */

package com.mwask.bat

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.library.Krespot
import com.mwask.bat.backend.model.PlaylistMember
import com.mwask.bat.backend.model.PlaylistPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val log = Logs.of(Logs.Module.MAIN)

class PlaylistContributorController(
    private val context: Context,
    private val playlistUri: String,
    private val scope: CoroutineScope,
) {
    var permissions by mutableStateOf<PlaylistPermissions?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var busyIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Reloads the member list; called every time the sheet opens. */
    fun refresh() {
        if (!playlistUri.startsWith("spotify:playlist:")) return
        scope.launch {
            loading = true
            val result = runCatching { Krespot.playlists.permissions(context, playlistUri) }.getOrNull()
            permissions = result
            loading = false
            log.i("contributors '$playlistUri' -> ${result?.members?.size ?: 0} member(s) " +
                    "admin=${result?.canAdministratePermissions == true}"
            )
        }
    }

    private fun mutate(userId: String, label: String, errorText: String, block: suspend () -> Boolean) {
        scope.launch {
            busyIds = busyIds + userId
            val ok = runCatching { block() }.getOrDefault(false)
            busyIds = busyIds - userId
            log.i("$label $userId on '$playlistUri' ok=$ok")
            if (ok) {
                refresh()
            } else {
                Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun add(userId: String) = mutate(userId, "add contributor", "Couldn't add contributor") {
        Krespot.playlists.setMemberPermission(context, playlistUri, userId, PlaylistMember.PERMISSION_CONTRIBUTOR)
    }

    fun removeAsContributor(userId: String) = mutate(userId, "remove contributor", "Couldn't remove contributor") {
        Krespot.playlists.setMemberPermission(context, playlistUri, userId, PlaylistMember.PERMISSION_VIEWER)
    }

    fun removeFromPlaylist(userId: String) = mutate(userId, "remove member", "Couldn't remove from playlist") {
        Krespot.playlists.removeMember(context, playlistUri, userId)
    }
}

/** One controller per open playlist; switching playlists starts from a clean state. */
@Composable
fun rememberPlaylistContributorController(context: Context, playlistUri: String): PlaylistContributorController {
    val scope = rememberCoroutineScope()
    return remember(playlistUri) {
        PlaylistContributorController(context.applicationContext, playlistUri, scope)
    }
}
