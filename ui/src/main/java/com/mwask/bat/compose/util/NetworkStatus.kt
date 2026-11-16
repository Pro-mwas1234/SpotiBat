package com.mwask.bat.compose.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Live network reachability for the shell's offline banner. Mirrors what the
 * is missing or has not validated its internet capability. The callback keeps
 * the state current while the composable is shown, and unregisters on leave.
 */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var offline by remember { mutableStateOf(manager?.isOfflineNow() ?: false) }

    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                offline = manager?.isOfflineNow() ?: false
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                offline = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                offline = manager?.isOfflineNow() ?: true
            }
        }
        manager?.registerDefaultNetworkCallback(callback)
        onDispose { manager?.unregisterNetworkCallback(callback) }
    }
    return offline
}

private fun ConnectivityManager.isOfflineNow(): Boolean {
    val network = activeNetwork ?: return true
    val caps = getNetworkCapabilities(network) ?: return true
    return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
