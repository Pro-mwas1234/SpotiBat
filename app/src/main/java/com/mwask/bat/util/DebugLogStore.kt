package com.project.lol.util

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DebugLogStore {
    private const val MAX = 250

    // FIX (perf): SimpleDateFormat was created per snapshot() inside the
    // lock. DateTimeFormatter is immutable/thread-safe - one shared instance.
    private val TIME_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    // FIX (perf): entries are stored pre-formatted; snapshot() is now a pure
    // copy under the lock instead of format+join of 250 rows while blocking
    // the JavaBridge thread's log() calls. Formatting cost is paid once per
    // entry at ingestion, not once per snapshot.
    private val buf = ArrayDeque<String>()
    private val lock = Any()

    fun log(tag: String, msg: String) {
        val line = "${TIME_FMT.format(Instant.now())} [$tag] ${msg.take(600)}"
        synchronized(lock) {
            buf.addLast(line)
            while (buf.size > MAX) buf.removeFirst()
        }
        Log.d("SpotilolDbg", "[$tag] $msg")
    }

    fun snapshot(): List<String> = synchronized(lock) { ArrayList(buf) }

    @Suppress("unused")
    fun count(): Int = synchronized(lock) { buf.size }

    fun clear() = synchronized(lock) { buf.clear() }
}