package com.example.stockwidget

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwidget.databinding.ActivityOutlookBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutlookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOutlookBinding

    private val timeframeOrder = listOf("日", "週", "月", "半年", "1年", "3年")
    private val sectionTitles =
        listOf("総合", "日", "週", "月", "半年", "1年", "3年", "主な注目材料")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutlookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apiKeyInput.setText(StockPrefs.loadApiKey(this))

        val (cached, date) = StockPrefs.loadOutlook(this)
        if (cached.isNotBlank()) {
            binding.outlookDate.text = getString(R.string.outlook_generated_at, date)
            renderOutlook(cached)
        }

        binding.saveKeyButton.setOnClickListener {
            StockPrefs.saveApiKey(this, binding.apiKeyInput.text?.toString().orEmpty())
            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        }
        binding.generateButton.setOnClickListener { generate() }

        // D: auto-generate once per day when a key is set.
        val today = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date())
        val cachedDay = date.take(10)
        if (StockPrefs.loadApiKey(this).isNotBlank() && cachedDay != today) {
            binding.root.post { generate() }
        }
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
        binding.outlookContainer.removeAllViews()
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
                        binding.outlookDate.text =
                            getString(R.string.outlook_generated_at, today)
                        renderOutlook(result.text)
                        StockPrefs.saveOutlook(this, result.text, today)
                    }
                    is GeminiClient.Result.Err -> {
                        binding.outlookDate.text = ""
                        binding.outlookContainer.removeAllViews()
                        binding.outlookContainer.addView(
                            plainText(getString(R.string.outlook_error, result.message))
                        )
                    }
                }
            }
        }.start()
    }

    private fun renderOutlook(raw: String) {
        val container = binding.outlookContainer
        container.removeAllViews()

        val sections = parseSections(raw)
        if (sections.isEmpty()) {
            container.addView(plainText(raw))
            return
        }
        for ((title, body) in sections) {
            when {
                title == "総合" -> container.addView(infoCard("総合", body))
                title in timeframeOrder -> container.addView(timeframeCard(title, body))
                else -> container.addView(infoCard(title, body))
            }
        }
        // Disclaimer line (starts with ※) if present anywhere.
        val disc = raw.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("※") }
        if (disc != null) container.addView(disclaimer(disc))
    }

    /** Splits the text on the known 【...】 headings, preserving order. */
    private fun parseSections(raw: String): List<Pair<String, String>> {
        val marks = ArrayList<Triple<String, Int, Int>>() // title, start, headerEnd
        for (t in sectionTitles) {
            val token = "【$t】"
            val idx = raw.indexOf(token)
            if (idx >= 0) marks.add(Triple(t, idx, idx + token.length))
        }
        if (marks.isEmpty()) return emptyList()
        marks.sortBy { it.second }
        val result = ArrayList<Pair<String, String>>()
        for (i in marks.indices) {
            val (title, _, hEnd) = marks[i]
            val end = if (i + 1 < marks.size) marks[i + 1].second else raw.length
            var body = raw.substring(hEnd, end).trim()
            val sIdx = body.indexOf("※")
            if (sIdx >= 0) body = body.substring(0, sIdx).trim()
            result.add(title to body)
        }
        return result
    }

    private data class Verdict(val label: String, val arrow: String, val color: Int)

    private fun verdictOf(body: String): Pair<Verdict?, String> {
        val firstToken = body.trimStart().takeWhile { !it.isWhitespace() }
        val v = when {
            firstToken.contains("強気") -> Verdict("強気", "▲", StockQuote.COLOR_UP)
            firstToken.contains("弱気") -> Verdict("弱気", "▼", StockQuote.COLOR_DOWN)
            firstToken.contains("中立") || firstToken.contains("横ばい") ->
                Verdict("中立", "■", 0xFF9AA4B0.toInt())
            else -> null
        }
        val rest = if (v != null)
            body.trimStart().removePrefix(firstToken).trim() else body
        return v to rest
    }

    private fun timeframeCard(scope: String, body: String): View {
        val (v, detail) = verdictOf(body)
        val color = v?.color ?: Color.WHITE
        val card = card()

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val arrowView = makeText(v?.arrow ?: "・", 22f, color, bold = true)
        arrowView.setPadding(0, 0, dp(10), 0)
        header.addView(arrowView)
        header.addView(makeText(scope, 17f, Color.WHITE, bold = true))
        if (v != null) {
            header.addView(makeText("  ${v.label}", 15f, color, bold = true))
        }
        card.addView(header)

        val bodyView = makeText(detail, 14f, 0xFFE8EBEF.toInt())
        bodyView.setPadding(0, dp(6), 0, 0)
        card.addView(bodyView)
        return card
    }

    private fun infoCard(title: String, body: String): View {
        val card = card()
        card.addView(makeText(title, 15f, 0xFF1BA672.toInt(), bold = true))
        val bodyView = makeText(body, 14f, 0xFFE8EBEF.toInt())
        bodyView.setPadding(0, dp(6), 0, 0)
        card.addView(bodyView)
        return card
    }

    private fun makeText(
        content: String,
        sizeSp: Float,
        colorInt: Int,
        bold: Boolean = false
    ): TextView {
        val tv = TextView(this)
        tv.text = content
        tv.setTextColor(colorInt)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        tv.setLineSpacing(0f, 1.2f)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        return tv
    }

    private fun card(): LinearLayout {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setBackgroundColor(0xFF1E2530.toInt())
        ll.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(10)
        ll.layoutParams = lp
        return ll
    }

    private fun disclaimer(content: String): View {
        val tv = makeText(content, 12f, 0xFF99A3AF.toInt())
        tv.setPadding(0, dp(4), 0, dp(8))
        return tv
    }

    private fun plainText(content: String): View {
        val tv = makeText(content, 14f, Color.WHITE)
        tv.setTextIsSelectable(true)
        return tv
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
