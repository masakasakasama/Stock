package com.example.stockwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from the public GitHub release. Every step broadcasts an
 * [ACTION_STATUS] intent so the UI can show exactly what is happening
 * instead of failing silently.
 */
object AppUpdater {

    private const val VERSION_URL =
        "https://github.com/masakasakasama/Stock/releases/download/debug-latest/latest-version.txt"
    private const val APK_URL =
        "https://github.com/masakasakasama/Stock/releases/download/debug-latest/stock-widget-debug.apk"

    private const val PREFS = "stock_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val MIN_INTERVAL_MS = 30L * 60 * 1000 // 30 min

    private const val UA = "StockWidget-Updater"

    const val ACTION_STATUS = "com.example.stockwidget.UPDATE_STATUS"
    const val EXTRA_STATE = "state"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_CONFIRM = "confirm"

    const val STATE_CHECKING = "checking"
    const val STATE_UPTODATE = "uptodate"
    const val STATE_DOWNLOADING = "downloading"
    const val STATE_INSTALLING = "installing"
    const val STATE_NEED_PERMISSION = "need_permission"
    const val STATE_CONFIRM = "confirm"
    const val STATE_SUCCESS = "success"
    const val STATE_ERROR = "error"

    fun sendStatus(context: Context, state: String, message: String = "") {
        val i = Intent(ACTION_STATUS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_STATE, state)
            .putExtra(EXTRA_MESSAGE, message)
        context.sendBroadcast(i)
    }

    /** Throttled check used by background triggers. */
    fun maybeCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < MIN_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        checkNow(context)
    }

    /** Immediate check with visible status broadcasts. */
    fun checkNow(context: Context) {
        val appCtx = context.applicationContext
        Thread {
            try {
                check(appCtx)
            } catch (e: Exception) {
                sendStatus(appCtx, STATE_ERROR, e.message ?: "error")
            }
        }.start()
    }

    private fun check(context: Context) {
        sendStatus(context, STATE_CHECKING)
        val text = downloadText(VERSION_URL).trim()
        val latest = text.toIntOrNull()
        if (latest == null) {
            sendStatus(context, STATE_ERROR, "バージョン情報を取得できません")
            return
        }
        if (latest <= BuildConfig.VERSION_CODE) {
            sendStatus(context, STATE_UPTODATE, "最新です (ビルド ${BuildConfig.VERSION_CODE})")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            sendStatus(
                context, STATE_NEED_PERMISSION,
                "新版 (ビルド $latest) があります。インストール許可が必要です"
            )
            return
        }

        sendStatus(context, STATE_DOWNLOADING, "新版 (ビルド $latest) をダウンロード中…")
        val apkBytes = downloadBytes(APK_URL)
        if (apkBytes.isEmpty()) {
            sendStatus(context, STATE_ERROR, "ダウンロードに失敗しました")
            return
        }
        sendStatus(context, STATE_INSTALLING, "インストール中…")
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
