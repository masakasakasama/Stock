package com.example.stockwidget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches quotes from Yahoo Finance's public chart endpoint, which works
 * without an API key. One request per symbol keeps parsing simple and avoids
 * the crumb/cookie requirement of the batch quote endpoint.
 */
object YahooFinanceClient {

    private const val BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    fun fetch(symbol: String): StockQuote {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return StockQuote.failed(symbol, "empty")
        return try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("$BASE$encoded?range=1d&interval=1d")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode != 200) {
                    return StockQuote.failed(trimmed, "HTTP ${conn.responseCode}")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(trimmed, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            StockQuote.failed(trimmed, e.message ?: "network error")
        }
    }

    private fun parse(symbol: String, body: String): StockQuote {
        val chart = JSONObject(body).getJSONObject("chart")
        if (!chart.isNull("error")) {
            return StockQuote.failed(symbol, "not found")
        }
        val results = chart.optJSONArray("result")
        if (results == null || results.length() == 0) {
            return StockQuote.failed(symbol, "no data")
        }
        val meta = results.getJSONObject(0).getJSONObject("meta")
        val price = meta.optDouble("regularMarketPrice", Double.NaN)
        if (price.isNaN()) return StockQuote.failed(symbol, "no price")

        val prevClose = when {
            meta.has("previousClose") -> meta.optDouble("previousClose", price)
            meta.has("chartPreviousClose") -> meta.optDouble("chartPreviousClose", price)
            else -> price
        }
        val name = meta.optString("shortName").ifBlank {
            meta.optString("longName").ifBlank { meta.optString("symbol", symbol) }
        }
        val currency = meta.optString("currency", "")
        return StockQuote(
            symbol = symbol,
            shortName = name,
            price = price,
            previousClose = prevClose,
            currency = currency
        )
    }
}
