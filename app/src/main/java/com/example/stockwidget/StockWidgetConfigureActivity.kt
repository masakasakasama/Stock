package com.example.stockwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityStockWidgetConfigureBinding

class StockWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockWidgetConfigureBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out, leave the widget uninstalled.
        setResult(Activity.RESULT_CANCELED)

        binding = ActivityStockWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.symbolsInput.setText(StockPrefs.loadRaw(this, appWidgetId))
        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        val raw = binding.symbolsInput.text?.toString().orEmpty().trim()
        StockPrefs.saveSymbols(this, appWidgetId, raw)

        val mgr = AppWidgetManager.getInstance(this)
        StockWidgetProvider.updateWidget(this, mgr, appWidgetId)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
