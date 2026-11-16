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
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.core.auth

import android.content.Context
import com.mwask.bat.backend.core.store.SecureStore
import android.util.Base64
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.webview.cookieManagerOrNull
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.AUTH)

object EncoreAuth {

    data class Account(
        val id: String = "",
        val name: String = "",
        val email: String = "",
        val imageUrl: String = "",
        val product: String = "",
        val country: String = "",
        val colorHex: String = "",
    )

    private const val KEY_SESSION = "session"
    private const val KEY_NAME = "account_name"
    private const val KEY_ID = "account_id"
    private const val KEY_EMAIL = "account_email"
    private const val KEY_IMAGE = "account_image"
    private const val KEY_PRODUCT = "account_product"
    private const val KEY_COUNTRY = "account_country"
    private const val KEY_COLOR = "account_color"
    private const val KEY_SAVED_AT = "session_saved_at"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_TOKEN_AT = "access_token_at"
    private const val KEY_TOKEN_EXP = "access_token_exp"
    private const val KEY_CLIENT_TOKEN = "client_token"
    private const val KEY_CLIENT_TOKEN_EXP = "client_token_exp"
    private fun prefs(context: Context) = SecureStore.prefs(context)

    @Volatile
    private var tokenMemo: String? = null

    @Volatile
    private var tokenExpMemo: Long? = null

    @Volatile
    private var clientTokenMemo: String? = null

    @Volatile
    private var clientTokenExpMemo: Long? = null

    private fun clearTokenMemos() {
        tokenMemo = ""
        tokenExpMemo = 0L
        clientTokenMemo = ""
        clientTokenExpMemo = 0L
    }

    private const val KEY_MULTI_INDEX = "multi.index"
    private const val KEY_MULTI_ACTIVE = "multi.active"

    /** All accounts the user has logged into on this device. */
    fun savedAccounts(context: Context): List<Account> {
        val p = prefs(context)
        val ids = runCatching {
            JSONArray(p.getString(KEY_MULTI_INDEX, "[]"))
        }.getOrNull() ?: JSONArray()
        return (0 until ids.length()).mapNotNull { i ->
            val id = ids.optString(i)
            if (id.isBlank()) return@mapNotNull null
            val name = p.getString("multi.$id.name", "").orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Account(
                id = id,
                name = name,
                email = p.getString("multi.$id.email", "").orEmpty(),
                imageUrl = p.getString("multi.$id.image", "").orEmpty(),
                product = p.getString("multi.$id.product", "").orEmpty(),
                country = p.getString("multi.$id.country", "").orEmpty(),
                colorHex = p.getString("multi.$id.color", "").orEmpty(),
            )
        }
    }

    fun activeAccountId(context: Context): String =
        prefs(context).getString(KEY_MULTI_ACTIVE, "").orEmpty()

    /**
     * Snapshots the currently active session + profile into the multi-account
     * store. Call after a login completes (and the account id is known).
     */
    fun storeCurrentAccount(context: Context) {
        val account = account(context) ?: return
        if (account.id.isBlank()) {
            log.w("storeCurrentAccount: no account id yet, skipping")
            return
        }
        val p = prefs(context)
        p.edit()
            .putString("multi.${account.id}.session", p.getString(KEY_SESSION, null))
            .putString("multi.${account.id}.name", account.name)
            .putString("multi.${account.id}.email", account.email)
            .putString("multi.${account.id}.image", account.imageUrl)
            .putString("multi.${account.id}.product", account.product)
            .putString("multi.${account.id}.country", account.country)
            .putString("multi.${account.id}.color", account.colorHex)
            .putString(KEY_MULTI_ACTIVE, account.id)
            .apply()
        upsertIndex(context, account.id)
        log.i("stored account ${account.id} (${account.name}), saved=${savedAccounts(context).size}")
    }

    private fun upsertIndex(context: Context, id: String) {
        val p = prefs(context)
        val ids = runCatching {
            JSONArray(p.getString(KEY_MULTI_INDEX, "[]"))
        }.getOrDefault(JSONArray())
        val existing = (0 until ids.length()).map { ids.optString(it) }
        if (!existing.contains(id)) {
            ids.put(id)
            p.edit().putString(KEY_MULTI_INDEX, ids.toString()).apply()
        }
    }

