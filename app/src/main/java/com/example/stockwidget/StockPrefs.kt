package com.example.stockwidget

import android.content.Context

/** Per-widget storage of the comma-separated ticker list. */
object StockPrefs {

    private const val PREFS = "stock_widget_prefs"
    private const val KEY_PREFIX = "symbols_"

    /** Pseudo widget id used for the in-app watchlist (real widget ids are > 0). */
    const val APP_ID = 0

    /** App watchlist, falling back to the recommended defaults when unset. */
    fun loadAppSymbols(context: Context): List<String> {
        val saved = loadSymbols(context, APP_ID)
        return saved.ifEmpty { StockCatalog.defaults }
    }

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

    private const val KEY_API = "anthropic_api_key"
    private const val KEY_OUTLOOK = "outlook_text"
    private const val KEY_OUTLOOK_DATE = "outlook_date"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveApiKey(context: Context, key: String) =
        prefs(context).edit().putString(KEY_API, key.trim()).apply()

    fun loadApiKey(context: Context): String =
        prefs(context).getString(KEY_API, "") ?: ""

    fun saveOutlook(context: Context, text: String, date: String) =
        prefs(context).edit()
            .putString(KEY_OUTLOOK, text)
            .putString(KEY_OUTLOOK_DATE, date)
            .apply()

    fun loadOutlook(context: Context): Pair<String, String> =
        (prefs(context).getString(KEY_OUTLOOK, "") ?: "") to
            (prefs(context).getString(KEY_OUTLOOK_DATE, "") ?: "")

    fun putString(context: Context, key: String, value: String) =
        prefs(context).edit().putString(key, value).apply()

    fun getString(context: Context, key: String): String =
        prefs(context).getString(key, "") ?: ""
}
