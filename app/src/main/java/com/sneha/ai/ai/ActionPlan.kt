package com.sneha.ai.ai

import org.json.JSONArray
import org.json.JSONObject

sealed class Action {
    data class OpenApp(val app: String) : Action()
    data class YouTubeSearch(val query: String, val playFirst: Boolean) : Action()
    data class Media(val cmd: String) : Action()
    data class TapText(val text: String) : Action()
    data class TapXY(val x: Int, val y: Int) : Action()
    data class Scroll(val down: Boolean) : Action()
    object Back : Action()
    object Home : Action()
    data class TypeText(val text: String) : Action()
    object Enter : Action()
    object ReadScreen : Action()
    data class Wait(val ms: Long) : Action()
    data class Remember(val text: String) : Action()
}

fun Action.isUi(): Boolean = when (this) {
    is Action.TapText, is Action.TapXY, is Action.Scroll, is Action.TypeText -> true
    Action.Back, Action.Home, Action.Enter, Action.ReadScreen -> true
    is Action.YouTubeSearch -> playFirst
    else -> false
}

fun Action.describe(): String = when (this) {
    is Action.OpenApp -> "অ্যাপ খোলা: $app"
    is Action.YouTubeSearch -> "YouTube সার্চ: \"$query\"" + if (playFirst) " + প্রথম ফলাফল চালানো" else ""
    is Action.Media -> "মিডিয়া কমান্ড: $cmd"
    is Action.TapText -> "ট্যাপ: \"$text\""
    is Action.TapXY -> "ট্যাপ: ($x,$y)"
    is Action.Scroll -> "স্ক্রল " + if (down) "নিচে" else "উপরে"
    Action.Back -> "Back চাপা"
    Action.Home -> "Home-এ যাওয়া"
    is Action.TypeText -> "লেখা: \"$text\""
    Action.Enter -> "Enter চাপা"
    Action.ReadScreen -> "স্ক্রিনের লেবেল পড়া (শুধু ফোনে দেখানো হবে)"
    is Action.Wait -> "অপেক্ষা ${ms}ms"
    is Action.Remember -> "মনে রাখা: \"$text\""
}

data class Plan(val reply: String, val actions: List<Action>, val rejected: List<String>) {
    fun needsConfirm(): Boolean = actions.any { it.isUi() || it is Action.Remember }
}

object PlanParser {
    fun parse(raw: String): Plan {
        val cleaned = extractJsonObject(raw)
        val o = try {
            if (cleaned != null) JSONObject(cleaned) else JSONObject(raw.trim())
        } catch (_: Exception) {
            return Plan(cleanPlainReply(raw), emptyList(), emptyList())
        }

        val reply = o.optString("reply").trim().ifBlank {
            o.optString("text").trim().ifBlank { o.optString("message").trim() }
        }
        val arr = o.optJSONArray("actions") ?: JSONArray()
        val ok = mutableListOf<Action>()
        val rej = mutableListOf<String>()

        for (i in 0 until minOf(arr.length(), 10)) {
            val a = arr.optJSONObject(i) ?: continue
            val t = a.optString("type").trim()
            val act: Action? = when (t) {
                "open_app" -> a.optString("app").trim().takeIf { it.isNotBlank() }?.let { Action.OpenApp(it.take(60)) }
                "youtube_search" -> a.optString("query").trim().takeIf { it.isNotBlank() }
                    ?.let { Action.YouTubeSearch(it.take(120), a.optBoolean("play_first", false)) }
                "media" -> a.optString("command").trim().lowercase()
                    .takeIf { it in setOf("play", "pause", "next", "previous") }
                    ?.let { Action.Media(it) }
                "ui_tap_text" -> a.optString("text").trim().takeIf { it.isNotBlank() }?.let { Action.TapText(it.take(80)) }
                "ui_tap_xy" -> {
                    val x = a.optInt("x", -1)
                    val y = a.optInt("y", -1)
                    if (x in 0..5000 && y in 0..5000) Action.TapXY(x, y) else null
                }
                "ui_scroll" -> when (a.optString("direction").trim().lowercase()) {
                    "down" -> Action.Scroll(true)
                    "up" -> Action.Scroll(false)
                    else -> null
                }
                "ui_back" -> Action.Back
                "ui_home" -> Action.Home
                "ui_type" -> a.optString("text").takeIf { it.isNotEmpty() }?.let { Action.TypeText(it.take(500)) }
                "ui_enter" -> Action.Enter
                "ui_read" -> Action.ReadScreen
                "wait" -> Action.Wait(a.optLong("ms", 1000).coerceIn(200, 5000))
                "remember" -> a.optString("text").trim().takeIf { it.isNotBlank() }?.let { Action.Remember(it.take(300)) }
                else -> null
            }
            if (act == null) rej.add(t.ifBlank { "?" }) else ok.add(act)
        }
        return Plan(reply, ok, rej)
    }

    private fun extractJsonObject(input: String): String? {
        val s = input.trim()
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val ch = s[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return s.substring(start, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun cleanPlainReply(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