    /**
     * Replaces the active session with the stored snapshot of [id]: session
     * cookies and profile move into the active keys, the cached tokens (which
     * belong to the previous account) are dropped and the webview cookie jar
     * is re-seeded. Returns false when the id is unknown.
     */
    fun switchAccount(context: Context, id: String): Boolean {
        val p = prefs(context)
        val session = p.getString("multi.$id.session", null)
        val name = p.getString("multi.$id.name", "").orEmpty()
        if (session.isNullOrBlank() || name.isBlank()) {
            log.w("switchAccount: no stored snapshot for '$id'")
            return false
        }
        p.edit()
            .putString(KEY_SESSION, session)
            .putString(KEY_NAME, name)
            .putString(KEY_ID, id)
            .putString(KEY_EMAIL, p.getString("multi.$id.email", "").orEmpty())
            .putString(KEY_IMAGE, p.getString("multi.$id.image", "").orEmpty())
            .putString(KEY_PRODUCT, p.getString("multi.$id.product", "").orEmpty())
            .putString(KEY_COUNTRY, p.getString("multi.$id.country", "").orEmpty())
            .putString(KEY_COLOR, p.getString("multi.$id.color", "").orEmpty())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .putString(KEY_MULTI_ACTIVE, id)
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_AT)
            .remove(KEY_TOKEN_EXP)
            .remove(KEY_CLIENT_TOKEN)
            .remove(KEY_CLIENT_TOKEN_EXP)
            .apply()
        clearTokenMemos()
        resolvedUserId = ""
        val manager = cookieManagerOrNull()
        if (manager != null) {
            manager.removeAllCookies(null)
            manager.flush()
        } else {
            log.w("switchAccount: cookie jar unavailable, the old cookies stay put")
        }
        applySession(context)
        log.i("switchAccount: now on '$id' ($name)")
        return true
    }

    /**
     * Deletes the stored snapshot of [id]. Returns true when the active
     * account was the one removed (the caller must then pick another account
     * or log out completely).
     */
    fun forgetAccount(context: Context, id: String): Boolean {
        val p = prefs(context)
        val wasActive = activeAccountId(context) == id
        p.edit()
            .remove("multi.$id.session")
            .remove("multi.$id.name")
            .remove("multi.$id.email")
            .remove("multi.$id.image")
            .remove("multi.$id.product")
            .remove("multi.$id.country")
            .remove("multi.$id.color")
            .apply()
        val ids = runCatching {
            JSONArray(p.getString(KEY_MULTI_INDEX, "[]"))
        }.getOrDefault(JSONArray())
        val kept = (0 until ids.length()).map { ids.optString(it) }.filter { it != id }
        p.edit().putString(KEY_MULTI_INDEX, JSONArray(kept).toString()).apply()
        log.i("forgetAccount '$id' wasActive=$wasActive remaining=${kept.size}")
        return wasActive
    }

    /**
     * Prepares the cookie jar for logging into an additional account: the
     * for credentials. The secure session itself is untouched.
     */
    fun beginAddAccount(context: Context) {
        val manager = cookieManagerOrNull()
        if (manager == null) {
            log.w("beginAddAccount: cookie jar unavailable, cannot clear cookies")
            return
        }
        manager.removeAllCookies(null)
        manager.flush()
        log.i("beginAddAccount: cookie jar cleared for a fresh login")
    }

    fun saveToken(context: Context, token: String, expiresAtMs: Long = 0L) {
        if (token.isBlank()) return
        val previous = token(context)
        if (previous == token && expiresAtMs <= 0L) return
        val now = System.currentTimeMillis()
        val exp = if (expiresAtMs > 0L) expiresAtMs else jwtExpiryMs(token)
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_AT, now)
            .putLong(KEY_TOKEN_EXP, exp)
            .apply()
        tokenMemo = token
        tokenExpMemo = exp
        val ttl = if (exp <= 0L) "opaque" else "${(exp - now) / 1000}s"
        log.i("saved access token (${token.length}B) expiry=$ttl jwt=${exp > 0L}")
    }

    fun markTokenSeen(context: Context) {
        if (token(context).isBlank()) return
        prefs(context).edit()
            .putLong(KEY_TOKEN_AT, System.currentTimeMillis())
            .apply()
    }

    fun invalidateToken(context: Context) {
        log.w("access token rejected, clearing it so the next pass re-captures")
        prefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_AT)
            .remove(KEY_TOKEN_EXP)
            .apply()
        tokenMemo = ""
        tokenExpMemo = 0L
    }

    fun token(context: Context): String {
        tokenMemo?.let { return it }
        val value = prefs(context).getString(KEY_TOKEN, "").orEmpty()
        tokenMemo = value
        return value
    }

    fun tokenExpiresAt(context: Context): Long {
        tokenExpMemo?.let { return it }
        val value = prefs(context).getLong(KEY_TOKEN_EXP, 0L)
        tokenExpMemo = value
        return value
    }

    fun tokenRemainingMs(context: Context): Long {
        val exp = tokenExpiresAt(context)
        return if (exp <= 0L) -1L else exp - System.currentTimeMillis()
    }

    fun tokenExpired(context: Context): Boolean {
        val remaining = tokenRemainingMs(context)
        return remaining != -1L && remaining <= 0L
    }

    fun tokenAgeMs(context: Context): Long {
        val at = prefs(context).getLong(KEY_TOKEN_AT, 0L)
        return if (at == 0L) -1L else System.currentTimeMillis() - at
    }

    fun saveClientToken(context: Context, token: String, expiresAtMs: Long = 0L) {
        if (token.isBlank()) {
            prefs(context).edit()
                .remove(KEY_CLIENT_TOKEN)
                .remove(KEY_CLIENT_TOKEN_EXP)
                .apply()
            clientTokenMemo = ""
            clientTokenExpMemo = 0L
            return
        }
        if (clientToken(context) == token && expiresAtMs <= 0L) return
        val exp = if (expiresAtMs > 0L) expiresAtMs else jwtExpiryMs(token)
        prefs(context).edit()
            .putString(KEY_CLIENT_TOKEN, token)
            .putLong(KEY_CLIENT_TOKEN_EXP, exp)
            .apply()
        clientTokenMemo = token
        clientTokenExpMemo = exp
        val ttl = if (exp <= 0L) "opaque" else "${(exp - System.currentTimeMillis()) / 1000}s"
        log.i("saved client-token (${token.length}B) expiry=$ttl")
    }

    fun clientToken(context: Context): String {
        clientTokenMemo?.let { return it }
        val value = prefs(context).getString(KEY_CLIENT_TOKEN, "").orEmpty()
        clientTokenMemo = value
        return value
    }

    fun clientTokenRemainingMs(context: Context): Long {
        val exp = clientTokenExpiresAt(context)
        return if (exp <= 0L) -1L else exp - System.currentTimeMillis()
    }

    private fun clientTokenExpiresAt(context: Context): Long {
        clientTokenExpMemo?.let { return it }
        val value = prefs(context).getLong(KEY_CLIENT_TOKEN_EXP, 0L)
        clientTokenExpMemo = value
        return value
    }

    private fun jwtExpiryMs(token: String): Long {
        val parts = token.split('.')
        if (parts.size != 3) return 0L
        return runCatching {
            val decoded = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val exp = JSONObject(String(decoded, Charsets.UTF_8)).optLong("exp", 0L)
            if (exp > 0L) exp * 1000L else 0L
        }.getOrDefault(0L)
    }

    /** User-Agent used when contacting Spotify APIs. Set to null to let WebViews use the system default instead of a spoofed one. */
    const val DESKTOP_UA = ""

    private val COOKIE_DOMAINS = listOf(
        "https://open.spotify.com",
        "https://accounts.spotify.com",
        "https://api-partner.spotify.com",
        "https://spclient.wg.spotify.com",
        "https://api.spotify.com",
        "https://www.spotify.com",
    )

    fun isLoggedIn(context: Context): Boolean {
        val value = spDc(context)
        log.i("isLoggedIn=${value.isNotBlank()} sp_dc len=${value.length}")
        return value.isNotBlank()
    }

    fun spDc(context: Context): String {
        val session = prefs(context).getString(KEY_SESSION, null) ?: return ""
        return spDcOf(session)
    }

    fun spDcOf(cookies: String): String {
        val map = runCatching { JSONObject(cookies) }.getOrNull() ?: return ""
        COOKIE_DOMAINS.forEach { domain ->
            val raw = map.optString(domain, "")
            if (raw.isBlank()) return@forEach
            val found = raw.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("sp_dc=") }
            if (found != null) return found.substringAfter("sp_dc=")
        }
        return ""
    }

    fun cookieSummary(context: Context): String {
        val manager = cookieManagerOrNull()
            ?: return COOKIE_DOMAINS.joinToString(" ") {
                "${it.removePrefix("https://")}=unavailable"
            }
        return COOKIE_DOMAINS.joinToString(" ") { domain ->
            val cookies = manager.getCookie(domain)
            val host = domain.removePrefix("https://")
            when {
                cookies.isNullOrBlank() -> "$host=-"
                cookies.contains("sp_dc=") -> "$host=dc"
                else -> "$host=ok"
            }
        }
    }

    fun account(context: Context): Account? {
        if (!isLoggedIn(context)) return null
        val p = prefs(context)
        val name = p.getString(KEY_NAME, "").orEmpty()
        if (name.isBlank()) return null
        return Account(
            id = p.getString(KEY_ID, "").orEmpty(),
            name = name,
            email = p.getString(KEY_EMAIL, "").orEmpty(),
            imageUrl = p.getString(KEY_IMAGE, "").orEmpty(),
            product = p.getString(KEY_PRODUCT, "").orEmpty(),
            country = p.getString(KEY_COUNTRY, "").orEmpty(),
            colorHex = p.getString(KEY_COLOR, "").orEmpty(),
        )
    }

    fun displayName(context: Context): String = account(context)?.name.orEmpty()

    private const val USER_ID_RETRY_MS = 60_000L

    @Volatile
    private var resolvedUserId: String = ""

    @Volatile
    private var lastUserIdAttempt: Long = 0L

    private fun plausibleId(value: String): String? =
        value.trim().takeIf { it.isNotBlank() && it != "me" && !it.contains(":") && !it.contains(" ") }

    suspend fun userId(context: Context, force: Boolean = false): String {
        resolvedUserId.takeIf { it.isNotBlank() }?.let { return it }
        val stored = prefs(context).getString(KEY_ID, "").orEmpty()
        plausibleId(stored)?.let {
            resolvedUserId = it
            return it
        }
        if (stored.isNotBlank()) {
            log.i("userId: dropping the alias '$stored' an older build stored, resolving for real")
            prefs(context).edit().remove(KEY_ID).apply()
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastUserIdAttempt < USER_ID_RETRY_MS) return ""
        lastUserIdAttempt = now
        val token = token(context)
        if (token.isBlank()) {
            log.w("userId: no access token, cannot resolve the account id")
            return ""
        }
        val profile = profile(context, token)
        if (profile != null) {
            persistIdentity(context, profile.id, profile.name, profile.imageUrl)
            resolvedUserId = profile.id
            log.i("userId: resolved from profileAttributes -> ${profile.id}")
            return profile.id
        }
        val fetched = fetchAccount(token)
        val id = plausibleId(fetched?.id.orEmpty())
        if (fetched != null && id != null) {
            persistIdentity(context, id, fetched.name, fetched.imageUrl)
            resolvedUserId = id
            log.i("userId: resolved from /v1/me -> $id")
            return id
        }
        log.w("userId: profileAttributes and /v1/me both failed, the id is still unknown")
        return ""
    }

    private suspend fun profile(context: Context, token: String): Account? {
        val json = Pathfinder.queryPost(
            context,
            token,
            "profileAttributes",
            "{}",
            fallbackToGet = false,
        )
        val node = json?.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
        if (node == null) {
            log.w("profileAttributes: no data.me.profile")
            return null
        }
        val id = plausibleId(node.optString("username"))
            ?: plausibleId(node.optString("uri").removePrefix("spotify:user:"))
            ?: plausibleId(node.optString("accountId"))
        if (id == null) {
            log.w("profileAttributes: no usable username, keys=${node.keys().asSequence().toList()} " +
                    "username='${node.optString("username")}' uri='${node.optString("uri")}'"
            )
            return null
        }
        return Account(
            id = id,
            name = node.optString("name"),
            imageUrl = node.optJSONObject("avatar")?.optJSONArray("sources")
                ?.optJSONObject(0)?.optString("url").orEmpty(),
        )
    }

    private fun persistIdentity(context: Context, id: String, name: String, imageUrl: String) {
        val editor = prefs(context).edit().putString(KEY_ID, id)
        if (name.isNotBlank()) editor.putString(KEY_NAME, name)
        if (imageUrl.isNotBlank()) editor.putString(KEY_IMAGE, imageUrl)
        editor.apply()
    }

    fun savedAt(context: Context): Long = prefs(context).getLong(KEY_SAVED_AT, 0L)

    fun captureCookies(context: Context): String? {
        val manager = cookieManagerOrNull() ?: return null
        val map = JSONObject()
        var hasSpDc = false
        COOKIE_DOMAINS.forEach { domain ->
            val cookies = manager.getCookie(domain)
            if (!cookies.isNullOrBlank()) {
                map.put(domain, cookies)
                if (cookies.contains("sp_dc=")) hasSpDc = true
            }
        }
        if (!hasSpDc) return null
        return map.toString()
    }

    fun saveLogin(context: Context, cookies: String, account: Account) {
        log.i("saveLogin name=${account.name} id=${account.id} product=${account.product} avatar=${account.imageUrl.isNotBlank()}")
        prefs(context).edit()
            .putString(KEY_SESSION, cookies)
            .putString(KEY_NAME, account.name)
            .putString(KEY_ID, account.id)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_IMAGE, account.imageUrl)
            .putString(KEY_PRODUCT, account.product)
            .putString(KEY_COUNTRY, account.country)
            .putString(KEY_COLOR, account.colorHex)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        resolvedUserId = ""
        applySession(context)
    }

    /**
     * Persists the profile accent color of the active account so avatar
     * placeholders can be tinted before the profile endpoint is ever hit.
     */
    fun saveProfileColor(context: Context, colorHex: String) {
        if (colorHex.isBlank()) return
        val id = activeAccountId(context)
        val editor = prefs(context).edit().putString(KEY_COLOR, colorHex)
        if (id.isNotBlank()) editor.putString("multi.$id.color", colorHex)
        editor.apply()
    }

    /**
     * Persists the accent color of a specific saved account — used to backfill
     * colors for accounts that were saved before their profile was ever opened,
     * so the account switcher can tint their avatars too.
     */
    fun saveAccountColor(context: Context, accountId: String, colorHex: String) {
        if (colorHex.isBlank() || accountId.isBlank()) return
        val editor = prefs(context).edit().putString("multi.$accountId.color", colorHex)
        if (activeAccountId(context) == accountId) editor.putString(KEY_COLOR, colorHex)
        editor.apply()
    }

    fun applySession(context: Context) {
        val session = prefs(context).getString(KEY_SESSION, null) ?: return
        val map = runCatching { JSONObject(session) }.getOrNull() ?: return
        val manager = cookieManagerOrNull() ?: run {
            log.w("applySession skipped, the cookie jar is unavailable")
            return
        }
        manager.setAcceptCookie(true)
        map.keys().asSequence().forEach { domain ->
            map.optString(domain, "").split(";")
                .map { it.trim() }
                .filter { it.contains("=") }
                .forEach { manager.setCookie(domain, it) }
        }
        manager.flush()
        log.i("applySession restored ${map.length()} domains")
    }

    /**
     * Empties the WebView cookie jar (leaving the stored session, tokens and
     * per-account snapshots in prefs untouched). The add-account flow calls
     * this before its login webview opens: without it the live sp_dc of the
     * signed-in account makes accounts.spotify.com skip the login form and
     * bounce straight into the player, which the poll loop resolves as the
     * already-saved account — a duplicate that is refused, so the user ends
     * up back on the original account without ever seeing the login page.
     * Cancelling the flow restores the jar via [applySession].
     */
    fun clearCookies() {
        val manager = cookieManagerOrNull() ?: run {
            log.w("clearCookies skipped, the cookie jar is unavailable")
            return
        }
        manager.removeAllCookies(null)
        manager.flush()
        log.i("clearCookies emptied the cookie jar")
    }

    fun logout(context: Context) {
        log.i("logout")
        val active = activeAccountId(context)
        if (active.isNotBlank()) {
            forgetAccount(context, active)
        }
        prefs(context).edit()
            .remove(KEY_SESSION)
            .remove(KEY_NAME)
            .remove(KEY_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_IMAGE)
            .remove(KEY_PRODUCT)
            .remove(KEY_COUNTRY)
            .remove(KEY_COLOR)
            .remove(KEY_SAVED_AT)
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_AT)
            .remove(KEY_TOKEN_EXP)
            .remove(KEY_CLIENT_TOKEN)
            .remove(KEY_CLIENT_TOKEN_EXP)
            .remove(KEY_MULTI_ACTIVE)
            .apply()
        clearTokenMemos()
        resolvedUserId = ""
        val manager = cookieManagerOrNull()
        if (manager != null) {
            manager.removeAllCookies(null)
            manager.flush()
        } else {
            log.w("logout: cookie jar unavailable, the cookies stay put")
        }
    }

    suspend fun fetchToken(context: Context, spDc: String): String? {
        WebToken.mint(context, spDc)?.let { return it }
        val jar = cookieManagerOrNull()?.getCookie("https://open.spotify.com")
        val cookieHeader = jar?.takeIf { it.isNotBlank() } ?: "sp_dc=$spDc"
        log.i("fetchToken jar=${!jar.isNullOrBlank()} cookies=${cookieHeader.length}B")
        val response = Net.get(
            url = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player",
            headers = mapOf(
                "User-Agent" to DESKTOP_UA,
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Referer" to "https://open.spotify.com/",
                "Origin" to "https://open.spotify.com",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "Cookie" to cookieHeader,
            ),
            tag = "get_access_token",
        ) ?: return null
        val json = response.json()
        if (json == null) {
            log.w("fetchToken http=${response.code} not-json")
            return null
        }
        val anonymous = json.optBoolean("isAnonymous", false)
        val token = json.optString("accessToken")
        log.i("fetchToken http=${response.code} anonymous=$anonymous tokenLen=${token.length}")
        return token.takeIf { it.isNotBlank() && !anonymous }
    }

    suspend fun fetchAccount(token: String): Account? {
        val response = Net.get(
            url = "https://api.spotify.com/v1/me",
            headers = mapOf(
                "Authorization" to "Bearer $token",
                "Accept" to "application/json",
                "User-Agent" to DESKTOP_UA,
            ),
            tag = "me",
        ) ?: return null
        val json = response.json()
        if (json == null) {
            log.w("fetchAccount http=${response.code} not-json")
            return null
        }
        val images = json.optJSONArray("images")
        val id = json.optString("id").plainText()
        val name = json.optString("display_name").plainText()
        log.i("fetchAccount http=${response.code} id=$id name=$name")
        if (id.isBlank()) {
            log.w("fetchAccount http=${response.code} has no id, keys=${json.keys().asSequence().toList()}")
            return null
        }
        return Account(
            id = id,
            name = name.ifBlank { id },
            email = json.optString("email").plainText(),
            imageUrl = if (images != null && images.length() > 0) {
                images.getJSONObject(0).optString("url").plainText()
            } else "",
            product = json.optString("product").plainText(),
            country = json.optString("country").plainText(),
        )
    }
}
