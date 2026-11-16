/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |
 *                     |_|
 *
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.NET)

/**
 * Process-wide connectivity watchdog. One ConnectivityManager callback is
 * registered the first time any part of the app asks for it and stays alive
 * for the whole process, so the answer never goes stale the way per-screen
 * listeners do when their composable leaves composition (the shell banner
 * "forgetting" to re-appear once the mini player had loaded was exactly that).
 *
 * Everything reads the same cached answer:
 *  - [Net] refuses to send anything while [isOffline] says so,
 *  - the Compose shell banner and the login webview error page render from it,
 *  - the dealer loop waits on [awaitOnline] to redial the moment the network
 *    is back instead of burning through its backoff.
 */
object ConnectivityMonitor {

    /** Called on the main thread whenever the online/offline verdict flips. */
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    private val started = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while the device has no usable internet; starts out optimistic. */
    private val offlineState = MutableStateFlow(false)

    /** Flips once per offline -> online transition, see [awaitOnline]. */
    private val reconnectSignal = MutableStateFlow(0)

    private var manager: ConnectivityManager? = null

    fun isOffline(): Boolean = offlineState.value

    fun addListener(listener: (Boolean) -> Unit): Boolean = listeners.add(listener)

    fun removeListener(listener: (Boolean) -> Unit): Boolean = listeners.remove(listener)

    /**
     * Registers the system callback once per process. Safe to call from
     * anywhere, any number of times; later calls only refresh the verdict.
     */
    fun ensureStarted(context: Context) {
        val connectivity =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: run {
                log.w("no ConnectivityManager on this device, assuming online")
                return
            }
        manager = connectivity
        if (!started.compareAndSet(false, true)) {
            recompute("sync")
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                recompute("available")
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                recompute(
                    "capabilities",
                ) {
                    // Prefer the live capabilities of the network the system just
                    // reported: activeNetwork can lag a few milliseconds behind.
                    val active = manager?.activeNetwork
                    if (active != null && active == network) {
                        capabilities
                    } else {
                        null
                    }
                }
            }

            override fun onLost(network: Network) {
                recompute("lost")
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectivity.registerDefaultNetworkCallback(callback, mainHandler)
            } else {
                // API 24-25: callbacks arrive on ConnectivityManager's own thread;
                // recompute() still hops to main before notifying listeners.
                connectivity.registerDefaultNetworkCallback(callback)
            }
            log.i("watchdog registered, verdict sync follows")
        } catch (failure: Throwable) {
            started.set(false)
            log.e("watchdog registration failed ${failure.javaClass.simpleName}: ${failure.message}")
            return
        }
        recompute("start")
    }

    /**
     * Suspends until the monitor says the network is back, or until [timeoutMs]
     * passes — whichever comes first. Returns true when online. Used by the
     * dealer reconnect loop so a returning connection redials immediately
     * instead of waiting out the remaining backoff.
     */
    suspend fun awaitOnline(timeoutMs: Long): Boolean {
        if (!isOffline()) return true
        return withTimeoutOrNull(timeoutMs) {
            reconnectSignal.first { it > 0 }
            !isOffline()
        } ?: !isOffline()
    }

    /** Recomputes the offline verdict; notifies listeners only on a change. */
    private fun recompute(reason: String, liveCaps: () -> NetworkCapabilities? = { null }) {
        val connectivity = manager ?: return
        val caps = runCatching(liveCaps).getOrNull()
        val offline = if (caps != null) {
            !caps.hasUsableInternet()
        } else {
            connectivity.isCurrentlyOffline()
        }
        val before = offlineState.value
        offlineState.value = offline
        if (before == offline) return
        if (offline) {
            reconnectSignal.value = 0
            log.w("offline ($reason): refusing outbound traffic")
        } else {
            reconnectSignal.value += 1
            log.i("online again ($reason): outbound traffic re-opened")
        }
        val notify = { listeners.forEach { listener -> runCatching { listener(offline) } } }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notify()
        } else {
            mainHandler.post(notify)
        }
    }

    private fun ConnectivityManager.isCurrentlyOffline(): Boolean {
        val network = activeNetwork ?: return true
        val capabilities = getNetworkCapabilities(network) ?: return true
        return !capabilities.hasUsableInternet()
    }

    /**
     * A network counts as internet only when it carries the INTERNET
     * capability and the system has actually validated it — that is what
     * keeps the banner honest on captive portals and half-dead wifi.
     */
    private fun NetworkCapabilities.hasUsableInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
