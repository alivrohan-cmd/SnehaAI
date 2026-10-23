package com.sneha.ai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class MemoryEntry(val id: String, val text: String)

/** ব্যবহারকারী-নিয়ন্ত্রিত লোকাল মেমরি। শুধু ফোনের অভ্যন্তরীণ স্টোরেজে থাকে। */
class MemoryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "memory.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<MemoryEntry>> = _items

    private fun load(): List<MemoryEntry> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map { val o = a.getJSONObject(it); MemoryEntry(o.getString("id"), o.getString("text")) }
        }
    } catch (e: Exception) { emptyList() }

    @Synchronized
    private fun persist(l: List<MemoryEntry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text)) }
        file.writeText(a.toString())
        _items.value = l
    }

    /** সফল হলে null, নইলে ত্রুটির বার্তা */
    fun add(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return "খালি লেখা সংরক্ষণ করা যায় না"
        if (looksSensitive(t)) return "পাসওয়ার্ড/কী/OTP/কার্ড নম্বরের মতো সংবেদনশীল তথ্য সংরক্ষণ করা হয় না"
        persist(_items.value + MemoryEntry(UUID.randomUUID().toString(), t.take(300)))
        return null
    }

    fun update(id: String, text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return "খালি লেখা সংরক্ষণ করা যায় না"
        if (looksSensitive(t)) return "সংবেদনশীল তথ্য সংরক্ষণ করা হয় না"
        persist(_items.value.map { if (it.id == id) it.copy(text = t.take(300)) else it })
        return null
    }

    fun delete(id: String) = persist(_items.value.filter { it.id != id })

    fun clear() {
        file.delete()
        _items.value = emptyList()
    }

    fun exportJson(): String {
        val a = JSONArray()
        _items.value.forEach { a.put(JSONObject().put("text", it.text)) }
        return JSONObject().put("app", "Sneha AI").put("memory", a).toString(2)
    }

    companion object {
        fun looksSensitive(t: String): Boolean =
            Regex("(?i)(password|passwd|\\botp\\b|\\bpin\\b|api[_ -]?key|secret|token|পাসওয়ার্ড|ওটিপি|পিন নম্বর)").containsMatchIn(t) ||
                Regex("[A-Za-z0-9_\\-]{32,}").containsMatchIn(t) ||
                Regex("\\b\\d{12,19}\\b").containsMatchIn(t)
    }
}
