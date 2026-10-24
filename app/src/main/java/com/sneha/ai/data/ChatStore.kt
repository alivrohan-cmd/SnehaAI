package com.sneha.ai.data

import android.content.Context
import com.sneha.ai.ai.ChatMsg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chat.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ChatMsg>> = _items

    private fun load(): List<ChatMsg> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                ChatMsg(
                    role = o.getString("role"),
                    text = o.getString("text"),
                    ts = o.optLong("ts"),
                    modelUsed = o.optString("modelUsed").takeIf { s -> s.isNotBlank() },
                    isError = o.optBoolean("isError", false),
                    retryPrompt = o.optString("retryPrompt").takeIf { s -> s.isNotBlank() }
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun saveAll(l: List<ChatMsg>) {
        val trimmed = l.takeLast(200)
        val a = JSONArray()
        trimmed.forEach {
            val obj = JSONObject()
                .put("role", it.role)
                .put("text", it.text)
                .put("ts", it.ts)
                .put("isError", it.isError)
            it.modelUsed?.let { m -> obj.put("modelUsed", m) }
            it.retryPrompt?.let { rp -> obj.put("retryPrompt", rp) }
            a.put(obj)
        }
        try {
            file.writeText(a.toString())
        } catch (_: Exception) {}
        _items.value = trimmed
    }

    @Synchronized
    fun add(m: ChatMsg) {
        saveAll(_items.value + m)
    }

    @Synchronized
    fun removeErrorsForRetry(prompt: String) {
        saveAll(_items.value.filterNot { it.isError && it.retryPrompt == prompt })
    }

    fun clear() {
        file.delete()
        _items.value = emptyList()
    }
}
