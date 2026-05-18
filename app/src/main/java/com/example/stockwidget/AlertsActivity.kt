package com.example.stockwidget

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityAlertsBinding

class AlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.alertAbove.setOnClickListener { add("ABOVE") }
        binding.alertBelow.setOnClickListener { add("BELOW") }
        binding.alertPct.setOnClickListener { add("PCT") }

        AlertScheduler.schedule(this)
        renderList()
    }

    private fun add(kind: String) {
        val symbol = binding.alertSymbol.text?.toString()?.trim().orEmpty()
        val value = binding.alertValue.text?.toString()?.trim()?.toDoubleOrNull()
        if (symbol.isEmpty() || value == null) {
            Toast.makeText(this, R.string.alerts_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        AlertStore.add(this, Alert(symbol, kind, value))
        binding.alertSymbol.setText("")
        binding.alertValue.setText("")
        renderList()
    }

    private fun renderList() {
        val container = binding.alertsList
        container.removeAllViews()
        val alerts = AlertStore.load(this)
        if (alerts.isEmpty()) {
            val tv = TextView(this)
            tv.text = getString(R.string.alerts_empty)
            tv.setTextColor(0xFF99A3AF.toInt())
            container.addView(tv)
            return
        }
        alerts.forEachIndexed { index, a ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setBackgroundColor(0xFF1E2530.toInt())
            row.setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            row.layoutParams = lp

            val label = TextView(this)
            label.text = a.describe()
            label.setTextColor(0xFFFFFFFF.toInt())
            label.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            row.addView(label)

            val del = TextView(this)
            del.text = "削除"
            del.setTextColor(0xFFE0533D.toInt())
            del.setOnClickListener {
                AlertStore.removeAt(this, index)
                renderList()
            }
            row.addView(del)

            container.addView(row)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
