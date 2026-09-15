package com.mwask.bat.service

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.mwask.bat.offline.OfflinePlayback

/**
 * Audio focus for offline playback so the player respects the system:
 * - Phone call / full focus loss -> pause (a PlayPause command, like the
 *   user tapping pause themselves).
 * - Notification beep / navigation prompt -> duck by briefly seeking forward
 *   a second (the offline engine has no volume control of its own, so this
 *   skips past the interruption window; volume ducking proper would need a
 *   volume-shader or player-level gain).
 * - Focus regained after a duck -> seek back so no content is lost.
 */
class OfflineAudioFocus(
    private val audioManager: AudioManager,
    private val onPause: () -> Unit,
    private val onDuck: (targetPositionMs: Long) -> Unit,
    private val onUnduck: (restorePositionMs: Long) -> Unit,
) {

    private var preDuckPositionMs = -1L

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onPause()

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(duckTarget())

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (preDuckPositionMs >= 0) {
                    onUnduck(preDuckPositionMs)
                    preDuckPositionMs = -1
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .build()
    } else null

    /** Call when playback starts. */
    fun request(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Call when playback stops for good. */
    fun abandon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        preDuckPositionMs = -1
    }

    /**
     * Target position for a duck: 1.5s ahead, so short beeps land inside the
     * skipped window and AUDIOFOCUS_GAIN restores us right past them.
     */
    private fun duckTarget(): Long {
        val current = OfflinePlayback.currentPositionMs()
        if (current < 0) return -1L
        val duration = OfflinePlayback.currentDurationMs()
        val target = current + 1500L
        if (duration > 0 && target >= duration - 500L) {
            // Ducking would reach the end of the track; skip ducking entirely
            // and let the song finish naturally (no restore needed).
            preDuckPositionMs = -1
            return -1L
        }
        preDuckPositionMs = current
        return target
    }
}
