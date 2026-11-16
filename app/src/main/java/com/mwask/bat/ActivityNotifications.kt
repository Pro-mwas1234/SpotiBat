package com.mwask.bat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mwask.bat.backend.api.Notifications
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.model.ActivityNotification
import com.mwask.bat.backend.model.SelfProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val log = Logs.of(Logs.Module.MAIN)

class ActivityFeedController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var feed by mutableStateOf<List<ActivityNotification>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var unread by mutableStateOf(false)
        private set
    var self by mutableStateOf<SelfProfile?>(null)
        private set

    /** Latest read cursor of the last successful feed load. */
    private var cursor = ""

    /** Bumped by open(); MainActivity's effect reloads on every change. */
    var fetchTick by mutableStateOf(0)
        private set

    /** Opens (or re-opens) the feed: bumps the loader. */
    fun open() {
        fetchTick++
    }

    suspend fun load() {
        loading = true
        failed = false
        coroutineScope {
            val feedJob = async { runCatching { Notifications.feed(context) }.getOrNull() }
            val selfJob = async { runCatching { Notifications.selfProfile(context) }.getOrNull() }
            val result = feedJob.await()
            self = selfJob.await()
            if (result != null) {
                feed = result.items
                cursor = result.latestCursor
            }
            failed = result == null
            loading = false
            log.i("activity feed loaded items=${result?.items?.size ?: 0} " +
                    "new=${result?.items?.count { it.isNew } ?: 0} failed=${result == null}"
            )
        }
    }

    /** Marks the feed as read, mirroring the web player leaving the panel. */
    fun leave() {
        scope.launch {
            val pending = cursor
            if (pending.isNotBlank()) {
                val ok = runCatching { Notifications.resetCursor(context, pending) }.getOrDefault(false)
                log.i("activity resetCursor ok=$ok")
            }
            unread = false
        }
    }

    /** Refreshes the unread dot; called when the profile drawer opens. */
    fun refreshUnread() {
        scope.launch {
            unread = runCatching { Notifications.hasUnread(context) }.getOrDefault(false)
        }
    }
}

