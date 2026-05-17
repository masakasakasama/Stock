package com.example.stockwidget

data class StockPreset(
    val symbol: String,
    val label: String,
    val recommendedDefault: Boolean = false
)

/** Curated list shown as checkboxes in the configuration screen. */
object StockCatalog {

    val groups: List<Pair<String, List<StockPreset>>> = listOf(
        "為替" to listOf(
            StockPreset("USDJPY=X", "米ドル / 円 (USD/JPY)", recommendedDefault = true),
            StockPreset("EURJPY=X", "ユーロ / 円 (EUR/JPY)", recommendedDefault = true),
            StockPreset("GBPJPY=X", "英ポンド / 円 (GBP/JPY)"),
            StockPreset("EURUSD=X", "ユーロ / 米ドル (EUR/USD)")
        ),
        "株価指数" to listOf(
            StockPreset("^N225", "日経平均株価", recommendedDefault = true),
            StockPreset("^GSPC", "S&P 500", recommendedDefault = true),
            StockPreset("^IXIC", "NASDAQ 総合指数"),
            StockPreset("^DJI", "NY ダウ"),
            StockPreset("^VIX", "VIX 恐怖指数")
        ),
        "暗号資産" to listOf(
            StockPreset("BTC-JPY", "ビットコイン / 円"),
            StockPreset("BTC-USD", "ビットコイン / 米ドル"),
            StockPreset("ETH-USD", "イーサリアム / 米ドル")
        ),
        "コモディティ" to listOf(
            StockPreset("GC=F", "金 (Gold)"),
            StockPreset("CL=F", "原油 WTI")
        ),
        "米国の主要株" to listOf(
            StockPreset("AAPL", "Apple"),
            StockPreset("MSFT", "Microsoft"),
            StockPreset("NVDA", "NVIDIA"),
            StockPreset("TSLA", "Tesla"),
            StockPreset("AMZN", "Amazon"),
            StockPreset("GOOGL", "Alphabet (Google)")
        ),
        "日本の主要株" to listOf(
            StockPreset("7203.T", "トヨタ自動車"),
            StockPreset("6758.T", "ソニーグループ"),
            StockPreset("7974.T", "任天堂"),
            StockPreset("9984.T", "ソフトバンクグループ"),
            StockPreset("8306.T", "三菱UFJ")
        )
    )

    val all: List<StockPreset> get() = groups.flatMap { it.second }

    val defaults: List<String> get() = all.filter { it.recommendedDefault }.map { it.symbol }
}
