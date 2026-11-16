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

package com.mwask.bat.backend.core.log

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object Logs {

    const val TAG = "SpotiBat"

    enum class Module(val label: String) {
        AUTH("auth"),
        LOGIN("login"),
        LOGIN_FLOW("login-flow"),
        SILENT("silent"),
        WEBVIEW("webview"),
        CAPTURE("capture"),
        MAIN("main"),

        NET("net"),
        HASHES("hashes"),
        CLIENT_TOKEN("client-token"),
        PATHFINDER("pathfinder"),

        HOME("home"),
        SEARCH("search"),
        BROWSE("browse"),
        LIBRARY("library"),
        LIBRARY_PAGE("library-page"),
        COLLECTION("collection"),
        COLLECTION2("collection2"),
        PLAYLIST("playlist"),
        ALBUM("album"),
        ARTIST("artist"),
        FOLDER("folder"),
        ROOTLIST("rootlist"),
        TRACKS("tracks"),
        PODCASTS("podcasts"),
        WHATS_NEW("whats-new"),
        NOTIFICATIONS("notifications"),
        PROFILE("profile"),
        SETTINGS("settings"),
        LYRICS("lyrics"),
        HISTORY("history"),
        RECOMMENDATIONS("recommendations"),

        CONNECT("connect"),
        CLUSTER("cluster"),
        DEALER("dealer"),
        DEALER_HOST("dealer-host"),
        COMMAND("command"),
        REGISTER("register"),
        STATE("state"),
        MELODY("melody"),

        PLAYER("player"),
        ENGINE("engine"),
        SINK("sink"),
        FOCUS("focus"),
        SOURCE("source"),
        DRM("drm"),
        MP3("mp3"),

        BRIDGE("bridge"),
        HOST("host"),
        SERVICE("service"),

        SHELL("shell"),
        SHARE("share"),
    }

    enum class Level(val rank: Int) {
        VERBOSE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        OFF(5),
    }

    @Volatile
    var floor: Level = Level.VERBOSE

    private val modules = ConcurrentHashMap<Module, Level>()

    init {
        modules[Module.MAIN] = Level.INFO
    }

    fun level(module: Module, level: Level) {
        modules[module] = level
    }

    fun enabled(module: Module, level: Level): Boolean = level.rank >= (modules[module] ?: floor).rank

    fun of(module: Module): Logger = Logger(module)

    internal fun write(module: Module, level: Level, message: String, error: Throwable?) {
        val line = "[${module.label}] $message"
        when (level) {
            Level.VERBOSE -> Log.v(TAG, line, error)
            Level.DEBUG -> Log.d(TAG, line, error)
            Level.INFO -> Log.i(TAG, line, error)
            Level.WARN -> Log.w(TAG, line, error)
            Level.ERROR -> Log.e(TAG, line, error)
            Level.OFF -> Unit
        }
    }
}

class Logger internal constructor(private val module: Logs.Module) {

    fun v(message: String) = send(Logs.Level.VERBOSE, message)

    fun d(message: String) = send(Logs.Level.DEBUG, message)

    fun i(message: String) = send(Logs.Level.INFO, message)

    fun w(message: String, error: Throwable? = null) = send(Logs.Level.WARN, message, error)

    fun e(message: String, error: Throwable? = null) = send(Logs.Level.ERROR, message, error)

    private fun send(level: Logs.Level, message: String, error: Throwable? = null) {
        if (!Logs.enabled(module, level)) return
        Logs.write(module, level, message, error)
    }
}
