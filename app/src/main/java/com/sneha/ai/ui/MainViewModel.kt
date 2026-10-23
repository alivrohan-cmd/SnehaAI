package com.sneha.ai.ui

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.*
import com.sneha.ai.automation.SnehaAccessibilityService
import com.sneha.ai.data.K
import com.sneha.ai.data.Settings
import com.sneha.ai.voice.SpeechInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Phase { Idle, Listening, Thinking, Speaking, Executing }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as SnehaApp

    val settings: StateFlow<Settings> = a.prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val messages = a.chat.items
    val memory = a.memory.items
    val exec = a.executor.status

    private val phase = MutableStateFlow(Phase.Idle)
    val uiPhase: StateFlow<Phase> = combine(phase, a.tts.speaking, a.executor.status) { p, sp, e ->
        when {
            e.running -> Phase.Executing
            p != Phase.Idle -> p
            sp -> Phase.Speaking
            else -> Phase.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Phase.Idle)

    val partial = MutableStateFlow("")
    val level = MutableStateFlow(0f)
    val banner = MutableStateFlow<String?>(null)
    val pending = MutableStateFlow<Plan?>(null)
    val keySet = MutableStateFlow(a.keys.has(GEMINI_KEY_NAME))
    val models = MutableStateFlow<List<String>>(emptyList())
    val conn = MutableStateFlow("এখনও পরীক্ষা করা হয়নি")

    private val stt = SpeechInput(a)
    private var thinkJob: Job? = null

    // ---------- চ্যাট ----------
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        a.tts.stop()
        thinkJob?.cancel()
        thinkJob = viewModelScope.launch {
            a.chat.add(ChatMsg("user", t))
            phase.value = Phase.Thinking
            banner.value = null
            val s = settings.value
            val hist = a.chat.items.value.filter { it.role != "sys" }.takeLast(14)
            val sys = SystemPrompt.build(s, if (s.memoryEnabled) a.memory.items.value else emptyList(), SnehaAccessibilityService.connected.value)
            val cfg = GenConfig(s.model, if (s.useTemperature) s.temperature else null, s.maxTokens)
            val res = a.provider.generate(sys, hist, cfg, true)
            phase.value = Phase.Idle
            res.onSuccess { r ->
                a.prefs.addUsage(r.promptTokens, r.outputTokens)
                handle(PlanParser.parse(r.text))
            }.onFailure { e ->
                if (e is CancellationException) throw e
                a.chat.add(ChatMsg("sys", (e as? AiException)?.userMessage() ?: "ত্রুটি: ${e.message}"))
            }
        }
    }

    private fun handle(p: Plan) {
        val reply = p.reply.ifBlank { if (p.actions.isEmpty()) "…" else "ঠিক আছে, করছি।" }
        a.chat.add(ChatMsg("model", reply))
        if (settings.value.autoSpeak) speak(reply)
        if (p.rejected.isNotEmpty()) a.chat.add(ChatMsg("sys", "অসমর্থিত অ্যাকশন বাদ দেওয়া হয়েছে: ${p.rejected.joinToString()}"))
        if (p.actions.isEmpty()) return
        if (p.needsConfirm()) pending.value = p else runActions(p.actions)
    }

    private fun runActions(list: List<Action>) {
        if (a.executor.busy) { banner.value = "আরেকটি কাজ চলছে। আগে Stop চাপুন।"; return }
        a.executor.run(list) { log, _ -> a.chat.add(ChatMsg("sys", log.joinToString("\n"))) }
    }

    fun confirm() {
        val p = pending.value ?: return
        pending.value = null
        runActions(p.actions)
    }

    fun deny() {
        if (pending.value != null) a.chat.add(ChatMsg("sys", "■ আপনি অ্যাকশন বাতিল করেছেন"))
        pending.value = null
    }

    fun openApp(name: String) {
        viewModelScope.launch {
            val r = a.launcher.open(name, settings.value.favorites)
            a.chat.add(ChatMsg("sys", (if (r.ok) "✓ " else "✗ ") + r.msg))
        }
    }

    fun clearChat() = a.chat.clear()

    // ---------- ভয়েস ----------
    fun speak(text: String) {
        val s = settings.value
        if (!a.tts.speak(text, s.lang, s.speechRate, s.volume, s.voiceName)) a.tts.error.value?.let { banner.value = it }
    }

    fun startListening() {
        a.tts.stop()
        if (a.executor.busy) return
        phase.value = Phase.Listening
        partial.value = ""
        stt.start(
            settings.value.lang,
            onPartial = { partial.value = it },
            onFinal = { phase.value = Phase.Idle; partial.value = ""; level.value = 0f; send(it) },
            onError = { phase.value = Phase.Idle; partial.value = ""; level.value = 0f; banner.value = it },
            onLevel = { level.value = it },
        )
    }

    fun stopListening() = stt.stop()
    fun stopSpeaking() = a.tts.stop()

    /** জরুরি Stop: অটোমেশন, কথা বলা, শোনা ও ভাবা — সব বন্ধ */
    fun stopAll() {
        a.executor.cancel()
        a.tts.stop()
        thinkJob?.cancel()
        stt.release()
        phase.value = Phase.Idle
        partial.value = ""
        level.value = 0f
    }

    // ---------- AI Brain ----------
    fun saveKey(k: String) {
        a.keys.save(GEMINI_KEY_NAME, k.trim())
        keySet.value = true
        conn.value = "কী সংরক্ষিত — Test Connection চালান"
    }

    fun deleteKey() {
        a.keys.delete(GEMINI_KEY_NAME)
        keySet.value = false
        models.value = emptyList()
        conn.value = "কী মুছে ফেলা হয়েছে"
    }

    fun testConnection() {
        viewModelScope.launch {
            conn.value = "পরীক্ষা চলছে…"
            a.provider.listModels().onSuccess { list ->
                models.value = list
                val cur = settings.value.model
                var msg = "সংযোগ ঠিক আছে — ${list.size}টি মডেল পাওয়া গেছে"
                if (cur !in list) {
                    val pick = pickDefault(list)
                    if (pick != null) { a.prefs.set(K.model, pick); msg += "। নির্বাচিত মডেল তালিকায় ছিল না, তাই $pick বেছে নেওয়া হয়েছে" }
                }
                conn.value = msg
            }.onFailure { e ->
                if (e is CancellationException) throw e
                conn.value = (e as? AiException)?.userMessage() ?: "ত্রুটি: ${e.message}"
            }
        }
    }

    /** সবচেয়ে নতুন সংস্করণের সাধারণ 'flash' মডেল (lite/image/tts/live বাদে) */
    private fun pickDefault(list: List<String>): String? {
        fun ver(n: String) = Regex("(\\d+)(?:\\.(\\d+))?").find(n)?.let { (it.groupValues[1].toInt() * 100) + (it.groupValues[2].toIntOrNull() ?: 0) } ?: 0
        return list.filter { n -> n.contains("flash") && listOf("lite", "image", "tts", "live", "audio", "thinking", "exp").none { n.contains(it) } }
            .maxByOrNull { ver(it) }
    }

    fun <T> set(key: Preferences.Key<T>, v: T) {
        viewModelScope.launch { a.prefs.set(key, v) }
    }

    override fun onCleared() {
        stt.release()
        super.onCleared()
    }
}
