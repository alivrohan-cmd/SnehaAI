package com.sneha.ai.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    private val audio: AudioManager? = ctx.getSystemService(AudioManager::class.java)
    private var mutedBeep = false

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    private fun muteBeepSound() {
        if (mutedBeep) return
        try {
            audio?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audio?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            mutedBeep = true
        } catch (_: Exception) {}
    }

    private fun unmuteBeepSound() {
        if (!mutedBeep) return
        try {
            audio?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audio?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: Exception) {}
        mutedBeep = false
    }

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onLevel: (Float) -> Unit,
    ) {
        release()
        if (!available()) {
            onError("এই ফোনে স্পিচ রিকগনিশন সার্ভিস নেই")
            return
        }
        muteBeepSound()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                unmuteBeepSound()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                muteBeepSound()
            }
            override fun onError(code: Int) {
                release()
                unmuteBeepSound()
                onError(
                    when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কিছু শুনতে পাইনি"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক সমস্যা"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন অনুমতি নেই"
                        else -> "ত্রুটি ($code)"
                    }
                )
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                unmuteBeepSound()
                if (t.isBlank()) onError("কিছু শুনতে পাইনি") else onFinal(t)
            }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val tag = if (lang == "bn") "bn-BD" else "en-US"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(i)
    }

    fun stop() {
        muteBeepSound()
        rec?.stopListening()
        unmuteBeepSound()
    }

    fun release() {
        try {
            rec?.destroy()
        } catch (_: Exception) {}
        rec = null
        unmuteBeepSound()
    }
}
