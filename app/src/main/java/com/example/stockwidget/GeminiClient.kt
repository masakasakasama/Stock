package com.example.stockwidget

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Gemini (Generative Language API) over raw HTTP. Uses the free-tier
 * `gemini-2.5-flash` model with Google Search grounding so the outlook
 * reflects current news. The API key is user-supplied (Google AI Studio,
 * free) and stored on-device only.
 */
object GeminiClient {

    private const val MODEL = "gemini-2.5-flash"
    private const val BASE =
        "https://generativelanguage.googleapis.com/v1beta/models/"

    private const val SYSTEM_PROMPT =
        "あなたは日本語で市況解説を書くアシスタントです。Google検索で世の中の" +
        "最新ニュースや経済指標を確認し、与えられたウォッチ銘柄(指数・為替・株式)の" +
        "現状を踏まえて、相場と世の中の流れの見通しを書いてください。\n" +
        "出力は次の見出しで、各2〜4文・簡潔に:\n" +
        "【総合】世の中の流れと相場全体の地合い\n" +
        "【日】今日〜1日\n【週】1週間\n【月】1ヶ月\n【半年】6ヶ月\n" +
        "【1年】1年\n【3年】3年\n【主な注目材料】箇条書き3点\n" +
        "最後に必ず: 「※本テキストはAIによる参考情報であり、将来を保証する投資助言ではありません。」\n" +
        "断定を避け、上下双方のシナリオに触れること。"

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Err(val message: String) : Result()
    }

    fun generateOutlook(apiKey: String, userContext: String): Result {
        if (apiKey.isBlank()) return Result.Err("APIキーが設定されていません")

        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", userContext))
                        )
                )
            )
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put(
                "generationConfig",
                JSONObject().put("maxOutputTokens", 2048).put("temperature", 0.7)
            )

        return try {
            val key = URLEncoder.encode(apiKey, "UTF-8")
            val url = URL("$BASE$MODEL:generateContent?key=$key")
            val resp = post(url, body) ?: return Result.Err("ネットワークエラー")
            val obj = JSONObject(resp)
            if (obj.has("error")) {
                val msg = obj.optJSONObject("error")?.optString("message") ?: "APIエラー"
                return Result.Err(msg)
            }
            val candidates = obj.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.Err("空の応答")
            }
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return Result.Err("応答を取得できませんでした")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                if (t.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(t)
                }
            }
            Result.Ok(sb.toString().trim().ifBlank { "応答を取得できませんでした" })
        } catch (e: Exception) {
            Result.Err(e.message ?: "不明なエラー")
        }
    }

    private fun post(url: URL, body: JSONObject): String? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
