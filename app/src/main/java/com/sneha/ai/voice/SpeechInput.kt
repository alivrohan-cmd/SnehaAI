package com.sneha.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null

    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onLevel: (Float) -> Unit,
    ) {
        release()
        if (!available()) {
            onError("এই ফোনে স্পিচ রিকগনিশন সার্ভিস নেই (Google অ্যাপ/Speech Services ইনস্টল করুন)")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                release()
                onError(
                    when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কিছু শুনতে পাইনি, আবার বলুন"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "স্পিচ রিকগনিশনে নেটওয়ার্ক সমস্যা"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন অনুমতি নেই"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "রিকগনাইজার ব্যস্ত, একটু পরে চেষ্টা করুন"
                        12, 13 -> "এই ভাষা এই ফোনের স্পিচ রিকগনিশনে ডাউনলোড করা নেই"
                        else -> "স্পিচ রিকগনিশন ত্রুটি ($code)"
                    }
                )
            }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                release()
                if (t.isBlank()) onError("কিছু শুনতে পাইনি, আবার বলুন") else onFinal(t)
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

    fun stop() { rec?.stopListening() }

    fun release() {
        rec?.destroy()
        rec = null
    }
}
