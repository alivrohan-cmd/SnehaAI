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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val keySet = MutableStateFlow(a.keys.has(GEMINI_KEY_NAME) || a.keys.has(GATEWAY_KEY_NAME))
    val gatewayKeySet = MutableStateFlow(a.keys.has(GATEWAY_KEY_NAME))
    val geminiKeySet = MutableStateFlow(a.keys.has(GEMINI_KEY_NAME))
    val models = MutableStateFlow<List<String>>(emptyList())
    val conn = MutableStateFlow("এখনও পরীক্ষা করা হয়নি")
    val connOk = MutableStateFlow<Boolean?>(null)
    val testingConn = MutableStateFlow(false)
    val retryStatus = MutableStateFlow<String?>(null)
    val handsFree = MutableStateFlow(true)
    val lastSpokenReply = MutableStateFlow("")

    private val stt = SpeechInput(a)
    private var thinkJob: Job? = null
    private var inForeground = true
    private var awakeForNextCommand = false

    private val wakeWords = listOf(
        "doreman", "doraemon", "doremon", "ডোরেমন", "ডরেমন", "দোরেমন", "hey doreman", "হে ডোরেমন", "hello doreman"
    )

    private fun currentProvider(): AiProvider = a.activeProvider(settings.value.providerMode)

    fun onAppBackgrounded() {
        inForeground = false
        stt.release()
        if (!a.executor.busy) {
            a.tts.stop()
            phase.value = Phase.Idle
            partial.value = ""
            level.value = 0f
        }
    }

    fun onAppForegrounded() {
        inForeground = true
        if (handsFree.value && keySet.value && !a.executor.busy && phase.value == Phase.Idle) {
            scheduleNextListen(500)
        }
    }

    private fun onVoiceResult(spoken: String) {
        val raw = spoken.trim()
        if (raw.isEmpty()) {
            if (handsFree.value && inForeground) scheduleNextListen(600)
            return
        }

        val lower = raw.lowercase()
        val hasWakeWord = wakeWords.any { lower.contains(it) }

        if (!hasWakeWord && !awakeForNextCommand) {
            if (handsFree.value && inForeground) scheduleNextListen(500)
            return
        }

        val cleanedCmd = stripWakeWord(raw)
        if (cleanedCmd.isBlank()) {
            awakeForNextCommand = true
            val greet = "হ্যাঁ বলুন, আমি শুনছি!"
            lastSpokenReply.value = greet
            speak(greet) {
                if (handsFree.value && inForeground) scheduleNextListen(350)
            }
            return
        }

        awakeForNextCommand = false
        send(cleanedCmd, isRetry = false)
    }

    fun send(text: String, isRetry: Boolean = false) {
        val raw = text.trim()
        if (raw.isEmpty()) return

        val cleanedCmd = stripWakeWord(raw).ifBlank { raw }
        a.tts.stop()
        thinkJob?.cancel()
        thinkJob = viewModelScope.launch {
            if (isRetry) {
                a.chat.removeErrorsForRetry(cleanedCmd)
            } else {
                a.chat.add(ChatMsg("user", cleanedCmd))
            }
            phase.value = Phase.Thinking
            banner.value = null
            retryStatus.value = null

            val s = settings.value
            val hist = a.chat.items.value
                .filter { (it.role == "user" || it.role == "model") && !it.isError }
                .takeLast(12)
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

            val res = currentProvider().generate(sys, hist, cfg, true) { statusMsg ->
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
                val friendly = aiErr?.userMessage() ?: "সংযোগ বা সার্ভার ত্রুটি: আবার চেষ্টা করুন।"
                lastSpokenReply.value = friendly
                banner.value = friendly
                if (handsFree.value && inForeground) scheduleNextListen(2000)
            }
        }
    }

    private fun stripWakeWord(input: String): String {
        var s = input.trim()
        for (w in wakeWords) {
            if (s.equals(w, true)) return ""
            val idx = s.lowercase().indexOf(w)
            if (idx == 0) {
                s = s.substring(w.length).trim().removePrefix(",").trim()
            }
        }
        return s
    }

    fun retry(prompt: String) {
        send(prompt, isRetry = true)
    }

    private fun handle(p: Plan, modelUsed: String, usedFallback: Boolean) {
        val reply = p.reply.ifBlank { if (p.actions.isEmpty()) "…" else "ঠিক আছে, করছি।" }
        val badge = if (usedFallback) "$modelUsed (বিকল্প)" else modelUsed
        a.chat.add(ChatMsg(role = "model", text = reply, modelUsed = badge))
        lastSpokenReply.value = reply

        val hasActions = p.actions.isNotEmpty()
        if (settings.value.autoSpeak && inForeground) {
            speak(reply) {
                if (handsFree.value && inForeground && !hasActions && !a.executor.busy) {
                    scheduleNextListen(450)
                }
            }
        }
        if (hasActions) {
            runActions(p.actions)
        }
    }

    private fun runActions(list: List<Action>) {
        if (a.executor.busy) a.executor.cancel()
        a.executor.run(list) { log, _ ->
            val summary = log.lastOrNull() ?: ""
            if (summary.isNotBlank()) {
                lastSpokenReply.value = summary
            }
            if (handsFree.value && inForeground) {
                scheduleNextListen(1200)
            }
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        viewModelScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (inForeground && handsFree.value && !a.executor.busy && phase.value == Phase.Idle && !a.tts.speaking.value) {
                startListening(manualTap = false)
            }
        }
    }

    fun confirm() {
        val p = pending.value ?: return
        pending.value = null
        runActions(p.actions)
    }

    fun deny() {
        pending.value = null
    }

    fun openApp(name: String) {
        viewModelScope.launch {
            val r = a.launcher.open(name, settings.value.favorites)
            lastSpokenReply.value = r.msg
        }
    }

    fun clearChat() {
        a.chat.clear()
        lastSpokenReply.value = ""
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val s = settings.value
        if (!a.tts.speak(text, s.lang, s.speechRate, s.volume, s.voiceName, onDone)) {
            a.tts.error.value?.let { banner.value = it }
            onDone?.invoke()
        }
    }

    fun toggleHandsFree(enable: Boolean) {
        handsFree.value = enable
        if (enable) {
            startListening(manualTap = false)
        } else {
            stopListening()
        }
    }

    fun startListening(manualTap: Boolean = true) {
        if (!inForeground) return
        a.tts.stop()
        if (a.executor.busy) return
        if (manualTap) awakeForNextCommand = true
        phase.value = Phase.Listening
        partial.value = ""
        stt.start(
            settings.value.lang,
            onPartial = { partial.value = it },
            onFinal = {
                phase.value = Phase.Idle
                partial.value = ""
                level.value = 0f
                onVoiceResult(it)
            },
            onError = {
                phase.value = Phase.Idle
                partial.value = ""
                level.value = 0f
                if (handsFree.value && inForeground) {
                    scheduleNextListen(800)
                }
            },
            onLevel = { level.value = it },
        )
    }

    fun stopListening() = stt.stop()
    fun stopSpeaking() = a.tts.stop()

    fun stopAll() {
        handsFree.value = false
        awakeForNextCommand = false
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
        geminiKeySet.value = true
        keySet.value = true
        connOk.value = null
        conn.value = "Gemini কী সংরক্ষিত হয়েছে — এবার 'Test Connection' চাপুন"
    }

    fun saveGatewayKey(k: String) {
        a.keys.save(GATEWAY_KEY_NAME, k.trim())
        gatewayKeySet.value = true
        keySet.value = true
        connOk.value = null
        conn.value = "Gateway API Key (sk-...) সংরক্ষিত হয়েছে — এবার 'Test Connection' চাপুন"
    }

    fun deleteKey() {
        a.keys.delete(GEMINI_KEY_NAME)
        a.keys.delete(GATEWAY_KEY_NAME)
        geminiKeySet.value = false
        gatewayKeySet.value = false
        keySet.value = false
        connOk.value = false
        models.value = emptyList()
        conn.value = "API key মুছে ফেলা হয়েছে"
    }

    fun testConnection() {
        viewModelScope.launch {
            testingConn.value = true
            val mode = settings.value.providerMode
            conn.value = if (mode == "gateway") "কাস্টম গেটওয়ে (${settings.value.gatewayBaseUrl})-এর সাথে সংযোগ পরীক্ষা চলছে…"
            else "Google Gemini সার্ভারের সাথে সংযোগ পরীক্ষা চলছে…"

            currentProvider().listModels().onSuccess { list ->
                models.value = list
                connOk.value = list.isNotEmpty()
                if (list.isEmpty()) {
                    conn.value = "সংযোগ হয়েছে কিন্তু কোনো মডেল পাওয়া যায়নি।"
                } else {
                    val cur = settings.value.model
                    var msg = "✓ সংযোগ সফল — ${list.size}টি মডেল পাওয়া গেছে"
                    if (cur !in list) {
                        val pick = list.firstOrNull { it.contains("claude", true) || it.contains("flash", true) || it.contains("gpt", true) }
                            ?: list.firstOrNull()
                        if (pick != null) {
                            a.prefs.set(K.model, pick)
                            msg += "। মডেল হিসেবে '$pick' সেট করা হয়েছে"
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

    fun <T> set(key: Preferences.Key<T>, v: T) {
        viewModelScope.launch { a.prefs.set(key, v) }
    }

    override fun onCleared() {
        stt.release()
        super.onCleared()
    }
}
