package com.example.stockwidget

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import com.example.stockwidget.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(AppUpdater.EXTRA_STATE) ?: return
            val message = intent.getStringExtra(AppUpdater.EXTRA_MESSAGE).orEmpty()
            binding.updateLog.text = message.ifBlank { state }

            if (state == AppUpdater.STATE_CONFIRM) {
                val confirm: Intent? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(AppUpdater.EXTRA_CONFIRM, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(AppUpdater.EXTRA_CONFIRM)
                    }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(it) }
                }
            }
            if (state == AppUpdater.STATE_SUCCESS) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.update_done, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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
            binding.updateLog.text = getString(R.string.checking_update)
            AppUpdater.checkNow(this)
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        registerStatusReceiver()
        binding.autoupdateStatus.setText(
            if (canSelfInstall()) R.string.autoupdate_enabled
            else R.string.autoupdate_disabled
        )
        loadQuotes()
        // Check for an update every time the app is opened.
        AppUpdater.checkNow(this)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(statusReceiver) }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(AppUpdater.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
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
            row.setOnClickListener {
                startActivity(
                    Intent(this, ChartActivity::class.java)
                        .putExtra(ChartActivity.EXTRA_SYMBOL, q.symbol)
                        .putExtra(ChartActivity.EXTRA_NAME, q.shortName)
                )
            }
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
