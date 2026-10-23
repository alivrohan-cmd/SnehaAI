package com.sneha.ai.data

import android.content.Context
import com.sneha.ai.ai.ChatMsg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** চ্যাট ইতিহাস (সর্বশেষ ২০০টি বার্তা) — শুধু লোকালি। Room-এর বদলে সহজ JSON ফাইল, যাতে Termux বিল্ড হালকা থাকে। */
class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chat.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ChatMsg>> = _items

    private fun load(): List<ChatMsg> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                ChatMsg(o.getString("role"), o.getString("text"), o.optLong("ts"))
            }
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized
    fun add(m: ChatMsg) {
        val l = (_items.value + m).takeLast(200)
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("role", it.role).put("text", it.text).put("ts", it.ts)) }
        try { file.writeText(a.toString()) } catch (_: Exception) {}
        _items.value = l
    }

    fun clear() {
        file.delete()
        _items.value = emptyList()
    }
}
