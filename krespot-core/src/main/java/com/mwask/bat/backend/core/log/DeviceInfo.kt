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

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.webkit.WebView
import com.mwask.bat.backend.core.auth.EncoreAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Builds a plain-text report of everything worth knowing when debugging this
 * install: app build, Android build, hardware, display, memory, storage,
 * network, battery, WebView, the Widevine/DRM stack and the (secret-free)
 * length or remaining lifetime.
 */
object DeviceInfo {

    /** The Widevine scheme UUID (MediaDrm.WIDEVINE only exists from API 31). */
    private val WIDEVINE: UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    fun collect(context: Context): String {
        val app = context.applicationContext
        val out = StringBuilder()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US)
            .format(Date(System.currentTimeMillis()))
        val uptime = formatDuration(SystemClock.elapsedRealtime())

        out.appendLine("SpotiBat device report")
        out.appendLine("generated: $stamp (uptime $uptime)")
        out.appendLine()

        section(out, "App")
        val pack = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        line(out, "package", app.packageName)
        line(out, "versionName", pack?.versionName.orEmpty().ifBlank { "unknown" })
        line(out, "versionCode", pack?.let { versionCodeOf(it) } ?: "unknown")
        line(out, "targetSdk", app.applicationInfo?.targetSdkVersion?.toString() ?: "unknown")
        line(out, "minSdk", "24")
        line(out, "debuggable", ((app.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0).toString())
        line(out, "dataDir", app.filesDir.parentFile?.absolutePath ?: "unknown")
        line(out, "pid", Process.myPid().toString())
        line(out, "threads", Thread.activeCount().toString())
        if (pack != null) {
            line(out, "installed", stamp(pack.firstInstallTime))
            line(out, "updated", stamp(pack.lastUpdateTime))
        }

        section(out, "Session")
        line(out, "loggedIn", runCatching { EncoreAuth.isLoggedIn(app) }.getOrDefault(false).toString())
        line(out, "spDcLength", EncoreAuth.spDc(app).length.toString())
        val account = runCatching { EncoreAuth.account(app) }.getOrNull()
        if (account != null) {
            line(out, "account", account.name.ifBlank { "unknown" })
            line(out, "accountId", account.id.ifBlank { "unknown" })
            if (account.email.isNotBlank()) line(out, "email", account.email)
            if (account.product.isNotBlank()) line(out, "product", account.product)
            if (account.country.isNotBlank()) line(out, "country", account.country)
        }
        val saved = runCatching { EncoreAuth.savedAccounts(app) }.getOrDefault(emptyList())
        line(out, "savedAccounts", saved.size.toString())
        saved.forEachIndexed { i, one ->
            line(out, "  account[$i]", "${one.name.ifBlank { "unknown" }} <${one.email.ifBlank { "-" }}> ${one.product}")
        }
        val tokenLeft = runCatching { EncoreAuth.tokenRemainingMs(app) }.getOrDefault(-2L)
        line(out, "accessToken", when {
            tokenLeft == -2L -> "unknown"
            EncoreAuth.token(app).isBlank() -> "none"
            tokenLeft < 0L -> "present (no expiry)"
            else -> "present, ${formatDuration(tokenLeft)} left"
        })
        val clientLeft = runCatching { EncoreAuth.clientTokenRemainingMs(app) }.getOrDefault(-2L)
        line(out, "clientToken", when {
            clientLeft == -2L -> "unknown"
            EncoreAuth.clientToken(app).isBlank() -> "none"
            clientLeft < 0L -> "present (no expiry)"
            else -> "present, ${formatDuration(clientLeft)} left"
        })

        section(out, "Device")
        line(out, "manufacturer", Build.MANUFACTURER)
        line(out, "model", Build.MODEL)
        line(out, "device", Build.DEVICE)
        line(out, "product", Build.PRODUCT)
        line(out, "brand", Build.BRAND)
        line(out, "board", Build.BOARD)
        line(out, "hardware", Build.HARDWARE)
        line(out, "bootloader", Build.BOOTLOADER)
        line(out, "buildId", Build.ID)
        line(out, "incremental", Build.VERSION.INCREMENTAL)
        line(out, "fingerprint", Build.FINGERPRINT)
        line(out, "emulator", isEmulator().toString())

        section(out, "Android")
        line(out, "release", Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" })
        line(out, "sdkInt", Build.VERSION.SDK_INT.toString())
        line(out, "securityPatch", Build.VERSION.SECURITY_PATCH)
        line(out, "codename", Build.VERSION.CODENAME.orEmpty().ifBlank { "REL" })
        line(out, "locale", Locale.getDefault().toLanguageTag())
        line(out, "timezone", TimeZone.getDefault().id)

        section(out, "Region")
        val locale = Locale.getDefault()
        line(out, "country", "${locale.country.ifBlank { "?" }} ${locale.displayCountry}")
        runCatching {
            val telephony = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephony != null) {
                line(out, "simCountry", telephony.simCountryIso.ifBlank { "?" })
                line(out, "networkCountry", telephony.networkCountryIso.ifBlank { "?" })
                line(out, "carrier", telephony.simOperatorName
                    .ifBlank { telephony.networkOperatorName }
                    .ifBlank { "?" }
                )
            }
        }

        section(out, "System on chip")
        line(out, "cores", Runtime.getRuntime().availableProcessors().toString())
        line(out, "abis", Build.SUPPORTED_ABIS.joinToString(", "))
        if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
            line(out, "abis64", Build.SUPPORTED_64_BIT_ABIS.joinToString(", "))
        }

