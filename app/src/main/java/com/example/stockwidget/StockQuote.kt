package com.example.stockwidget

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

    companion object {
        fun failed(symbol: String, message: String) =
            StockQuote(symbol, symbol, 0.0, 0.0, "", message)
    }
}
