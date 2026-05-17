package com.example.stockwidget

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Builds the grouped catalog checkboxes into a container and reads back the selection. */
class SymbolPicker(
    private val context: Context,
    private val container: LinearLayout
) {
    private val checkBoxes = LinkedHashMap<String, CheckBox>()

    fun populate(selected: Set<String>) {
        container.removeAllViews()
        checkBoxes.clear()
        for ((groupName, presets) in StockCatalog.groups) {
            container.addView(makeHeader(groupName))
            for (preset in presets) {
                val cb = CheckBox(context).apply {
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

    fun checkedSymbols(): List<String> =
        StockCatalog.all.map { it.symbol }.filter { checkBoxes[it]?.isChecked == true }

    private fun makeHeader(title: String): TextView = TextView(context).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.START
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(0, pad, 0, pad / 4)
        setTypeface(typeface, Typeface.BOLD)
    }
}
