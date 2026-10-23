package com.sneha.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sneha.ai.SnehaApp
import com.sneha.ai.ai.*
import com.sneha.ai.coding.TermuxBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class CodingViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as SnehaApp
    val langs = listOf("python", "java", "javascript", "html", "css", "c", "cpp", "kotlin", "bash")
    val lang = MutableStateFlow("python")
    val prompt = MutableStateFlow("")
    val code = MutableStateFlow("")
    val note = MutableStateFlow("")
    val log = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val running = MutableStateFlow(false)
    val pendingScript = MutableStateFlow<String?>(null)
    val preview = MutableStateFlow<String?>(null)
    val needPerm = MutableStateFlow(false)
    val saved = MutableStateFlow(a.projects.list())
    private var runJob: Job? = null

    fun fileName(): String = when (lang.value) {
        "python" -> "main.py"; "java" -> "Main.java"; "javascript" -> "script.js"; "html" -> "index.html"
        "css" -> "style.css"; "c" -> "main.c"; "cpp" -> "main.cpp"; "kotlin" -> "Main.kt"; else -> "script.sh"
    }

    fun ask(task: String) {
        val p = prompt.value.trim()
        if (task == "write" && p.isEmpty()) return
        if (task != "write" && code.value.isBlank()) { note.value = "আগে এডিটরে কোড দিন বা তৈরি করুন"; return }
        viewModelScope.launch {
            busy.value = true
            note.value = ""
            val s = a.prefs.current()
            val l = lang.value
            val user = when (task) {
                "write" -> "Language: $l\nTask: $p"
                "explain" -> "Explain this $l code step by step:\n```\n${code.value}\n```"
                else -> "Fix this $l code. Problem/errors: ${p.ifBlank { log.value.takeLast(1500) }}\n```\n${code.value}\n```"
            }
            val sys = "You are a careful coding assistant. Explanations in ${if (s.lang == "bn") "Bengali" else "English"}; code, identifiers and comments in English. " +
                "When you return code, return ONE complete file in a single fenced code block, then a short explanation. " +
                "Never claim the code was executed or tested. Mention the runtime/compiler needed if relevant."
            val cfg = GenConfig(s.model, if (s.useTemperature) 0.2f else null, maxOf(s.maxTokens, 4096))
            a.provider.generate(sys, listOf(ChatMsg("user", user)), cfg, false).onSuccess { r ->
                a.prefs.addUsage(r.promptTokens, r.outputTokens)
                val m = Regex("```[A-Za-z0-9+#_-]*[ \\t]*\\n([\\s\\S]*?)```").find(r.text)
                if (m != null) { code.value = m.groupValues[1].trimEnd() + "\n"; note.value = r.text.replace(m.value, "").trim() }
                else note.value = r.text
            }.onFailure { e ->
                if (e is CancellationException) throw e
                note.value = (e as? AiException)?.userMessage() ?: "ত্রুটি: ${e.message}"
            }
            busy.value = false
        }
    }

    fun requestRun() {
        val l = lang.value
        val c = code.value
        if (c.isBlank()) { log.value = "কোড খালি"; return }
        when (l) {
            "html" -> preview.value = c
            "css" -> preview.value = "<!doctype html><meta charset=utf-8><style>$c</style><body><h1>Heading</h1><p>Paragraph <a href=\"#\">link</a></p><button>Button</button></body>"
            "javascript" -> preview.value = jsWrap(c)
            else -> viewModelScope.launch {
                val s = a.prefs.current()
                val t = a.termux
                if (!s.termuxEnabled) { log.value = "Termux রান বন্ধ। Settings > কোডিং থেকে চালু করুন। কোড শুধু তৈরি হয়েছে, চালানো হয়নি।"; return@launch }
                if (!t.installed()) { log.value = "Termux ইনস্টল নেই। কোড চালানো হয়নি।"; return@launch }
                if (!t.hasPermission()) { needPerm.value = true; return@launch }
                val sc = t.buildScript(l, c)
                if (sc == null) { log.value = "'$l' চালানোর ব্যাকএন্ড নেই (Not implemented)। কোড শুধু তৈরি হয়েছে, চালানো হয়নি।"; return@launch }
                pendingScript.value = sc
            }
        }
    }

    fun approveRun() {
        val sc = pendingScript.value ?: return
        pendingScript.value = null
        val id = UUID.randomUUID().toString()
        log.value = "Termux-এ পাঠানো হচ্ছে…"
        running.value = true
        runJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val r = withTimeoutOrNull(90_000) { TermuxBus.results.first { it.id == id } }
            running.value = false
            log.value = if (r == null) "Termux থেকে ফলাফল আসেনি (টাইমআউট/বাতিল)।"
            else "exit=${r.exit ?: "?"}\n--- stdout ---\n${r.stdout}\n--- stderr ---\n${r.stderr}" + (r.err?.let { "\n--- error ---\n$it" } ?: "")
        }
        val err = a.termux.run(sc, id)
        if (err != null) { runJob?.cancel(); running.value = false; log.value = "ত্রুটি: $err" }
    }

    fun cancelRun() {
        runJob?.cancel()
        running.value = false
        log.value += "\nঅপেক্ষা বন্ধ করা হয়েছে। Termux-এ প্রসেস থাকলে সেটি ৬০ সেকেন্ডের timeout-এ নিজে থামবে।"
    }

    fun save(project: String) {
        if (code.value.isBlank()) return
        val f = a.projects.save(project, fileName(), code.value)
        saved.value = a.projects.list()
        log.value = "সংরক্ষিত: ${f.absolutePath}"
    }

    private fun jsWrap(c: String): String = """<!doctype html><meta charset="utf-8"><body style="font-family:monospace;background:#111;color:#eee"><pre id="o"></pre><script>
const o=document.getElementById('o');
const w=(...a)=>{o.textContent+=a.map(x=>typeof x==='object'?JSON.stringify(x):String(x)).join(' ')+'\n'};
console.log=w;console.error=(...a)=>w('ERROR:',...a);window.onerror=(m,s,l)=>w('ERROR:',m,'line',l);
try{
$c
}catch(e){w('ERROR:',e.message)}
</script>"""
}
