package com.project.lol.webview.helpers

/**
 * Bounded, thread-safe store of ad audio content IDs harvested by the
 * page-level state hook (AdStateHook).
 *
 * Ported from the Blockify (blockify-desktop) architecture: JavaScript inside
 * the Spotify web player inspects player-state payloads delivered via fetch
 * and WebSocket, extracts the audio file IDs belonging to tracks flagged as
 * ads, and registers them natively so shouldInterceptRequest can silence
 * those exact media requests - closing the gap left by static host matching
 * (matchAdCdn), which cannot distinguish ad audio served from the same
 * generic CDN as music (opaque file IDs, no URL marker).
 *
 * Design mirrors Blockify's blocker.setAdContentIds():
 *  - IDs validated with ^[a-zA-Z0-9_-]{8,128}$ (hand-rolled, allocation-free)
 *  - bounded to 32 entries, least-recently-seen evicted first
 *  - cleared on every main-frame navigation (page start)
 *
 * Concurrency: writes are serialized under a lock and republish an immutable
 * snapshot; reads (matches/snapshot/size) are lock-free via a single volatile
 * reference. shouldInterceptRequest may run concurrently on multiple WebView
 * IO threads, so the hot path must never block on a writer.
 */
object AdIdStore {
    private const val MAX_IDS = 32
    private const val MIN_ID_LEN = 8
    private const val MAX_ID_LEN = 128

    private val lock = Any()

    // Recency-ordered working set: remove+add = "touch" (moves to MRU end).
    // Capacity pre-sized so the backing hash table never resizes below the bound.
    private val ids = LinkedHashSet<String>(MAX_IDS * 2)

    // Immutable snapshot published for the hot path. Never mutated after
    // publication, so readers can iterate it without holding the lock.
    @Volatile
    private var published: List<String> = emptyList()

    /**
     * Adds validated candidates. Returns true when at least one new ID was
     * stored (callers use this to rate-limit logging).
     */
    fun addAll(candidates: List<String>): Boolean {
        if (candidates.isEmpty()) return false
        var changed = false
        synchronized(lock) {
            for (raw in candidates) {
                val id = raw.trim()
                if (!isValidId(id)) continue
                val existed = ids.remove(id)
                ids.add(id)
                if (!existed) changed = true
            }
            // Evict least-recently-seen entries past the bound.
            val iter = ids.iterator()
            while (ids.size > MAX_IDS) {
                iter.next()
                iter.remove()
            }
            if (changed) published = ArrayList(ids)
        }
        return changed
    }

    /**
     * Hot path from shouldInterceptRequest: lock-free scan of an immutable
     * snapshot. With <= 32 IDs of >= 8 chars each, the total scan is a few
     * microseconds even on busy pages.
     */
    fun matches(url: String): Boolean {
        if (url.length < MIN_ID_LEN) return false
        val snapshot = published // single volatile read
        for (i in snapshot.indices) { // indexed loop: no Iterator allocation
            if (url.contains(snapshot[i])) return true
        }
        return false
    }

    /** Already an immutable copy; safe to hand out directly. */
    @Suppress("unused")
    fun snapshot(): List<String> = published

    fun clear() {
        synchronized(lock) {
            ids.clear()
            published = emptyList()
        }
    }

    fun size(): Int = published.size

    /** Allocation-free equivalent of ^[a-zA-Z0-9_-]{8,128}$. */
    private fun isValidId(id: String): Boolean {
        val n = id.length
        if (n !in MIN_ID_LEN..MAX_ID_LEN) return false
        for (i in 0 until n) {
            val c = id[i]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-'
            if (!ok) return false
        }
        return true
    }
}