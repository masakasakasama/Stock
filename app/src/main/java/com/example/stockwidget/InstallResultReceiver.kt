package com.example.stockwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Tell the UI so a foreground screen can show the install
                // prompt immediately.
                context.sendBroadcast(
                    Intent(AppUpdater.ACTION_STATUS)
                        .setPackage(context.packageName)
                        .putExtra(AppUpdater.EXTRA_STATE, AppUpdater.STATE_CONFIRM)
                        .putExtra(AppUpdater.EXTRA_MESSAGE, "インストールを確認してください")
                        .putExtra(AppUpdater.EXTRA_CONFIRM, confirm)
                )
                notify(
                    context,
                    context.getString(R.string.update_ready_title),
                    context.getString(R.string.update_ready_body),
                    confirm
                )
            }

            PackageInstaller.STATUS_SUCCESS ->
                AppUpdater.sendStatus(context, AppUpdater.STATE_SUCCESS, "更新が完了しました")

            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppUpdater.sendStatus(
                    context, AppUpdater.STATE_ERROR,
                    "インストール失敗: ${msg ?: "不明なエラー"}"
                )
            }
        }
    }

    private fun notify(context: Context, title: String, body: String, action: Intent) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channelId = "updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            action,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        mgr.notify(1001, notification)
    }
}
