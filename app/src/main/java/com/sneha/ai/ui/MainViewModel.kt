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
    val connOk = MutableStateFlow<Boolean?>(null)
    val testingConn = MutableStateFlow(false)
    val retryStatus = MutableStateFlow<String?>(null)

    private val stt = SpeechInput(a)
    private var thinkJob: Job? = null

    fun send(text: String, isRetry: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty()) return
        a.tts.stop()
        thinkJob?.cancel()
        thinkJob = viewModelScope.launch {
            if (isRetry) {
                a.chat.removeErrorsForRetry(t)
            } else {
                a.chat.add(ChatMsg("user", t))
            }
            phase.value = Phase.Thinking
            banner.value = null
            retryStatus.value = null

            val s = settings.value
            val hist = a.chat.items.value
                .filter { (it.role == "user" || it.role == "model") && !it.isError }
                .takeLast(14)
            val sys = SystemPrompt.build(
                s,
                if (s.memoryEnabled) a.memory.items.value else emptyList(),
                SnehaAccessibilityService.connected.value
            )
            val cfg = GenConfig(
                model = s.model,
                temperature = if (s.useTemperature) s.temperature else null,
                maxTokens = s.maxTokens,
                fallbackModel = s.fallbackModel,
                autoFallback = s.autoFallback,
                maxRetries = s.maxRetries
            )

            val res = a.provider.generate(sys, hist, cfg, true) { statusMsg ->
                retryStatus.value = statusMsg
            }
            phase.value = Phase.Idle
            retryStatus.value = null

            res.onSuccess { r ->
                connOk.value = true
                a.prefs.addUsage(r.promptTokens, r.outputTokens)
                handle(PlanParser.parse(r.text), r.modelUsed.ifBlank { s.model }, r.usedFallback)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                val aiErr = e as? AiException
                if (aiErr?.kind == AiError.INVALID_KEY || aiErr?.kind == AiError.NO_KEY) {
                    connOk.value = false
                }
                val friendly = aiErr?.userMessage() ?: "সংযোগ বা সার্ভার ত্রুটি: অনুগ্রহ করে আবার চেষ্টা করুন।"
                a.chat.add(
                    ChatMsg(
                        role = "sys",
                        text = friendly,
                        isError = true,
                        retryPrompt = t
                    )
                )
            }
        }
    }

    fun retry(prompt: String) {
        send(prompt, isRetry = true)
    }

    private fun handle(p: Plan, modelUsed: String, usedFallback: Boolean) {
        val reply = p.reply.ifBlank { if (p.actions.isEmpty()) "…" else "ঠিক আছে, করছি।" }
        val badge = if (usedFallback) "$modelUsed (বিকল্প)" else modelUsed
        a.chat.add(ChatMsg(role = "model", text = reply, modelUsed = badge))
        if (settings.value.autoSpeak) speak(reply)
        if (p.rejected.isNotEmpty()) {
            a.chat.add(ChatMsg("sys", "অসমর্থিত অ্যাকশন বাদ দেওয়া হয়েছে: ${p.rejected.joinToString()}"))
        }
        if (p.actions.isEmpty()) return
        if (p.needsConfirm()) pending.value = p else runActions(p.actions)
    }

    private fun runActions(list: List<Action>) {
        if (a.executor.busy) {
            banner.value = "আরেকটি কাজ চলছে। আগে Stop চাপুন।"
            return
        }
        a.executor.run(list) { log, _ ->
            a.chat.add(ChatMsg("sys", log.joinToString("\n")))
        }
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

    fun speak(text: String) {
        val s = settings.value
        if (!a.tts.speak(text, s.lang, s.speechRate, s.volume, s.voiceName)) {
            a.tts.error.value?.let { banner.value = it }
        }
    }

    fun startListening() {
        a.tts.stop()
        if (a.executor.busy) return
        phase.value = Phase.Listening
        partial.value = ""
        stt.start(
            settings.value.lang,
            onPartial = { partial.value = it },
            onFinal = {
                phase.value = Phase.Idle
                partial.value = ""
                level.value = 0f
                send(it)
            },
            onError = {
                phase.value = Phase.Idle
                partial.value = ""
                level.value = 0f
                banner.value = it
            },
            onLevel = { level.value = it },
        )
    }

    fun stopListening() = stt.stop()
    fun stopSpeaking() = a.tts.stop()

    fun stopAll() {
        a.executor.cancel()
        a.tts.stop()
        thinkJob?.cancel()
        stt.release()
        phase.value = Phase.Idle
        partial.value = ""
        retryStatus.value = null
        level.value = 0f
    }

    fun saveKey(k: String) {
        a.keys.save(GEMINI_KEY_NAME, k.trim())
        keySet.value = true
        connOk.value = null
        conn.value = "কী সংরক্ষিত হয়েছে — এবার 'Test Connection' চাপুন"
    }

    fun deleteKey() {
        a.keys.delete(GEMINI_KEY_NAME)
        keySet.value = false
        connOk.value = false
        models.value = emptyList()
        conn.value = "API key মুছে ফেলা হয়েছে"
    }

    fun testConnection() {
        viewModelScope.launch {
            testingConn.value = true
            conn.value = "Google Gemini সার্ভারের সাথে সংযোগ পরীক্ষা চলছে…"
            a.provider.listModels().onSuccess { list ->
                models.value = list
                connOk.value = list.isNotEmpty()
                if (list.isEmpty()) {
                    conn.value = "সংযোগ হয়েছে কিন্তু কোনো generateContent মডেল পাওয়া যায়নি।"
                } else {
                    val cur = settings.value.model
                    val curFallback = settings.value.fallbackModel
                    var msg = "✓ সংযোগ সফল — আপনার API key-তে ${list.size}টি মডেল সক্রিয় আছে"
                    if (cur !in list) {
                        val pick = pickDefault(list)
                        if (pick != null) {
                            a.prefs.set(K.model, pick)
                            msg += "। প্রাথমিক মডেল হিসেবে '$pick' বেছে নেওয়া হয়েছে"
                        }
                    }
                    if (curFallback !in list) {
                        val fbPick = pickFallbackDefault(list, settings.value.model)
                        if (fbPick != null) {
                            a.prefs.set(K.fallbackModel, fbPick)
                        }
                    }
                    conn.value = msg
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                connOk.value = false
                conn.value = (e as? AiException)?.userMessage() ?: "ত্রুটি: ${e.message}"
            }
            testingConn.value = false
        }
    }

    private fun pickDefault(list: List<String>): String? {
        fun ver(n: String) = Regex("(\\d+)(?:\\.(\\d+))?").find(n)?.let {
            (it.groupValues[1].toInt() * 100) + (it.groupValues[2].toIntOrNull() ?: 0)
        } ?: 0
        return list.filter { n ->
            n.contains("flash") &&
                listOf("lite", "image", "tts", "live", "audio", "thinking", "exp").none { n.contains(it) }
        }.maxByOrNull { ver(it) } ?: list.firstOrNull()
    }

    private fun pickFallbackDefault(list: List<String>, primary: String): String? {
        val candidates = list.filter { n ->
            n != primary &&
                listOf("image", "tts", "live", "audio", "transcribe").none { n.contains(it) }
        }
        return candidates.firstOrNull { it.contains("flash-lite") }
            ?: candidates.firstOrNull { it.contains("flash") }
            ?: candidates.firstOrNull()
    }

    fun <T> set(key: Preferences.Key<T>, v: T) {
        viewModelScope.launch { a.prefs.set(key, v) }
    }

    override fun onCleared() {
        stt.release()
        super.onCleared()
    }
}
