package com.example.stockwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** kind: "ABOVE" / "BELOW" = price threshold; "PCT" = abs daily change% threshold. */
data class Alert(
    val symbol: String,
    val kind: String,
    val value: Double,
    val enabled: Boolean = true
) {
    fun describe(): String = when (kind) {
        "ABOVE" -> "$symbol が $value 以上"
        "BELOW" -> "$symbol が $value 以下"
        else -> "$symbol が ±$value% 変動"
    }
}

object AlertStore {

    private const val KEY = "alerts_json"

    fun load(context: Context): MutableList<Alert> {
        val raw = StockPrefs.getString(context, KEY)
        if (raw.isBlank()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Alert(
                    o.getString("symbol"),
                    o.getString("kind"),
                    o.getDouble("value"),
                    o.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(mutableListOf())
    }

    fun save(context: Context, alerts: List<Alert>) {
        val arr = JSONArray()
        for (a in alerts) {
            arr.put(
                JSONObject()
                    .put("symbol", a.symbol)
                    .put("kind", a.kind)
                    .put("value", a.value)
                    .put("enabled", a.enabled)
            )
        }
        StockPrefs.putString(context, KEY, arr.toString())
    }

    fun add(context: Context, alert: Alert) {
        val list = load(context)
        list.add(alert)
        save(context, list)
    }

    fun removeAt(context: Context, index: Int) {
        val list = load(context)
        if (index in list.indices) {
            list.removeAt(index)
            save(context, list)
        }
    }
}
