package com.project.lol.webview.helpers

import androidx.collection.LruCache
import com.project.lol.webview.helpers.JsUtils.MAX_CACHE_BYTES
import com.project.lol.webview.helpers.JsUtils.stripConsoleLogs

/**
 * Strips `console.log(...)` calls from JavaScript before it is handed to
 * the WebView.
 *
 * Correctness behavior is identical to the previous version (see git
 * history for the individual FIXes): literals are lexed and never touched,
 * nesting is arbitrary, calls become `void 0`, `window.console.log` etc.
 * are left alone.
 *
 * Performance notes (this rewrite):
 *  - the scan is driven by native `indexOf` searches, not a per-char loop;
 *  - the output buffer is lazy: inputs with no strippable call are returned
 *    as the same instance with zero allocation;
 *  - the regex/division heuristic no longer allocates substrings;
 *  - results are memoized in memory
 */
object JsUtils {

    private val REGEX_KEYWORDS = setOf(
        "return", "typeof", "instanceof", "in", "of", "new",
        "delete", "void", "case", "do", "else", "yield", "await"
    )

    /**
     * Upper bound for cached payloads, in bytes (UTF-16: 2 bytes per char).
     * ~2 MB is enough for dozens of typical JS bundles while capping memory
     * use in long-running WebView sessions.
     */
    private const val MAX_CACHE_BYTES = 2 * 1024 * 1024

