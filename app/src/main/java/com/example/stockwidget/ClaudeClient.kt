package com.example.stockwidget

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Anthropic Messages API client over raw HTTP (no JVM SDK — keeps the
 * APK small and avoids Android desugaring issues). Uses Claude with the
 * server-side web search tool so the outlook reflects current news.
 *
 * The API key is supplied by the user and stored on-device only.
 */
object ClaudeClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-opus-4-7"
    private const val ANTHROPIC_VERSION = "2023-06-01"

    private const val SYSTEM_PROMPT =
        "あなたは日本語で市況解説を書くアシスタントです。world の最新ニュースや" +
        "経済指標を web_search で確認し、与えられたウォッチ銘柄(指数・為替・株式)の" +
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

        val system = JSONArray().put(
            JSONObject()
                .put("type", "text")
                .put("text", SYSTEM_PROMPT)
                .put("cache_control", JSONObject().put("type", "ephemeral"))
        )
        val tools = JSONArray().put(
            JSONObject().put("type", "web_search_20260209").put("name", "web_search")
        )
        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", userContext)
        )
        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 3000)
            .put("thinking", JSONObject().put("type", "adaptive"))
            .put("system", system)
            .put("tools", tools)
            .put("messages", messages)

        return try {
            var convo = messages
            for (attempt in 0..3) {
                val resp = post(apiKey, rebuild(body, convo))
                    ?: return Result.Err("ネットワークエラー")
                val obj = JSONObject(resp)
                if (obj.has("error")) {
                    val msg = obj.optJSONObject("error")?.optString("message") ?: "APIエラー"
                    return Result.Err(msg)
                }
                val content = obj.optJSONArray("content") ?: return Result.Err("空の応答")
                if (obj.optString("stop_reason") == "pause_turn" && attempt < 3) {
                    // Server tool loop paused; resume by echoing the assistant turn.
                    convo = JSONArray(convo.toString())
                        .put(JSONObject().put("role", "assistant").put("content", content))
                    continue
                }
                return Result.Ok(extractText(content).ifBlank { "応答を取得できませんでした" })
            }
            Result.Err("応答が完了しませんでした")
        } catch (e: Exception) {
            Result.Err(e.message ?: "不明なエラー")
        }
    }

    private fun rebuild(base: JSONObject, messages: JSONArray): JSONObject =
        JSONObject(base.toString()).put("messages", messages)

    private fun extractText(content: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(block.optString("text"))
            }
        }
        return sb.toString().trim()
    }

    private fun post(apiKey: String, body: JSONObject): String? {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
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
