package com.example.stockwidget

import android.os.Bundle
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
