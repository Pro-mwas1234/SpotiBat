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

package com.mwask.bat.backend.core.store

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {

    @Volatile
    private var caches: MutableMap<String, MutableMap<String, AppPrefEntry>>? = null

    private val lock = Any()

    fun prefs(context: Context, store: String): Prefs = Prefs(context.applicationContext, store)

    fun edit(context: Context, store: String): Editor = Editor(context.applicationContext, store)

    fun getString(context: Context, store: String, key: String, default: String? = null): String? {
        val entry = ensure(context, store)[key] ?: return default
        return entry.stringValue ?: default
    }

    fun getLong(context: Context, store: String, key: String, default: Long = 0L): Long {
        val entry = ensure(context, store)[key] ?: return default
        return entry.longValue ?: default
    }

    fun getInt(context: Context, store: String, key: String, default: Int = 0): Int {
        val entry = ensure(context, store)[key] ?: return default
        return entry.longValue?.toInt() ?: default
    }

    fun getBoolean(context: Context, store: String, key: String, default: Boolean = false): Boolean {
        val entry = ensure(context, store)[key] ?: return default
        entry.longValue?.let { return it != 0L }
        entry.stringValue?.let { return it == "true" }
        return default
    }

    fun putString(context: Context, store: String, key: String, value: String?) {
        if (value == null) {
            remove(context, store, key)
            return
        }
        put(context, AppPrefEntry(store = store, key = key, stringValue = value))
    }

    fun putLong(context: Context, store: String, key: String, value: Long) {
        put(context, AppPrefEntry(store = store, key = key, longValue = value))
    }

    fun putInt(context: Context, store: String, key: String, value: Int) {
        put(context, AppPrefEntry(store = store, key = key, longValue = value.toLong()))
    }

    fun putBoolean(context: Context, store: String, key: String, value: Boolean) {
        put(context, AppPrefEntry(store = store, key = key, longValue = if (value) 1L else 0L))
    }

    fun remove(context: Context, store: String, key: String) {
        synchronized(lock) {
            ensure(context, store).remove(key)
            db(context).dao().remove(store, key)
        }
    }

    fun contains(context: Context, store: String, key: String): Boolean =
        ensure(context, store).containsKey(key)

    fun all(context: Context, store: String): Map<String, *> {
        val map = LinkedHashMap<String, Any?>()
        for ((k, e) in ensure(context, store)) {
            when {
                e.stringValue != null -> map[k] = e.stringValue
                e.longValue != null -> map[k] = e.longValue
            }
        }
        return map
    }

    private fun put(context: Context, entry: AppPrefEntry) {
        synchronized(lock) {
            ensure(context, entry.store)[entry.key] = entry
            db(context).dao().put(entry)
        }
    }

    private fun db(context: Context): AppPrefsDb = AppPrefsDb.get(context.applicationContext)

    private fun ensure(context: Context, store: String): MutableMap<String, AppPrefEntry> {
        val allCaches = caches ?: synchronized(lock) {
            caches ?: mutableMapOf<String, MutableMap<String, AppPrefEntry>>().also { caches = it }
        }
        synchronized(lock) {
            allCaches[store]?.let { return it }
            val app = context.applicationContext
            val database = db(app)
            migrate(app, database, store)
            val map = database.dao().all(store).associateBy { it.key }.toMutableMap()
            allCaches[store] = map
            return map
        }
    }

    private fun migrate(context: Context, database: AppPrefsDb, store: String) {
        val prefs = context.getSharedPreferences(store, Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) return
        val dao = database.dao()
        for ((key, value) in all) {
            val entry = when (value) {
                is String -> AppPrefEntry(store = store, key = key, stringValue = value)
                is Long -> AppPrefEntry(store = store, key = key, longValue = value)
                is Int -> AppPrefEntry(store = store, key = key, longValue = value.toLong())
                is Boolean -> AppPrefEntry(store = store, key = key, longValue = if (value) 1L else 0L)
                is Float -> AppPrefEntry(store = store, key = key, stringValue = value.toString())
                is Set<*> -> AppPrefEntry(store = store, key = key, stringValue = value.filterIsInstance<String>().joinToString("\u0001"))
                else -> null
            }
            if (entry != null) dao.put(entry)
        }
        prefs.edit().clear().apply()
    }

    class Editor(private val context: Context, private val store: String) {
        private val ops = mutableListOf<() -> Unit>()

        fun putString(key: String, value: String?): Editor {
            ops.add { putString(context, store, key, value) }
            return this
        }

        fun putLong(key: String, value: Long): Editor {
            ops.add { putLong(context, store, key, value) }
            return this
        }

        fun putInt(key: String, value: Int): Editor {
            ops.add { putInt(context, store, key, value) }
            return this
        }

        fun putBoolean(key: String, value: Boolean): Editor {
            ops.add { putBoolean(context, store, key, value) }
            return this
        }

        fun remove(key: String): Editor {
            ops.add { remove(context, store, key) }
            return this
        }

        fun clear(): Editor {
            ops.add {
                synchronized(lock) {
                    ensure(context, store).clear()
                    db(context).dao().clear(store)
                }
            }
            return this
        }

        fun apply() {
            for (op in ops) op()
        }

        fun commit(): Boolean {
            apply()
            return true
        }
    }

    class Prefs(private val context: Context, private val store: String) {
        fun getString(key: String, default: String?): String? = getString(context, store, key, default)
        fun getLong(key: String, default: Long): Long = getLong(context, store, key, default)
        fun getInt(key: String, default: Int): Int = getInt(context, store, key, default)
        fun getBoolean(key: String, default: Boolean): Boolean = getBoolean(context, store, key, default)
        fun contains(key: String): Boolean = contains(context, store, key)
        val all: Map<String, *> get() = all(context, store)
        fun edit(): Editor = edit(context, store)
    }
}
