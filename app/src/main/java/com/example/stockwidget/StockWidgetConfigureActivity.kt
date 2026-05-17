package com.example.stockwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.Toast
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityStockWidgetConfigureBinding

class StockWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockWidgetConfigureBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val checkBoxes = mutableMapOf<String, CheckBox>()

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

        buildCheckboxes(selected.toSet())

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
        refreshUpdateStatus()
    }

    private fun canSelfInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            packageManager.canRequestPackageInstalls()

    private fun refreshUpdateStatus() {
        binding.autoupdateStatus.setText(
            if (canSelfInstall()) R.string.autoupdate_enabled
            else R.string.autoupdate_disabled
        )
    }

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

    private fun buildCheckboxes(selected: Set<String>) {
        val container = binding.presetsContainer
        for ((groupName, presets) in StockCatalog.groups) {
            container.addView(makeHeader(groupName))
            for (preset in presets) {
                val cb = CheckBox(this).apply {
                    text = "${preset.label}  (${preset.symbol})"
                    isChecked = preset.symbol in selected
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                checkBoxes[preset.symbol] = cb
                container.addView(cb)
            }
        }
    }

    private fun makeHeader(title: String): TextView = TextView(this).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.START
        setPadding(0, dp(16), 0, dp(4))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun save() {
        val fromCheckboxes = StockCatalog.all
            .map { it.symbol }
            .filter { checkBoxes[it]?.isChecked == true }

        val fromCustom = binding.symbolsInput.text?.toString().orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val merged = LinkedHashSet<String>().apply {
            addAll(fromCheckboxes)
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
