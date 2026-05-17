package com.example.stockwidget

import java.text.NumberFormat
import java.util.Locale

data class StockQuote(
    val symbol: String,
    val shortName: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val error: String? = null
) {
    val change: Double get() = price - previousClose

    val changePercent: Double
        get() = if (previousClose != 0.0) change / previousClose * 100.0 else 0.0

    val isUp: Boolean get() = change >= 0.0

    fun formattedPrice(): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
        val unit = when (currency.uppercase(Locale.US)) {
            "USD" -> "$"
            "JPY" -> "¥"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> if (currency.isBlank()) "" else "$currency "
        }
        return unit + nf.format(price)
    }

    fun formattedChange(): String {
        val sign = if (isUp) "+" else ""
        return String.format(
            Locale.US,
            "%s%.2f (%s%.2f%%)",
            sign, change, sign, changePercent
        )
    }

    companion object {
        const val COLOR_UP = 0xFF1BA672.toInt()
        const val COLOR_DOWN = 0xFFE0533D.toInt()

        fun failed(symbol: String, message: String) =
            StockQuote(symbol, symbol, 0.0, 0.0, "", message)
    }
}
