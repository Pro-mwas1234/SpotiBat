package com.mwask.bat

import android.widget.Toast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.store.AppPrefs
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.compose.components.UpdateDialog
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.components.UpdateInstallPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val log = Logs.of(Logs.Module.MAIN)

internal class UpdateController {

    var appUpdate by mutableStateOf<AppUpdate?>(null)
        internal set

    var checking by mutableStateOf(false)
        internal set

    var checked by mutableStateOf(false)
        internal set

    var prompt by mutableStateOf<AppUpdate?>(null)
        internal set

    var phase by mutableStateOf(UpdateInstallPhase.Idle)
        internal set

    var downloadedMb by mutableStateOf(0f)
        internal set

    var totalMb by mutableStateOf(0f)
        internal set

    var speedMbps by mutableStateOf(0f)
        internal set

    internal var fetchTick by mutableStateOf(0)
        internal set

    internal var installTick by mutableStateOf(0)
        internal set

    internal var autoTick by mutableStateOf(0)

    internal var timerTick by mutableStateOf(0)

    private var manualPending = false

    private var lastCheckAt = 0L

    fun requestManualCheck() {
        manualPending = true
        fetchTick++
    }

    fun onHomeEntered() {
        autoTick++
    }

    fun snooze(context: android.content.Context) {
        AppPrefs.prefs(context, CLIENT_PREFS).edit()
            .putLong("updateSnoozeUntil", System.currentTimeMillis() + 6 * 60 * 60 * 1000L)
            .apply()
        prompt = null
    }

    suspend fun autoCheck(context: android.content.Context) {
        val manual = manualPending
        manualPending = false
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheckAt < 30 * 60_000L) return
        lastCheckAt = now
        checking = true
        val found = withContext(Dispatchers.IO) {
            runCatching { UpdateChecker.check(context) }.getOrNull()
        }
        checking = false
        checked = true
        appUpdate = found
        if (found == null) {
            if (manual) {
                Toast.makeText(context, "No new updates yet", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!manual && AppPrefs.prefs(context, CLIENT_PREFS)
                .getLong("updateSnoozeUntil", 0L) > now
        ) return
        phase = UpdateInstallPhase.Idle
        prompt = found
    }

    suspend fun install(context: android.content.Context) {
        val target = prompt ?: return
        phase = UpdateInstallPhase.Downloading
        downloadedMb = 0f
        totalMb = 0f
        speedMbps = 0f
        val file = withContext(Dispatchers.IO) {
            UpdateInstaller.download(context, target) { done, total, speed ->
                downloadedMb = done / 1_000_000f
                totalMb = total / 1_000_000f
                speedMbps = speed / 1_000_000f
            }
        }
        if (file == null) {
            phase = UpdateInstallPhase.Idle
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
            return
        }
        phase = UpdateInstallPhase.Installing
        runCatching { UpdateInstaller.install(context, file) }
            .onFailure {
                phase = UpdateInstallPhase.Idle
                Toast.makeText(context, "Install failed", Toast.LENGTH_SHORT).show()
            }
    }
}

@Composable
internal fun rememberUpdateController(): UpdateController = remember { UpdateController() }

@Composable
internal fun UpdateEffects(controller: UpdateController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(controller.fetchTick) {
        if (controller.fetchTick == 0) return@LaunchedEffect
        controller.checking = true
        controller.appUpdate = withContext(Dispatchers.IO) {
            runCatching { UpdateChecker.check(context) }.getOrNull()
        }
        controller.checking = false
        controller.checked = true
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { UpdateInstaller.cleanup(context) }
        while (isActive) {
            delay(30 * 60 * 1000L)
            controller.timerTick++
        }
    }
    LaunchedEffect(controller.autoTick, controller.timerTick, controller.fetchTick) {
        if (controller.autoTick == 0 && controller.timerTick == 0 && controller.fetchTick == 0) {
            return@LaunchedEffect
        }
        controller.autoCheck(context)
    }
    LaunchedEffect(controller.installTick) {
        if (controller.installTick == 0) return@LaunchedEffect
        controller.install(context)
    }
    if (controller.prompt != null) {
        UpdateDialog(
            update = controller.prompt!!,
            currentVersion = UpdateChecker.currentVersion(context).orEmpty(),
            phase = controller.phase,
            downloadedMb = controller.downloadedMb,
            totalMb = controller.totalMb,
            speedMbps = controller.speedMbps,
            onLater = { controller.snooze(context) },
            onInstall = { controller.installTick++ },
            onDismiss = { controller.prompt = null },
        )
    }
}
