package com.example.stockwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.text.NumberFormat
import java.util.Locale

class StockRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return StockRemoteViewsFactory(applicationContext, appWidgetId)
    }
}

private class StockRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val quotes = mutableListOf<StockQuote>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Runs on a binder thread, so synchronous network access is allowed.
        val symbols = StockPrefs.loadSymbols(context, appWidgetId)
        quotes.clear()
        for (symbol in symbols) {
            quotes.add(YahooFinanceClient.fetch(symbol))
        }
    }

    override fun onDestroy() {
        quotes.clear()
    }

    override fun getCount(): Int = quotes.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.stock_widget_item)
        val q = quotes[position]

        row.setTextViewText(R.id.item_symbol, q.symbol)

        if (q.error != null) {
            row.setTextViewText(R.id.item_name, context.getString(R.string.error_label, q.error))
            row.setTextViewText(R.id.item_price, "--")
            row.setTextViewText(R.id.item_change, "")
            row.setTextColor(R.id.item_change, Color.GRAY)
            return row
        }

        row.setTextViewText(R.id.item_name, q.shortName)
        row.setTextViewText(R.id.item_price, formatPrice(q.price, q.currency))

        val sign = if (q.isUp) "+" else ""
        val changeText = String.format(
            Locale.US,
            "%s%.2f (%s%.2f%%)",
            sign, q.change, sign, q.changePercent
        )
        row.setTextViewText(R.id.item_change, changeText)
        row.setTextColor(
            R.id.item_change,
            if (q.isUp) Color.parseColor("#1BA672") else Color.parseColor("#E0533D")
        )
        return row
    }

    private fun formatPrice(price: Double, currency: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
        val symbol = when (currency.uppercase(Locale.US)) {
            "USD" -> "$"
            "JPY" -> "¥"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> if (currency.isBlank()) "" else "$currency "
        }
        return symbol + nf.format(price)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
