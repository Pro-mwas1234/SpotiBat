package com.mwask.bat.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for offline playback, shared by the Compose UI
 * (mini bar + full-screen player) and [com.mwask.bat.service.OfflineMediaService]
 * (notification + media session).
 *
 * Ownership: the screen owns the actual [android.media.MediaPlayer] engine and
 * [publish]es every state change here. Consumers render from [state] and send
 * user intents (from the notification or headset controls) via [commands]; the
 * screen's controller executes them on the engine.
 */
object OfflinePlayback {

    /** Immutable snapshot of what every surface should be showing right now. */
    data class Snapshot(
        val hasTrack: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val coverPath: String? = null,
        val playing: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
    )

    /** One user intent coming from the notification, media session, or headset. */
    sealed interface Command {
        data object PlayPause : Command
        data object Next : Command
        data object Prev : Command
        data object Stop : Command
        data class Seek(val positionMs: Long) : Command
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    /** Called by the playback owner (the screen) whenever anything changes. */
    fun publish(snapshot: Snapshot) {
        _state.value = snapshot
    }

    /** Submit a user intent from any surface (notification, session, headset). */
    fun submit(command: Command) {
        _commands.tryEmit(command)
    }

    /**
     * Begin forwarding [commands] to [handler]. Returns a [Job]; cancel it when
     * the owner goes away (or call [clearController]).
     */
    fun collectCommands(scope: CoroutineScope, handler: (Command) -> Unit): Job =
        scope.launch {
            commands.collect { handler(it) }
        }

    /** Reset state when playback is fully torn down. */
    fun clear() {
        _state.value = Snapshot()
    }
}
