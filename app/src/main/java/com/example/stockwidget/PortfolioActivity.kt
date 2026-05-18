package com.example.stockwidget

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityPortfolioBinding
import java.text.NumberFormat
import java.util.Locale

class PortfolioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPortfolioBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pfAdd.setOnClickListener { add() }
        refresh()
    }

    private fun add() {
        val symbol = binding.pfSymbol.text?.toString()?.trim().orEmpty()
        val shares = binding.pfShares.text?.toString()?.trim()?.toDoubleOrNull()
        val cost = binding.pfCost.text?.toString()?.trim()?.toDoubleOrNull()
        if (symbol.isEmpty() || shares == null || cost == null) {
            Toast.makeText(this, R.string.pf_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        HoldingStore.add(this, Holding(symbol, shares, cost))
        binding.pfSymbol.setText("")
        binding.pfShares.setText("")
        binding.pfCost.setText("")
        refresh()
    }

    private fun refresh() {
        val holdings = HoldingStore.load(this)
        binding.pfList.removeAllViews()
        binding.pfTotal.text = ""
        if (holdings.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.pf_empty)
            tv.setTextColor(0xFF99A3AF.toInt())
            binding.pfList.addView(tv)
            return
        }
        binding.pfProgress.visibility = View.VISIBLE
        Thread {
            val quotes = holdings.map { it to YahooFinanceClient.fetch(it.symbol) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.pfProgress.visibility = View.GONE
                render(quotes)
            }
        }.start()
    }

    private fun render(data: List<Pair<Holding, StockQuote>>) {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
        var totalValue = 0.0
        var totalCost = 0.0
        binding.pfList.removeAllViews()

        data.forEachIndexed { index, (h, q) ->
            val ok = q.error == null
            val value = q.price * h.shares
            val cost = h.avgCost * h.shares
            val pl = value - cost
            val plPct = if (cost != 0.0) pl / cost * 100.0 else 0.0
            // Skip failed quotes from totals so a single fetch error
            // doesn't make the aggregate P/L look catastrophically wrong.
            if (ok) {
                totalValue += value
                totalCost += cost
            }

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setBackgroundColor(0xFF1E2530.toInt())
            card.setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            card.layoutParams = lp

            val top = LinearLayout(this)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            val name = TextView(this)
            name.text = "${h.symbol}  ×${trim(h.shares)} @ ${trim(h.avgCost)}"
            name.setTextColor(0xFFFFFFFF.toInt())
            name.layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            top.addView(name)
            val del = TextView(this)
            del.text = "削除"
            del.setTextColor(0xFFE0533D.toInt())
            del.setOnClickListener {
                HoldingStore.removeAt(this, index)
                refresh()
            }
            top.addView(del)
            card.addView(top)

            val detail = TextView(this)
            if (!ok) {
                detail.text = "取得失敗（合計には含めません）"
                detail.setTextColor(0xFF99A3AF.toInt())
            } else {
                val sign = if (pl >= 0) "+" else ""
                detail.text = String.format(
                    Locale.US,
                    "評価額 %s　損益 %s%s (%s%.2f%%)",
                    nf.format(value), sign, nf.format(pl), sign, plPct
                )
                detail.setTextColor(
                    if (pl >= 0) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
                )
            }
            detail.setPadding(0, dp(6), 0, 0)
            card.addView(detail)

            binding.pfList.addView(card)
        }

        val tpl = totalValue - totalCost
        val tpct = if (totalCost != 0.0) tpl / totalCost * 100.0 else 0.0
        val s = if (tpl >= 0) "+" else ""
        binding.pfTotal.text = String.format(
            Locale.US,
            "合計 評価額 %s　損益 %s%s (%s%.2f%%)",
            nf.format(totalValue), s, nf.format(tpl), s, tpct
        )
        binding.pfTotal.setTextColor(
            if (tpl >= 0) StockQuote.COLOR_UP else StockQuote.COLOR_DOWN
        )
    }

    private fun trim(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
