package com.mwask.bat.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetArt {

    private const val DISK_DIR = "widget_art"
    private const val DISK_LIMIT = 12
    private const val MAX_SIDE = 640
    private const val ROUND_FRACTION = 0.075f
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memory = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
                size > 6
        },
    )

    private val tints = Collections.synchronizedMap(LinkedHashMap<String, Int>())

    private val inFlight = mutableMapOf<String, Job>()

    fun cached(url: String): Bitmap? = memory[url]

    fun prefetch(context: Context, url: String, onLoaded: () -> Unit = {}) {
        if (url.isBlank() || memory.containsKey(url)) return
        synchronized(inFlight) {
            if (inFlight.containsKey(url)) return
            inFlight[url] = scope.launch {
                val bitmap = load(context.applicationContext, url)
                synchronized(inFlight) { inFlight.remove(url) }
                if (bitmap != null) {
                    memory[url] = bitmap
                    withContext(Dispatchers.Main) { onLoaded() }
                }
            }
        }
    }

    fun tintFor(url: String, bitmap: Bitmap?, fallback: Int): Int {
        if (bitmap == null) return fallback
        tints[url]?.let { return it }
        val dominant = Palette.from(bitmap).maximumColorCount(24).generate().dominantSwatch
            ?: return fallback
        val tint = ColorUtils.blendARGB(dominant.rgb, 0xFF101010.toInt(), 0.74f)
        tints[url] = tint
        return tint
    }

    private suspend fun load(context: Context, url: String): Bitmap? {
        readDisk(context, url)?.let { return it }
        val bitmap = download(url)?.let(::roundCorners) ?: return null
        writeDisk(context, url, bitmap)
        return bitmap
    }

    private fun roundCorners(source: Bitmap): Bitmap = runCatching {
        val radius = (minOf(source.width, source.height) * ROUND_FRACTION).coerceAtLeast(8f)
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val path = android.graphics.Path().apply {
            addRoundRect(
                android.graphics.RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
                radius,
                radius,
                android.graphics.Path.Direction.CW,
            )
        }
        canvas.drawPath(path, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        output
    }.getOrDefault(source)

    private fun diskFile(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, DISK_DIR), name)
    }

    private fun readDisk(context: Context, url: String): Bitmap? = runCatching {
        val file = diskFile(context, url)
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )?.also { memory[url] = it }
    }.getOrNull()

    private fun writeDisk(context: Context, url: String, bitmap: Bitmap) {
        runCatching {
            val dir = File(context.cacheDir, DISK_DIR)
            if (!dir.exists()) dir.mkdirs()
            trimDisk(dir)
            FileOutputStream(diskFile(context, url)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    private fun trimDisk(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        val excess = files.size - DISK_LIMIT + 1
        if (excess > 0) {
            files.take(excess).forEach { it.delete() }
        }
    }

    private fun download(url: String): Bitmap? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
