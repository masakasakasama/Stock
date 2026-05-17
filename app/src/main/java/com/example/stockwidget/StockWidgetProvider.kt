package com.example.stockwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

class StockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        AppUpdater.maybeCheck(context)
        refresh(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, StockWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) refresh(context, mgr, ids)
        }
    }


    private fun refresh(
        context: Context,
        mgr: AppWidgetManager,
        ids: IntArray
    ) {
        // Show a valid layout immediately so the launcher can place the widget.
        for (id in ids) {
            val rv = baseViews(context)
            rv.removeAllViews(R.id.widget_rows)
            rv.setViewVisibility(R.id.widget_status, View.VISIBLE)
            rv.setTextViewText(R.id.widget_status, context.getString(R.string.loading_label))
            mgr.updateAppWidget(id, rv)
        }

        val pending = goAsync()
        val appCtx = context.applicationContext
        Thread {
            try {
                val symbols = StockPrefs.loadAppSymbols(appCtx)
                for (id in ids) {
                    val quotes = symbols.map { YahooFinanceClient.fetch(it) }
                    val rv = baseViews(appCtx)
                    rv.removeAllViews(R.id.widget_rows)
                    if (quotes.isEmpty()) {
                        rv.setViewVisibility(R.id.widget_status, View.VISIBLE)
                        rv.setTextViewText(
                            R.id.widget_status,
                            appCtx.getString(R.string.empty_hint)
                        )
                    } else {
                        rv.setViewVisibility(R.id.widget_status, View.GONE)
                        for (q in quotes) rv.addView(R.id.widget_rows, rowViews(appCtx, q))
                    }
                    mgr.updateAppWidget(id, rv)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun baseViews(context: Context): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.stock_widget)
        val refreshIntent = Intent(context, StockWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPending = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.widget_title, openApp)
        return rv
    }

    private fun rowViews(context: Context, q: StockQuote): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.stock_widget_item)
        row.setTextViewText(R.id.item_symbol, q.symbol)
        if (q.error != null) {
            row.setTextViewText(R.id.item_name, context.getString(R.string.error_label, q.error))
            row.setTextViewText(R.id.item_price, "--")
            row.setTextViewText(R.id.item_change, "")
            row.setTextColor(R.id.item_change, Color.GRAY)
        } else {
            row.setTextViewText(R.id.item_name, q.shortName)
            row.setTextViewText(R.id.item_price, q.formattedPrice())
            row.setTextViewText(R.id.item_change, q.formattedChange())
            row.setTextColor(
                R.id.item_change,
                if (q.isUp) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
            )
        }
        return row
    }

    companion object {
        const val ACTION_REFRESH = "com.example.stockwidget.ACTION_REFRESH"
    }
}
