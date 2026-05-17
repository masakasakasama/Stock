package com.example.stockwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityStockWidgetConfigureBinding

class StockWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockWidgetConfigureBinding
    private lateinit var picker: SymbolPicker
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val saved = StockPrefs.loadSymbols(this, appWidgetId)
        // First-time setup (nothing saved yet) starts from the recommended set.
        val selected = if (saved.isEmpty()) StockCatalog.defaults else saved

        picker = SymbolPicker(this, binding.presetsContainer)
        picker.populate(selected.toSet())

        // Symbols that don't match any preset go into the free-text field.
        val knownSymbols = StockCatalog.all.map { it.symbol }.toSet()
        val custom = selected.filter { it !in knownSymbols }
        binding.symbolsInput.setText(custom.joinToString(", "))

        binding.saveButton.setOnClickListener { save() }

        binding.autoupdateButton.setOnClickListener { openInstallPermission() }
        binding.checkUpdateButton.setOnClickListener {
            AppUpdater.checkNow(this)
            Toast.makeText(this, R.string.check_update_button, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.autoupdateStatus.setText(
            if (canSelfInstall()) R.string.autoupdate_enabled
            else R.string.autoupdate_disabled
        )
    }

    private fun canSelfInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            packageManager.canRequestPackageInstalls()

    private fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
        }
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

        StockPrefs.saveSymbols(this, appWidgetId, merged.joinToString(", "))

        val mgr = AppWidgetManager.getInstance(this)
        StockWidgetProvider.updateWidget(this, mgr, appWidgetId)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
