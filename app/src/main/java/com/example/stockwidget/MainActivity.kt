package com.example.stockwidget

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = getString(
            R.string.version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )

        binding.refreshButton.setOnClickListener { loadQuotes() }
        binding.editButton.setOnClickListener {
            startActivity(Intent(this, WatchlistEditActivity::class.java))
        }
        binding.autoupdateButton.setOnClickListener { openInstallPermission() }
        binding.checkUpdateButton.setOnClickListener {
            AppUpdater.checkNow(this)
            Toast.makeText(this, R.string.checking_update, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.autoupdateStatus.setText(
            if (canSelfInstall()) R.string.autoupdate_enabled
            else R.string.autoupdate_disabled
        )
        loadQuotes()
    }

    private fun loadQuotes() {
        val symbols = StockPrefs.loadAppSymbols(this)
        binding.progress.visibility = View.VISIBLE
        binding.quotesContainer.removeAllViews()

        Thread {
            val quotes = symbols.map { YahooFinanceClient.fetch(it) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.progress.visibility = View.GONE
                renderQuotes(quotes)
                binding.updatedAt.text = getString(
                    R.string.updated_at,
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                )
            }
        }.start()
    }

    private fun renderQuotes(quotes: List<StockQuote>) {
        val inflater = LayoutInflater.from(this)
        val container = binding.quotesContainer
        if (quotes.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.empty_hint)
                setTextColor(0xCCFFFFFF.toInt())
            }
            container.addView(empty)
            return
        }
        for (q in quotes) {
            val row = inflater.inflate(R.layout.quote_row, container, false) as LinearLayout
            row.findViewById<TextView>(R.id.row_symbol).text = q.symbol
            val priceView = row.findViewById<TextView>(R.id.row_price)
            val changeView = row.findViewById<TextView>(R.id.row_change)
            if (q.error != null) {
                row.findViewById<TextView>(R.id.row_name).text =
                    getString(R.string.error_label, q.error)
                priceView.text = "--"
                changeView.text = ""
            } else {
                row.findViewById<TextView>(R.id.row_name).text = q.shortName
                priceView.text = q.formattedPrice()
                changeView.text = q.formattedChange()
                changeView.setTextColor(
                    if (q.isUp) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
                )
            }
            container.addView(row)
        }
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
}
