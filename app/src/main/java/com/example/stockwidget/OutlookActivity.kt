package com.example.stockwidget

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityOutlookBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutlookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOutlookBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutlookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apiKeyInput.setText(StockPrefs.loadApiKey(this))

        val (cached, date) = StockPrefs.loadOutlook(this)
        if (cached.isNotBlank()) {
            binding.outlookText.text = cached
            binding.outlookDate.text = getString(R.string.outlook_generated_at, date)
        }

        binding.saveKeyButton.setOnClickListener {
            StockPrefs.saveApiKey(this, binding.apiKeyInput.text?.toString().orEmpty())
            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        }

        binding.generateButton.setOnClickListener { generate() }
    }

    private fun generate() {
        val key = binding.apiKeyInput.text?.toString().orEmpty().trim()
        if (key.isBlank()) {
            Toast.makeText(this, R.string.api_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        StockPrefs.saveApiKey(this, key)

        binding.outlookProgress.visibility = View.VISIBLE
        binding.generateButton.isEnabled = false
        binding.outlookText.text = ""
        binding.outlookDate.text = getString(R.string.outlook_generating)

        Thread {
            val symbols = StockPrefs.loadAppSymbols(this)
            val quotes = symbols.map { YahooFinanceClient.fetch(it) }
            val today = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date())
            val context = buildString {
                append("日付: ").append(today).append('\n')
                append("ウォッチ銘柄の現状:\n")
                for (q in quotes) {
                    if (q.error != null) continue
                    append("- ").append(q.symbol)
                        .append(" (").append(q.shortName).append("): ")
                        .append(q.formattedPrice())
                        .append(" ").append(q.formattedChange()).append('\n')
                }
                append("\n上記を踏まえ、世の中の流れと相場の見通しを指定の形式で日本語で書いてください。")
            }

            val result = GeminiClient.generateOutlook(key, context)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.outlookProgress.visibility = View.GONE
                binding.generateButton.isEnabled = true
                when (result) {
                    is GeminiClient.Result.Ok -> {
                        binding.outlookText.text = result.text
                        binding.outlookDate.text =
                            getString(R.string.outlook_generated_at, today)
                        StockPrefs.saveOutlook(this, result.text, today)
                    }
                    is GeminiClient.Result.Err -> {
                        binding.outlookDate.text = ""
                        binding.outlookText.text =
                            getString(R.string.outlook_error, result.message)
                    }
                }
            }
        }.start()
    }
}
