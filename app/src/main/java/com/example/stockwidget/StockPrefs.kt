package com.example.stockwidget

import android.content.Context

/** Per-widget storage of the comma-separated ticker list. */
object StockPrefs {

    private const val PREFS = "stock_widget_prefs"
    private const val KEY_PREFIX = "symbols_"

    fun saveSymbols(context: Context, appWidgetId: Int, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + appWidgetId, raw)
            .apply()
    }

    fun loadRaw(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + appWidgetId, "") ?: ""

    fun loadSymbols(context: Context, appWidgetId: Int): List<String> =
        loadRaw(context, appWidgetId)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + appWidgetId)
            .apply()
    }
}
