package com.example.stockwidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlin.math.abs

class AlertWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val alerts = AlertStore.load(ctx).filter { it.enabled }
            if (alerts.isEmpty()) return Result.success()

            val symbols = alerts.map { it.symbol }.distinct()
            val quotes = symbols.associateWith { YahooFinanceClient.fetch(it) }

            var id = 2000
            for (a in alerts) {
                val q = quotes[a.symbol] ?: continue
                if (q.error != null) continue
                val hit = when (a.kind) {
                    "ABOVE" -> q.price >= a.value
                    "BELOW" -> q.price <= a.value
                    else -> abs(q.changePercent) >= a.value
                }
                if (hit) {
                    notify(
                        ctx, id++,
                        "${a.symbol} アラート",
                        "${q.formattedPrice()} (${q.formattedChange()}) — ${a.describe()}"
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            CrashLog.append(applicationContext, "AlertWorker", e)
            Result.success()
        }
    }

    private fun notify(ctx: Context, id: Int, title: String, body: String) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        val ch = "alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(ch, "価格アラート", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val n = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        mgr.notify(id, n)
    }
}
