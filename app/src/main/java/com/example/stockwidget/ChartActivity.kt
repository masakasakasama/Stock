package com.example.stockwidget

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityChartBinding
import com.google.android.material.button.MaterialButton

class ChartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChartBinding
    private lateinit var symbol: String
    private var selected = YahooFinanceClient.ChartRange.MONTH
    private val buttons = mutableMapOf<YahooFinanceClient.ChartRange, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        symbol = intent.getStringExtra(EXTRA_SYMBOL).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        binding.chartSymbol.text = symbol
        binding.chartName.text = name

        buildRangeButtons()
        loadQuoteHeader()
        loadSeries()
    }

    private fun buildRangeButtons() {
        val density = resources.displayMetrics.density
        for (range in YahooFinanceClient.ChartRange.values()) {
            val btn = MaterialButton(
                this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = range.label
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.rightMargin = (8 * density).toInt()
                layoutParams = lp
                setOnClickListener {
                    selected = range
                    highlightSelected()
                    loadSeries()
                }
            }
            buttons[range] = btn
            binding.rangeButtons.addView(btn)
        }
        highlightSelected()
    }

    private fun highlightSelected() {
        for ((range, btn) in buttons) {
            btn.alpha = if (range == selected) 1f else 0.5f
        }
    }

    private fun loadQuoteHeader() {
        Thread {
            val q = YahooFinanceClient.fetch(symbol)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (q.error == null) {
                    binding.chartPrice.text = q.formattedPrice()
                    binding.chartChange.text = q.formattedChange()
                    binding.chartChange.setTextColor(
                        if (q.isUp) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
                    )
                }
            }
        }.start()
    }

    private fun loadSeries() {
        binding.chartStatus.visibility = View.VISIBLE
        binding.chartStatus.text = getString(R.string.loading_label)
        binding.chartView.setValues(emptyList())
        val range = selected
        Thread {
            val series = YahooFinanceClient.fetchSeries(symbol, range)
            runOnUiThread {
                if (isFinishing || isDestroyed || range != selected) return@runOnUiThread
                if (series.closes.size >= 2) {
                    binding.chartStatus.visibility = View.GONE
                    binding.chartView.setValues(series.closes)
                } else {
                    binding.chartStatus.visibility = View.VISIBLE
                    binding.chartStatus.text = getString(
                        R.string.error_label,
                        series.error ?: "no data"
                    )
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_SYMBOL = "symbol"
        const val EXTRA_NAME = "name"
    }
}
