package com.sneha.ai.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("sneha_prefs")

data class Settings(
    val providerMode: String = "gemini",
    val gatewayBaseUrl: String = "https://tabitoken.com",
    val gatewayAuthScheme: String = "bearer",
    val gatewayFormat: String = "auto",
    val model: String = "gemini-3.6-flash",
    val fallbackModel: String = "gemini-3.5-flash-lite",
    val autoFallback: Boolean = true,
    val maxRetries: Int = 3,
    val useTemperature: Boolean = false,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val lang: String = "bn",
    val speechRate: Float = 1.0f,
    val volume: Float = 1.0f,
    val voiceName: String = "",
    val autoSpeak: Boolean = true,
    val theme: String = "dark",
    val fontScale: Float = 1.0f,
    val reduceMotion: Boolean = false,
    val memoryEnabled: Boolean = true,
    val favorites: Set<String> = emptySet(),
    val termuxEnabled: Boolean = false,
    val notifyAutomation: Boolean = true,
    val reqCount: Int = 0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
)

object K {
    val providerMode = stringPreferencesKey("provider_mode")
    val gatewayBaseUrl = stringPreferencesKey("gateway_base_url")
    val gatewayAuthScheme = stringPreferencesKey("gateway_auth_scheme")
    val gatewayFormat = stringPreferencesKey("gateway_format")
    val model = stringPreferencesKey("model")
    val fallbackModel = stringPreferencesKey("fallback_model")
    val autoFallback = booleanPreferencesKey("auto_fallback")
    val maxRetries = intPreferencesKey("max_retries")
    val useTemp = booleanPreferencesKey("use_temperature")
    val temperature = floatPreferencesKey("temperature")
    val maxTokens = intPreferencesKey("max_tokens")
    val lang = stringPreferencesKey("lang")
    val rate = floatPreferencesKey("rate")
    val volume = floatPreferencesKey("volume")
    val voice = stringPreferencesKey("voice")
    val autoSpeak = booleanPreferencesKey("auto_speak")
    val theme = stringPreferencesKey("theme")
    val fontScale = floatPreferencesKey("font_scale")
    val reduceMotion = booleanPreferencesKey("reduce_motion")
    val memory = booleanPreferencesKey("memory_enabled")
    val favorites = stringSetPreferencesKey("favorites")
    val termux = booleanPreferencesKey("termux_enabled")
    val notify = booleanPreferencesKey("notify_automation")
    val req = intPreferencesKey("req_count")
    val tin = longPreferencesKey("tokens_in")
    val tout = longPreferencesKey("tokens_out")
}

class Prefs(private val ctx: Context) {
    val flow: Flow<Settings> = ctx.ds.data.map { p ->
        val d = Settings()
        Settings(
            providerMode = p[K.providerMode] ?: d.providerMode,
            gatewayBaseUrl = p[K.gatewayBaseUrl] ?: d.gatewayBaseUrl,
            gatewayAuthScheme = p[K.gatewayAuthScheme] ?: d.gatewayAuthScheme,
            gatewayFormat = p[K.gatewayFormat] ?: d.gatewayFormat,
            model = p[K.model] ?: d.model,
            fallbackModel = p[K.fallbackModel] ?: d.fallbackModel,
            autoFallback = p[K.autoFallback] ?: d.autoFallback,
            maxRetries = (p[K.maxRetries] ?: d.maxRetries).coerceIn(0, 5),
            useTemperature = p[K.useTemp] ?: d.useTemperature,
            temperature = p[K.temperature] ?: d.temperature,
            maxTokens = p[K.maxTokens] ?: d.maxTokens,
            lang = p[K.lang] ?: d.lang,
            speechRate = p[K.rate] ?: d.speechRate,
            volume = p[K.volume] ?: d.volume,
            voiceName = p[K.voice] ?: d.voiceName,
            autoSpeak = p[K.autoSpeak] ?: d.autoSpeak,
            theme = p[K.theme] ?: d.theme,
            fontScale = p[K.fontScale] ?: d.fontScale,
            reduceMotion = p[K.reduceMotion] ?: d.reduceMotion,
            memoryEnabled = p[K.memory] ?: d.memoryEnabled,
            favorites = p[K.favorites] ?: d.favorites,
            termuxEnabled = p[K.termux] ?: d.termuxEnabled,
            notifyAutomation = p[K.notify] ?: d.notifyAutomation,
            reqCount = p[K.req] ?: 0,
            tokensIn = p[K.tin] ?: 0L,
            tokensOut = p[K.tout] ?: 0L,
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        ctx.ds.edit { it[key] = value }
    }

    suspend fun addUsage(inTokens: Int, outTokens: Int) {
        ctx.ds.edit {
            it[K.req] = (it[K.req] ?: 0) + 1
            it[K.tin] = (it[K.tin] ?: 0L) + inTokens
            it[K.tout] = (it[K.tout] ?: 0L) + outTokens
        }
    }
}
