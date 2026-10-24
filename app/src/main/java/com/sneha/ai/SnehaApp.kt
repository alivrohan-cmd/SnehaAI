package com.sneha.ai

import android.app.Application
import com.sneha.ai.ai.AiProvider
import com.sneha.ai.ai.GatewayProvider
import com.sneha.ai.ai.GeminiProvider
import com.sneha.ai.apps.AppLauncher
import com.sneha.ai.automation.ActionExecutor
import com.sneha.ai.coding.ProjectStore
import com.sneha.ai.coding.TermuxRunner
import com.sneha.ai.comms.CommsHelper
import com.sneha.ai.data.ChatStore
import com.sneha.ai.data.MemoryStore
import com.sneha.ai.data.Prefs
import com.sneha.ai.data.SecureKeyStore
import com.sneha.ai.voice.SpeechOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SnehaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val keys by lazy { SecureKeyStore(this) }
    val memory by lazy { MemoryStore(this) }
    val chat by lazy { ChatStore(this) }
    val launcher by lazy { AppLauncher(this) }
    val comms by lazy { CommsHelper(this) }
    val projects by lazy { ProjectStore(this) }
    val termux by lazy { TermuxRunner(this) }
    val tts by lazy { SpeechOutput(this) }
    val executor by lazy { ActionExecutor(this) }

    val gemini by lazy { GeminiProvider(keys) }
    val gateway by lazy { GatewayProvider(keys, prefs) }

    fun activeProvider(mode: String): AiProvider = if (mode == "gateway") gateway else gemini
    val provider: AiProvider get() = gemini
}
