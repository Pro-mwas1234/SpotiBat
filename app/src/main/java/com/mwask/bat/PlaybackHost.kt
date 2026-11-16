package com.mwask.bat

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.mwask.bat.backend.core.log.Logs

object PlaybackHost {

    private val log = Logs.of(Logs.Module.HOST)

    @Volatile
    private var instance: PlaybackBridge? = null

    @Volatile
    private var foreground = false

    val generation = mutableStateOf(0)

    private fun bump() {
        generation.value = generation.value + 1
    }

    val started: Boolean get() = instance != null

    /** The live bridge behind the service's notification, if one is running. */
    val bridge: PlaybackBridge? get() = instance

    fun ensure(context: Context): PlaybackBridge {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: PlaybackBridge(context.applicationContext).also { bridge ->
                bridge.start()
                log.i("bridge created device=${bridge.deviceId}")
                instance = bridge
                bump()
            }
        }
    }

    fun startService(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { log.e("service start failed ${it.javaClass.simpleName}: ${it.message}") }
    }

    fun markForeground() {
        foreground = true
    }

    fun teardown(context: Context) {
        val bridge = instance
        instance = null
        foreground = false
        bump()
        if (bridge != null) {
            runCatching { bridge.handOff() }
                .onFailure { log.w("hand off failed ${it.javaClass.simpleName}: ${it.message}") }
            bridge.stop()
        }
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
            .onFailure { log.w("service stop failed ${it.javaClass.simpleName}") }
        log.i("playback torn down, the process holds nothing")
    }

    fun reset(context: Context) {
        instance?.reset()
        instance = null
        foreground = false
        bump()
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        log.i("bridge reset, next start mints a new device")
    }

    fun release() {
        instance?.stop()
        instance = null
        foreground = false
        bump()
        log.i("bridge released")
    }
}
