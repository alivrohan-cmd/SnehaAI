package com.sneha.ai.ai

import com.sneha.ai.data.Prefs
import com.sneha.ai.data.SecureKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

const val GATEWAY_KEY_NAME = "gateway_api_key"

class GatewayProvider(
    private val keys: SecureKeyStore,
    private val prefs: Prefs
) : AiProvider {
    override val id = "gateway"
    override val name = "Custom Gateway"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun apiKey(): String {
        val gwKey = keys.load(GATEWAY_KEY_NAME)?.trim().orEmpty()
        if (gwKey.isNotBlank()) return gwKey
        val gemKey = keys.load(GEMINI_KEY_NAME)?.trim().orEmpty()
        if (gemKey.isNotBlank()) return gemKey
        throw AiException(AiError.NO_KEY, "Gateway API Key সেট করা নেই। Settings > AI Brain-এ গিয়ে আপনার sk-... টোকেন দিন।")
    }

    private fun normalizeBaseUrl(raw: String): String {
        var u = raw.trim().removeSuffix("/")
        if (u.isBlank()) u = "https://tabitoken.com"
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        u = u.removeSuffix("/v1/chat/completions")
            .removeSuffix("/v1/messages")
            .removeSuffix("/v1/models")
            .removeSuffix("/v1")
        return u
    }

    private fun Request.Builder.applyAuth(key: String, scheme: String): Request.Builder {
        return when (scheme.lowercase()) {
            "x-api-key" -> this
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
            else -> this
                .header("Authorization", "Bearer $key")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
        }
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val s = prefs.current()
            val base = normalizeBaseUrl(s.gatewayBaseUrl)
            val key = apiKey()
            val req = Request.Builder()
                .url("$base/v1/models")
                .applyAuth(key, s.gatewayAuthScheme)
                .get()
                .build()

            val list = mutableListOf<String>()
            try {
                client.newCall(req).execute().use { r ->
                    val b = r.body?.string().orEmpty()
                    if (r.isSuccessful) {
                        val obj = JSONObject(b)
                        val arr = obj.optJSONArray("data") ?: obj.optJSONArray("models") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i)
                            val mId = item?.optString("id")?.takeIf { it.isNotBlank() }
                                ?: item?.optString("name")?.removePrefix("models/")?.takeIf { it.isNotBlank() }
                            if (mId != null) list.add(mId)
                        }
                    } else if (r.code == 401 || r.code == 403) {
                        throw AiException(AiError.INVALID_KEY, "Gateway API Key সঠিক নয় (HTTP ${r.code})")
                    }
                }
            } catch (e: AiException) {
                throw e
            } catch (_: Exception) {}

            if (list.isEmpty()) {
                list.addAll(
                    listOf(
                        "claude-opus-4-6",
                        "claude-sonnet-4-6",
                        "claude-3-7-sonnet-20250219",
                        "claude-3-5-sonnet-20241022",
                        "gpt-4o",
                        "gpt-4o-mini",
                        "deepseek-chat",
                        "gemini-2.5-pro",
                        "gemini-2.5-flash"
                    )
                )
            }
            list.distinct().sorted()
        }
    }

    override suspend fun generate(
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig,
        json: Boolean,
        onStatus: ((String) -> Unit)?
    ): Result<AiReply> = withContext(Dispatchers.IO) {
        runCatching {
            val s = prefs.current()
            val base = normalizeBaseUrl(s.gatewayBaseUrl)
            val key = apiKey()
            val maxRetries = cfg.maxRetries.coerceIn(0, 5)
            val backoff = longArrayOf(1200L, 2500L, 4500L, 8000L, 12000L)
            var attempt = 0

            while (true) {
                try {
                    return@runCatching when (s.gatewayFormat) {
                        "anthropic" -> callAnthropic(base, key, s.gatewayAuthScheme, system, history, cfg)
                        "openai" -> callOpenAi(base, key, s.gatewayAuthScheme, system, history, cfg)
                        else -> {
                            try {
                                callOpenAi(base, key, s.gatewayAuthScheme, system, history, cfg)
                            } catch (e: AiException) {
                                if (e.httpCode == 404 || e.httpCode == 400 || e.httpCode == 405) {
                                    callAnthropic(base, key, s.gatewayAuthScheme, system, history, cfg)
                                } else throw e
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiException) {
                    if (!e.isRetryable || attempt >= maxRetries) throw e
                    val waitMs = backoff[attempt.coerceAtMost(backoff.lastIndex)] + Random.nextLong(150L, 500L)
                    attempt++
                    onStatus?.invoke("গেটওয়ে ব্যস্ত — ${"%.1f".format(waitMs / 1000.0)}s পর আবার চেষ্টা করছি ($attempt/$maxRetries)…")
                    delay(waitMs)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw AiException(AiError.OTHER, "Unreachable")
        }
    }

    private fun callOpenAi(
        base: String,
        key: String,
        scheme: String,
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig
    ): AiReply {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        history.filter { (it.role == "user" || it.role == "model") && !it.isError && it.text.isNotBlank() }
            .forEach {
                msgs.put(
                    JSONObject()
                        .put("role", if (it.role == "user") "user" else "assistant")
                        .put("content", it.text)
                )
            }

        val body = JSONObject()
            .put("model", cfg.model.ifBlank { "claude-3-5-sonnet-20241022" })
            .put("messages", msgs)
            .put("max_tokens", cfg.maxTokens)
        cfg.temperature?.let { body.put("temperature", it.toDouble()) }

        val req = Request.Builder()
            .url("$base/v1/chat/completions")
            .applyAuth(key, scheme)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = JSONObject(execHttp(req))
        val choices = resp.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw AiException(AiError.BLOCKED, "গেটওয়ে থেকে কোনো উত্তর পাওয়া যায়নি")
        }
        val msgObj = choices.getJSONObject(0).optJSONObject("message")
        val text = msgObj?.optString("content").orEmpty().trim()
        if (text.isBlank()) throw AiException(AiError.BLOCKED, "খালি উত্তর এসেছে")

        val u = resp.optJSONObject("usage")
        return AiReply(
            text = text,
            promptTokens = u?.optInt("prompt_tokens") ?: 0,
            outputTokens = u?.optInt("completion_tokens") ?: 0,
            modelUsed = cfg.model,
            usedFallback = false
        )
    }

    private fun callAnthropic(
        base: String,
        key: String,
        scheme: String,
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig
    ): AiReply {
        val msgs = JSONArray()
        history.filter { (it.role == "user" || it.role == "model") && !it.isError && it.text.isNotBlank() }
            .dropWhile { it.role != "user" }
            .forEach {
                msgs.put(
                    JSONObject()
                        .put("role", if (it.role == "user") "user" else "assistant")
                        .put("content", it.text)
                )
            }

        val body = JSONObject()
            .put("model", cfg.model.ifBlank { "claude-3-5-sonnet-20241022" })
            .put("system", system)
            .put("messages", msgs)
            .put("max_tokens", cfg.maxTokens)
        cfg.temperature?.let { body.put("temperature", it.toDouble()) }

        val req = Request.Builder()
            .url("$base/v1/messages")
            .applyAuth(key, scheme)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = JSONObject(execHttp(req))
        val contentArr = resp.optJSONArray("content")
        val text = buildString {
            if (contentArr != null) {
                for (i in 0 until contentArr.length()) {
                    val part = contentArr.getJSONObject(i)
                    if (part.optString("type") != "thinking") {
                        append(part.optString("text"))
                    }
                }
            }
        }.trim()
        if (text.isBlank()) throw AiException(AiError.BLOCKED, "খালি উত্তর এসেছে")

        val u = resp.optJSONObject("usage")
        return AiReply(
            text = text,
            promptTokens = u?.optInt("input_tokens") ?: 0,
            outputTokens = u?.optInt("output_tokens") ?: 0,
            modelUsed = cfg.model,
            usedFallback = false
        )
    }

    private fun execHttp(req: Request): String {
        return try {
            client.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    b
                } else {
                    val errMsg = try {
                        val root = JSONObject(b)
                        root.optJSONObject("error")?.optString("message")
                            ?: root.optString("message").takeIf { it.isNotBlank() }
                            ?: "HTTP ${r.code}"
                    } catch (_: Exception) {
                        "HTTP ${r.code}"
                    }
                    throw when (r.code) {
                        401, 403 -> AiException(AiError.INVALID_KEY, errMsg, r.code)
                        404 -> AiException(AiError.MODEL_NOT_FOUND, errMsg, r.code)
                        429 -> AiException(AiError.RATE_LIMIT, errMsg, r.code)
                        503, 529 -> AiException(AiError.HIGH_DEMAND, errMsg, r.code)
                        in 500..599 -> AiException(AiError.SERVER, errMsg, r.code)
                        else -> AiException(AiError.BAD_REQUEST, errMsg, r.code)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            throw AiException(AiError.TIMEOUT, e.message ?: "Timeout")
        } catch (e: IOException) {
            throw AiException(AiError.NETWORK, e.message ?: "Network error")
        }
    }
}
