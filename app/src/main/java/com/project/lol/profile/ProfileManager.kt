package com.project.lol.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {

    data class Profile(
        val name: String,
        val cookies: String,
        val savedAt: Long
    )

    private const val PREFS = "spotilol_profiles"
    private const val KEY_PROFILES = "profiles"

    private val COOKIE_DOMAINS = listOf(
        "https://open.spotify.com",
        "https://accounts.spotify.com",
        "https://api-partner.spotify.com",
        "https://gew4-spclient.spotify.com",
        "https://spclient.wg.spotify.com",
        "https://api.spotify.com",
        "https://www.spotify.com"
    )

    @Volatile
    private var cachedPrefs: SharedPreferences? = null
    private val prefsLock = Any()

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedPrefs ?: createPrefs(context.applicationContext).also { cachedPrefs = it }
        }
    }

    private fun createPrefs(appContext: Context): SharedPreferences {
        try {
            return createEncryptedPrefs(appContext)
        } catch (_: Exception) {
        }

        try {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Exception) {
        }

        return try {
            createEncryptedPrefs(appContext)
        } catch (_: Exception) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getProfiles(context: Context): List<Profile> {
        val raw = try {
            prefs(context).getString(KEY_PROFILES, null)
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                val cookies = o.optString("cookies")
                if (name.isBlank() || cookies.isBlank()) null
                else Profile(name, cookies, o.optLong("savedAt", 0L))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfile(context: Context, name: String, cookies: String) {
        val trimmed = name.trim()
        val list = getProfiles(context).toMutableList()
        list.removeAll { it.name == trimmed }
        list.add(0, Profile(trimmed, cookies, System.currentTimeMillis()))
        writeProfiles(context, list)
    }

    fun deleteProfile(context: Context, name: String) {
        writeProfiles(context, getProfiles(context).filterNot { it.name == name })
    }

    private fun writeProfiles(context: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("cookies", p.cookies)
                    .put("savedAt", p.savedAt)
            )
        }
        prefs(context).edit { putString(KEY_PROFILES, arr.toString()) }
    }

    fun captureSession(): String? {
        val map = JSONObject()
        var hasSpDc = false
        for (domain in COOKIE_DOMAINS) {
            val cookies = CookieManager.getInstance().getCookie(domain)
            if (!cookies.isNullOrBlank()) {
                map.put(domain, cookies)
                if (cookies.contains("sp_dc=")) hasSpDc = true
            }
        }
        if (!hasSpDc) return null
        return map.toString()
    }

    fun applyProfile(context: Context, json: String, onComplete: (Boolean) -> Unit) {
        val entries = try {
            val map = JSONObject(json)
            val out = mutableListOf<Pair<String, String>>()
            val keys = map.keys()
            while (keys.hasNext()) {
                val domain = keys.next()
                out.add(domain to map.getString(domain))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
        if (entries.isEmpty()) {
            postComplete(onComplete, false)
            return
        }

        CookieManager.getInstance().removeAllCookies {
            try {
                for ((domain, cookies) in entries) {
                    for (pair in cookies.split(";")) {
                        val cookie = pair.trim()
                        if (cookie.contains("=")) {
                            CookieManager.getInstance().setCookie(domain, cookie)
                        }
                    }
                }
                CookieManager.getInstance().flush()
                context.getSharedPreferences("spotilol_prefs", Context.MODE_PRIVATE)
                    .edit { putBoolean("LoggedIn", true) }
                postComplete(onComplete, true)
            } catch (_: Exception) {
                postComplete(onComplete, false)
            }
        }
    }

    private fun postComplete(onComplete: (Boolean) -> Unit, result: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onComplete(result)
        } else {
            Handler(Looper.getMainLooper()).post { onComplete(result) }
        }
    }
}