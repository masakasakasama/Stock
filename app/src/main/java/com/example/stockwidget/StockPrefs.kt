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

    fun saveApiKey(context: Context, key: String) {
        StockSecrets.save(context, key.trim())
        check(prefs(context).edit().remove(KEY_API).commit())
    }

    fun loadApiKey(context: Context): String {
        val legacy = prefs(context).getString(KEY_API, "").orEmpty()
        if (legacy.isNotBlank()) {
            // Remove old plaintext ONLY after the encrypted save and readback succeed.
            return runCatching {
                StockSecrets.save(context, legacy)
                check(StockSecrets.load(context) == legacy)
                check(prefs(context).edit().remove(KEY_API).commit())
                legacy
            }.getOrDefault(legacy)
        }
        return runCatching { StockSecrets.load(context) }.getOrDefault("")
    }

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
