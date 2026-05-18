package com.example.stockwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Holding(val symbol: String, val shares: Double, val avgCost: Double)

object HoldingStore {

    private const val KEY = "holdings_json"

    fun load(context: Context): MutableList<Holding> {
        val raw = StockPrefs.getString(context, KEY)
        if (raw.isBlank()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Holding(
                    o.getString("symbol"),
                    o.getDouble("shares"),
                    o.getDouble("avgCost")
                )
            }
        }.getOrDefault(mutableListOf())
    }

    fun save(context: Context, list: List<Holding>) {
        val arr = JSONArray()
        for (h in list) {
            arr.put(
                JSONObject()
                    .put("symbol", h.symbol)
                    .put("shares", h.shares)
                    .put("avgCost", h.avgCost)
            )
        }
        StockPrefs.putString(context, KEY, arr.toString())
    }

    fun add(context: Context, h: Holding) {
        val list = load(context)
        list.add(h)
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