        section(out, "Memory")
        runCatching {
            val runtime = Runtime.getRuntime()
            line(out, "javaHeap", "max=${formatBytes(runtime.maxMemory())} " +
                "used=${formatBytes(runtime.totalMemory() - runtime.freeMemory())} " +
                "free=${formatBytes(runtime.freeMemory())}"
            )
            line(out, "nativeHeap", formatBytes(Debug.getNativeHeapAllocatedSize()))
        }
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activity?.getMemoryInfo(memory)
        if (activity != null) {
            line(out, "total", formatBytes(memory.totalMem))
            line(out, "available", formatBytes(memory.availMem))
            line(out, "threshold", formatBytes(memory.threshold))
            line(out, "lowRamDevice", activity.isLowRamDevice.toString())
            line(out, "largeHeap", ((app.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP != 0).toString())
        }

        section(out, "Storage")
        statFs(out, "data", Environment.getDataDirectory())
        statFs(out, "externalFiles", app.getExternalFilesDir(null) ?: app.filesDir)
        runCatching {
            @Suppress("DEPRECATION")
            statFs(out, "externalRoot", Environment.getExternalStorageDirectory())
        }
        line(out, "filesDir", app.filesDir.absolutePath)

        section(out, "Display")
        val metrics = app.resources.displayMetrics
        line(out, "pixels", "${metrics.widthPixels} x ${metrics.heightPixels}")
        line(out, "sizeDp", "${(metrics.widthPixels / metrics.density).toInt()}x" +
            "${(metrics.heightPixels / metrics.density).toInt()}dp"
        )
        line(out, "densityDpi", metrics.densityDpi.toString())
        line(out, "density", metrics.density.toString())
        line(out, "xdpi/ydpi", "%.1f / %.1f".format(Locale.US, metrics.xdpi, metrics.ydpi))
        line(out, "orientation",
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                "landscape"
            } else {
                "portrait"
            }
        )
        line(out, "refreshRateHz", refreshHz(context))

