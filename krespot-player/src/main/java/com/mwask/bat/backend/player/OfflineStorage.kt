package com.mwask.bat.backend.player

import android.content.Context
import java.io.File

/**
 * Where downloaded audio lives. App-private storage by default; Settings can point it at a
 * removable volume, in which case the songs already on disk are moved across. Downloads,
 * playback and the library index all resolve their file through [audioDir], so one move keeps
 * everything pointing at the same folder.
 */
object OfflineStorage {

    private const val PREFS = "spotibat_offline"
    private const val KEY_ROOT = "storage_root"
    private const val AUDIO_DIR = "offline_audio"

    /** Folder created on a removable volume, so the songs are not buried under Android/data. */
    private const val FOLDER = "SpotiBat"
    private const val COPY_BUFFER_BYTES = 256 * 1024

    const val INTERNAL_ID = "internal"

    /** One place the songs can be kept. */
    data class Option(
        val id: String,
        val label: String,
        val path: String,
        val availableBytes: Long,
        val removable: Boolean,
    )

    /** The folders offered in Settings: app storage first, then any removable volume. */
    fun options(context: Context): List<Option> {
        val app = context.applicationContext
        val internal = app.filesDir
        val found = mutableListOf(
            Option(
                id = INTERNAL_ID,
                label = "Phone storage",
                path = internal.absolutePath,
                availableBytes = internal.usableSpace,
                removable = false,
            ),
        )
        app.getExternalFilesDirs(null).forEachIndexed { index, dir ->
            if (dir == null) return@forEachIndexed
            if (isPrimaryVolume(dir)) return@forEachIndexed
            val root = File(dir, FOLDER)
            if (!writable(root)) return@forEachIndexed
            found += Option(
                id = "volume:$index",
                label = volumeLabel(dir),
                path = root.absolutePath,
                availableBytes = root.usableSpace,
                removable = true,
            )
        }
        return found
    }

    /** The folder holding the songs, created if it is missing. */
    fun audioDir(context: Context): File {
        val dir = File(configuredRoot(context) ?: context.applicationContext.filesDir, AUDIO_DIR)
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }

    /** The root the user picked, or null while the songs stay in app storage. */
    fun selectedPath(context: Context): String? = configuredRoot(context)?.absolutePath

    /**
     * Points the downloads at [path] and carries the songs already saved there. Returns false
     * (and leaves the setting alone) if the new folder cannot be written or a song cannot be
     * moved, so the caller can tell the user instead of losing their library.
     */
    fun setRoot(context: Context, path: String): Boolean {
        val app = context.applicationContext
        val target = when {
            path.isBlank() -> app.filesDir
            allowedRoots(app).any { it.absolutePath == File(path).absolutePath } -> File(path)
            else -> return false
        }
        if (!writable(target)) return false
        val current = audioDir(app)
        val next = File(target, AUDIO_DIR)
        if (current.absolutePath == next.absolutePath) {
            prefs(app).edit().remove(KEY_ROOT).apply()
            return true
        }
        if (!next.isDirectory && !next.mkdirs()) return false
        if (!moveAudio(current, next)) return false
        val stored = if (target.absolutePath == app.filesDir.absolutePath) null else target.absolutePath
        prefs(app).edit().apply {
            if (stored == null) remove(KEY_ROOT) else putString(KEY_ROOT, stored)
        }.apply()
        return true
    }

    /** The id of the option currently in use, matching what [options] hands out. */
    fun selectedId(context: Context): String {
        val selected = selectedPath(context) ?: return INTERNAL_ID
        return options(context).firstOrNull { it.path == selected }?.id ?: INTERNAL_ID
    }

    private fun moveAudio(from: File, to: File): Boolean {
        val files = from.listFiles() ?: return true
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        for (file in files) {
            if (!file.isFile) continue
            // Half-written downloads are left behind; the song is queued again by the user.
            if (file.name.endsWith(".part")) {
                file.delete()
                continue
            }
            val target = File(to, file.name)
            if (target.absolutePath == file.absolutePath && target.exists()) continue
            val moved = runCatching {
                file.inputStream().use { input ->
                    target.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                target.length() == file.length()
            }.getOrDefault(false)
            if (!moved) {
                runCatching { target.delete() }
                return false
            }
            file.delete()
        }
        return true
    }

    /** Remembers nothing about a root that no longer exists, e.g. an ejected card. */
    private fun configuredRoot(context: Context): File? {
        val app = context.applicationContext
        val stored = prefs(app).getString(KEY_ROOT, null)?.takeIf { it.isNotBlank() } ?: return null
        val root = File(stored)
        if (allowedRoots(app).none { it.absolutePath == root.absolutePath }) return null
        if (!writable(root)) return null
        return root
    }

    /** Folders the app may write to without asking for permission. */
    private fun allowedRoots(context: Context): List<File> {
        val roots = mutableListOf(context.filesDir)
        context.getExternalFilesDirs(null).forEach { dir ->
            if (dir != null) roots += File(dir, FOLDER)
        }
        return roots
    }

    private fun writable(dir: File): Boolean = runCatching {
        if (dir.exists() && !dir.isDirectory) return@runCatching false
        if (!dir.exists() && !dir.mkdirs()) return@runCatching false
        val probe = File(dir, ".spotibat-probe")
        probe.writeText("ok")
        probe.delete()
    }.getOrDefault(false)

    private fun isPrimaryVolume(dir: File): Boolean =
        dir.absolutePath.replace('\\', '/').startsWith("/storage/emulated/")

    private fun volumeLabel(dir: File): String {
        val segments = dir.absolutePath.replace('\\', '/').trim('/').split('/')
        val id = segments.getOrNull(1).orEmpty()
        return if (id.isNotBlank() && id != "emulated" && id != "self") "SD card ($id)" else "SD card"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
