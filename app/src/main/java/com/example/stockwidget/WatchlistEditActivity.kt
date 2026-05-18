package com.example.stockwidget

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityWatchlistEditBinding

/** Edits the in-app watchlist (shared across the app, independent of widgets). */
class WatchlistEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchlistEditBinding
    private lateinit var picker: SymbolPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchlistEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = StockPrefs.loadAppSymbols(this)

        picker = SymbolPicker(this, binding.presetsContainer)
        picker.populate(current.toSet())

        val knownSymbols = StockCatalog.all.map { it.symbol }.toSet()
        val custom = current.filter { it !in knownSymbols }
        binding.symbolsInput.setText(custom.joinToString(", "))

        binding.saveButton.setOnClickListener { save() }
        binding.searchButton.setOnClickListener { runSearch() }
    }

    private fun runSearch() {
        val q = binding.searchInput.text?.toString()?.trim().orEmpty()
        binding.searchResults.removeAllViews()
        if (q.isEmpty()) return
        val loading = TextView(this)
        loading.text = getString(R.string.searching)
        loading.setTextColor(0xFF99A3AF.toInt())
        binding.searchResults.addView(loading)

        Thread {
            val hits = YahooFinanceClient.search(q)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.searchResults.removeAllViews()
                if (hits.isEmpty()) {
                    val tv = TextView(this)
                    tv.text = getString(R.string.search_none)
                    tv.setTextColor(0xFF99A3AF.toInt())
                    binding.searchResults.addView(tv)
                    return@runOnUiThread
                }
                for (h in hits) binding.searchResults.addView(resultRow(h))
            }
        }.start()
    }

    private fun resultRow(h: YahooFinanceClient.SearchHit): View {
        val row = TextView(this)
        row.text = "＋ ${h.symbol}  ${h.name}" +
            if (h.exchange.isNotBlank()) "  (${h.exchange})" else ""
        row.setTextColor(0xFFFFFFFF.toInt())
        row.setBackgroundColor(0xFF1E2530.toInt())
        val pad = (12 * resources.displayMetrics.density).toInt()
        row.setPadding(pad, pad, pad, pad)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
        row.layoutParams = lp
        row.setOnClickListener { addSymbol(h.symbol) }
        return row
    }

    private fun addSymbol(symbol: String) {
        val cur = binding.symbolsInput.text?.toString().orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .toMutableList()
        if (symbol !in cur) cur.add(symbol)
        binding.symbolsInput.setText(cur.joinToString(", "))
        android.widget.Toast.makeText(
            this, getString(R.string.search_added, symbol),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun save() {
        val fromCustom = binding.symbolsInput.text?.toString().orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val merged = LinkedHashSet<String>().apply {
            addAll(picker.checkedSymbols())
            addAll(fromCustom)
        }

        StockPrefs.saveSymbols(this, StockPrefs.APP_ID, merged.joinToString(", "))
        finish()
    }
}