        section(out, "Network")
        runCatching {
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivity?.activeNetwork
            val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
            if (capabilities == null) {
                line(out, "transport", "none")
            } else {
                val transports = ArrayList<String>(4)
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                    transports.add("wifi")
                }
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    transports.add("cellular")
                }
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    transports.add("ethernet")
                }
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    transports.add("vpn")
                }
                if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    transports.add("bluetooth")
                }
                line(out, "transport", if (transports.isEmpty()) "other" else transports.joinToString("+"))
                line(out, "validated",
                    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString()
                )
                line(out, "metered", (connectivity?.isActiveNetworkMetered ?: true).toString())
                line(out, "bandwidth",
                    "down=${capabilities.linkDownstreamBandwidthKbps}kbps " +
                        "up=${capabilities.linkUpstreamBandwidthKbps}kbps"
                )
            }
        }.onFailure { line(out, "transport", "unavailable: ${it.message}") }

        section(out, "Battery")
        runCatching {
            val sticky = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            if (sticky != null) {
                val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = when (sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "full"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
                    else -> "unknown"
                }
                if (level >= 0 && scale > 0) {
                    line(out, "level", "${level * 100 / scale}% ($status)")
                } else {
                    line(out, "level", "unknown ($status)")
                }
            } else {
                line(out, "level", "unknown")
            }
        }.onFailure { line(out, "level", "unavailable: ${it.message}") }

        section(out, "WebView")
        line(out, "engine", runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "unknown (API < 26)")

        section(out, "DRM / Widevine")
        writeWidevine(out)

        return out.toString().trim() + "\n"
    }

    private fun writeWidevine(out: StringBuilder) {
        val supported = runCatching { MediaDrm.isCryptoSchemeSupported(WIDEVINE) }.getOrDefault(false)
        line(out, "cryptoSchemeSupported", supported.toString())
        if (!supported) return
        var drm: MediaDrm? = null
        runCatching {
            drm = MediaDrm(WIDEVINE)
            val instance = drm ?: return
            for (name in listOf(
                "vendor", "version", "description", "algorithms",
                "securityLevel", "systemId", "oemCryptoApiVersion",
                "maxHdcpLevel", "connectedHdcpLevel", "usageReportingSupport",
            )) {
                val value = runCatching { instance.getPropertyString(name) }.getOrNull()
                line(out, name, value?.ifBlank { "-" } ?: "n/a")
            }
            val session = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    instance.openSession(MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO)
                } else {
                    instance.openSession()
                }
            }.getOrNull()
            if (session != null) {
                val level = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        instance.getSecurityLevel(session).toString()
                    } else {
                        "unknown (API < 28)"
                    }
                }.getOrDefault("unknown")
                line(out, "sessionSecurityLevel", level)
                runCatching { instance.closeSession(session) }
            } else {
                line(out, "sessionSecurityLevel", "session refused (not provisioned?)")
            }
        }.onFailure {
            line(out, "mediaDrm", "failed: ${it.javaClass.simpleName}: ${it.message}")
        }
        runCatching { drm?.release() }
    }

    private fun statFs(out: StringBuilder, label: String, dir: File) {
        runCatching {
            val stats = StatFs(dir.absolutePath)
            line(out, "$label.available", formatBytes(stats.availableBytes))
            line(out, "$label.total", formatBytes(stats.totalBytes))
        }.onFailure { line(out, label, "unavailable: ${it.message}") }
    }

    /**
     * The refresh rate through the context the caller handed in. A UI context
     * answers getDisplay() from API 30; an application context throws instead
     * of returning null, so each attempt is guarded on its own to leave the
     * deprecated WindowManager default display as a working fallback. Reading
     * both from the application context is what used to report "unknown".
     */
    private fun refreshHz(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val direct = runCatching { context.display }.getOrNull()
            if (direct != null && direct.refreshRate > 0f) {
                return "%.1f".format(Locale.US, direct.refreshRate)
            }
        }
        @Suppress("DEPRECATION")
        val fallback = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay?.refreshRate
        return fallback?.takeIf { it > 0f }
            ?.let { "%.1f".format(Locale.US, it) } ?: "unknown"
    }

    private fun stamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    private fun section(out: StringBuilder, title: String) {
        out.appendLine()
        out.appendLine("── $title " + "─".repeat((26 - title.length).coerceAtLeast(1)))
    }

    private fun line(out: StringBuilder, key: String, value: String) {
        out.append(key.padEnd(24)).append(' ').append(value).appendLine()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "unknown"
        val unit = 1024.0
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit)).toInt().coerceAtMost(4)
        val prefix = arrayOf("KB", "MB", "GB", "TB")[exp - 1]
        return "%.1f %s".format(Locale.US, bytes / Math.pow(unit, exp.toDouble()), prefix)
    }

    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "unknown"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}

/** Long version code across old and new Android. */
private fun versionCodeOf(info: android.content.pm.PackageInfo): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode.toString()
    } else {
        @Suppress("DEPRECATION") info.versionCode.toString()
    }
