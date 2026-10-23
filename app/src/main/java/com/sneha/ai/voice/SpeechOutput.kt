package com.sneha.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class SpeechOutput(ctx: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    val speaking = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { speaking.value = true }
                    override fun onDone(id: String?) { speaking.value = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { speaking.value = false }
                    override fun onStop(id: String?, interrupted: Boolean) { speaking.value = false }
                })
            } else error.value = "TextToSpeech শুরু করা যায়নি"
        }
    }

    fun speak(text: String, lang: String, rate: Float, volume: Float, voiceName: String): Boolean {
        val t = tts
        if (t == null || !ready) { error.value = "TTS এখনও প্রস্তুত নয়"; return false }
        val loc = Locale.forLanguageTag(if (lang == "bn") "bn-IN" else "en-IN")
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            error.value = "এই ভাষার TTS ভয়েস ইনস্টল নেই। Settings > ভয়েস > TTS ইঞ্জিন সেটিংস থেকে ডাউনলোড করুন।"
            return false
        }
        if (voiceName.isNotBlank()) {
            try { t.voices?.firstOrNull { it.name == voiceName }?.let { t.voice = it } } catch (_: Exception) {}
        }
        t.setSpeechRate(rate)
        val b = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        error.value = null
        t.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, b, "sneha")
        return true
    }

    fun stop() {
        tts?.stop()
        speaking.value = false
    }

    fun voices(lang: String): List<String> = try {
        tts?.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun shutdown() { tts?.shutdown() }
}
