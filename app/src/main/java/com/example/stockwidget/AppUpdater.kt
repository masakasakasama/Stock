package com.example.stockwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update: checks the GitHub release for a newer build and, if the user
 * has granted "install unknown apps" to this app, downloads and installs it
 * silently via PackageInstaller (no user action on Android 12+).
 *
 * Requires the GitHub repository to be public so the release assets can be
 * downloaded without authentication.
 */
object AppUpdater {

    private const val VERSION_URL =
        "https://github.com/masakasakasama/Stock/releases/download/debug-latest/latest-version.txt"
    private const val APK_URL =
        "https://github.com/masakasakasama/Stock/releases/download/debug-latest/stock-widget-debug.apk"

    private const val PREFS = "stock_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private val MIN_INTERVAL_MS = 3L * 60 * 60 * 1000 // 3 hours

    private const val UA = "StockWidget-Updater"

    /** Throttled background check. Safe to call from widget updates. */
    fun maybeCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < MIN_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        Thread {
            runCatching { check(context) }
        }.start()
    }

    /** Forced immediate check (e.g. from the config screen). */
    fun checkNow(context: Context) {
        Thread { runCatching { check(context) } }.start()
    }

    private fun check(context: Context) {
        val latest = downloadText(VERSION_URL).trim().toIntOrNull() ?: return
        if (latest <= BuildConfig.VERSION_CODE) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // Permission not granted yet; nothing we can do silently.
            return
        }

        val apkBytes = downloadBytes(APK_URL)
        if (apkBytes.isEmpty()) return
        installApk(context, apkBytes)
    }

    private fun installApk(context: Context, apk: ByteArray) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(
                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
            )
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("app.apk", 0, apk.size.toLong()).use { out ->
                out.write(apk)
                session.fsync(out)
            }
            val statusIntent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    private fun openConnection(spec: String): HttpURLConnection {
        var url = URL(spec)
        var redirects = 0
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", UA)
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                url = URL(url, location)
                redirects++
                continue
            }
            return conn
        }
    }

    private fun downloadText(spec: String): String {
        val conn = openConnection(spec)
        return try {
            if (conn.responseCode != 200) "" else
                conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadBytes(spec: String): ByteArray {
        val conn = openConnection(spec)
        return try {
            if (conn.responseCode != 200) ByteArray(0) else
                conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