    /**
     * FIX (perf): memoizes stripped output so each asset is scanned at most
     * once. LruCache is thread-safe, evicts least-recently-used entries when
     * [MAX_CACHE_BYTES] is exceeded, and is keyed by the caller-supplied
     * request URL. Using a plain HashMap here would grow unbounded for the
     * lifetime of the process.
     */
    private val cache = object : LruCache<String, String>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: String): Int =
            ((key.length + value.length) * 2).coerceAtLeast(1)
    }

    /**
     * Cached variant of [stripConsoleLogs] for use in
     * `shouldInterceptRequest`, where the same asset is intercepted on
     * every page load.
     *
     * @param key stable identity of the asset, typically the request URL.
     *   For mutable/remote responses, include a version or content hash in
     *   the key; volatile query parameters (session tokens, timestamps)
     *   should be stripped from the key to avoid cache churn.
     * @param code the original JavaScript source.
     * @return the stripped source; after the first call for [key], the
     *   memoized result is returned without re-scanning.
     */
    fun stripConsoleLogsCached(key: String, code: String): String {
        // FIX (perf): check the cache before scanning. LruCache synchronizes
        // internally per call, so no lock is held while we scan - concurrent
        // misses on different URLs proceed in parallel.
        cache[key]?.let { return it }

        val stripped = stripConsoleLogs(code)

        // Note: on a concurrent duplicate miss, the second result simply
        // overwrites the first - the outputs are deterministic, so this is
        // harmless.
        cache.put(key, stripped)
        return stripped
    }

    /**
     * Replaces every `console.log(...)` call with `void 0`.
     *
     * Safe against calls appearing inside strings, comments, and regex
     * literals; handles arbitrary paren nesting inside the call arguments.
     * If the input does not contain the substring "console", the original
     * instance is returned without allocation.
     */
    fun stripConsoleLogs(code: String): String {
        // PERF: one native search doubles as the "contains" fast path.
        var nextConsole = code.indexOf("console")
        if (nextConsole == -1) return code

        val n = code.length
        var nextLit = nextLiteral(code, 0)
        var sb: StringBuilder? = null // PERF: lazy - created on the first real strip
        var i = 0

        while (i < n) {
            // PERF: refresh positions lazily; regions already consumed are
            // never rescanned.
            if (nextLit != -1 && nextLit < i) nextLit = nextLiteral(code, i)
            if (nextConsole != -1 && nextConsole < i) {
                nextConsole = code.indexOf("console", i)
            }

            when {
                // A `console` occurrence comes first: everything in
                // [i, nextConsole) is plain code - bulk-copy it.
                nextConsole != -1 && (nextLit == -1 || nextConsole < nextLit) -> {
                    sb?.append(code, i, nextConsole)
                    val openEnd = matchCallStart(code, nextConsole)
                    val close = if (openEnd != -1) findMatchingParen(code, openEnd) else -1
                    if (close != -1) {
                        if (sb == null) {
                            sb = StringBuilder(code.length)
                                .also { it.append(code, 0, nextConsole) }
                        }
                        sb.append("void 0")
                        i = close + 1
                    } else {
                        // Not a call (console.warn, myconsole, window.console…) -
                        // copy "console" verbatim and continue after it.
                        sb?.append(code, nextConsole, nextConsole + 7)
                        i = nextConsole + 7
                    }
                }
                // A string/comment/regex/division boundary comes first.
                nextLit != -1 -> {
                    sb?.append(code, i, nextLit)
                    when (code[nextLit]) {
                        '"', '\'', '`' -> {
                            val e = skipString(code, nextLit)
                            sb?.append(code, nextLit, e); i = e
                        }
                        '/' -> when {
                            nextLit + 1 < n && code[nextLit + 1] == '/' -> {
                                val nl = code.indexOf('\n', nextLit)
                                val e = if (nl == -1) n else nl
                                sb?.append(code, nextLit, e); i = e
                            }
                            nextLit + 1 < n && code[nextLit + 1] == '*' -> {
                                val cm = code.indexOf("*/", nextLit + 2)
                                val e = if (cm == -1) n else cm + 2
                                sb?.append(code, nextLit, e); i = e
                            }
                            isRegexStart(code, nextLit) -> {
                                val e = skipRegex(code, nextLit)
                                sb?.append(code, nextLit, e); i = e
                            }
                            else -> { sb?.append('/'); i = nextLit + 1 } // division
                        }
                    }
                }
                else -> {
                    sb?.append(code, i, n)
                    i = n
                }
            }
        }
        // PERF: nothing was stripped -> return the original instance.
        return sb?.toString() ?: code
    }

    /** Next index >= [from] holding a quote or '/', or -1. Intrinsified. */
    private fun nextLiteral(code: String, from: Int): Int {
        var best = code.indexOf('"', from)
        var idx = code.indexOf('\'', from)
        if (idx != -1 && (best == -1 || idx < best)) best = idx
        idx = code.indexOf('`', from)
        if (idx != -1 && (best == -1 || idx < best)) best = idx
        idx = code.indexOf('/', from)
        if (idx != -1 && (best == -1 || idx < best)) best = idx
        return best
    }

    /**
     * Returns the index just past the '(' if [code] starting at [start] is
     * a `console.log(` token sequence; -1 otherwise.
     *
     * FIX: the previous regex only matched `console.log` verbatim, so
     * `window.console.log(...)` was partially stripped (leaving `window.`)
     * and whitespace such as `console . log (...)` was missed. Here we
     * verify the call is not preceded by an identifier character or a '.',
     * and allow whitespace between tokens as the JS grammar does.
     */
    private fun matchCallStart(code: String, start: Int): Int {
        val n = code.length
        if (!code.regionMatches(start, "console", 0, 7)) return -1
        val prev = if (start > 0) code[start - 1] else ' '
        if (prev.isLetterOrDigit() || prev == '_' || prev == '$' || prev == '.') return -1
        var i = skipWs(code, start + 7)
        if (i >= n || code[i] != '.') return -1
        i = skipWs(code, i + 1)
        if (!code.regionMatches(i, "log", 0, 3)) return -1
        i = skipWs(code, i + 3)
        return if (i < n && code[i] == '(') i + 1 else -1
    }

    /**
     * Returns the index of the ')' matching the '(' just before [from].
     *
     * FIX: the old regex supported at most one nesting level, so calls like
     * `console.log(a.map(x => f(x)))` were left in place. Depth tracking
     * supports arbitrary nesting. String/comment/regex literals are skipped
     * so their contents cannot affect the count.
     */
    private fun findMatchingParen(code: String, from: Int): Int {
        var depth = 1
        var i = from
        val n = code.length
        while (i < n) {
            when (code[i]) {
                '(' -> { depth++; i++ }
                ')' -> { depth--; if (depth == 0) return i; i++ }
                '"', '\'', '`' -> i = skipString(code, i)
                '/' -> when {
                    i + 1 < n && code[i + 1] == '/' -> {
                        val nl = code.indexOf('\n', i); i = if (nl == -1) n else nl
                    }
                    i + 1 < n && code[i + 1] == '*' -> {
                        val cm = code.indexOf("*/", i + 2); i = if (cm == -1) n else cm + 2
                    }
                    isRegexStart(code, i) -> i = skipRegex(code, i)
                    else -> i++
                }
                else -> i++
            }
        }
        return -1
    }

    /**
     * Heuristic: decides whether the '/' at [i] starts a regex literal or
     * is a division operator, based on the previous significant token.
     *
     * Known limitation: a regex literal written directly after `)` (e.g.
     * `if (x) /re/.test(y)`) is classified as division. The consequence is
     * that a call may be skipped - output is never corrupted.
     *
     * PERF: allocation-free (previously one substring per '/' after an identifier).
     */
    private fun isRegexStart(code: String, i: Int): Boolean {
        var j = i - 1
        while (j >= 0 && code[j].isWhitespace()) j--
        if (j < 0) return true
        val p = code[j]
        if (p == ')' || p == ']' || p == '}' ||
            p == '"' || p == '\'' || p == '`'
        ) return false
        if (!(p.isLetterOrDigit() || p == '_' || p == '$')) return true
        // Previous token is an identifier/number: regex only after a keyword.
        var k = j
        while (k >= 0 && (code[k].isLetterOrDigit() || code[k] == '_' || code[k] == '$')) k--
        val len = j - k
        for (kw in REGEX_KEYWORDS) {
            if (kw.length == len && code.regionMatches(k + 1, kw, 0, len)) return true
        }
        return false
    }

    /** Consumes a regex literal starting at [start]; assumes it is one. */
    private fun skipRegex(code: String, start: Int): Int {
        var i = start + 1
        val n = code.length
        var inClass = false
        while (i < n) {
            when (code[i]) {
                '\\' -> i += 2
                '[' -> { inClass = true; i++ }
                ']' -> { inClass = false; i++ }
                '/' -> if (!inClass) return i + 1 else i++
                // A newline means this was not a valid regex after all;
                // fall back to treating the '/' as a plain division sign.
                '\n' -> return start + 1
                else -> i++
            }
        }
        return start + 1
    }

    /** Consumes a quoted string starting at [start], honoring escapes. */
    private fun skipString(code: String, start: Int): Int {
        val q = code[start]
        var i = start + 1
        val n = code.length
        while (i < n) {
            if (code[i] == '\\') i += 2
            else if (code[i] == q) return i + 1
            else i++
        }
        return n
    }

    private fun skipWs(code: String, from: Int): Int {
        var i = from
        while (i < code.length && code[i].isWhitespace()) i++
        return i
    }
}