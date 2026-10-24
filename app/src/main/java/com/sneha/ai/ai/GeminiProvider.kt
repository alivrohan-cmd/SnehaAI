package com.sneha.ai.ai

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
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

const val GEMINI_KEY_NAME = "gemini_api_key"

class GeminiProvider(private val keys: SecureKeyStore) : AiProvider {
    override val id = "gemini"
    override val name = "Google Gemini"

    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var verifiedModelsCache: List<String> = emptyList()

    private fun key(): String =
        keys.load(GEMINI_KEY_NAME)?.takeIf { it.isNotBlank() }
            ?: throw AiException(AiError.NO_KEY, "API key missing")

    private fun exec(req: Request): String {
        return try {
            client.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    b
                } else {
                    val errObj = try { JSONObject(b).optJSONObject("error") } catch (_: Exception) { null }
                    val msg = errObj?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${r.code}"
                    val status = errObj?.optString("status").orEmpty()
                    throw mapHttpError(r.code, status, msg)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw AiException(AiError.TIMEOUT, e.message ?: "Socket timeout")
        } catch (e: InterruptedIOException) {
            throw AiException(AiError.TIMEOUT, e.message ?: "Request timed out")
        } catch (e: IOException) {
            throw AiException(AiError.NETWORK, e.message ?: "Network error")
        }
    }

    private fun mapHttpError(code: Int, status: String, msg: String): AiException {
        val lower = msg.lowercase()
        return when (code) {
            400 -> {
                if (lower.contains("api key") || lower.contains("api_key_invalid") || lower.contains("leaked")) {
                    AiException(AiError.INVALID_KEY, msg, code, status)
                } else {
                    AiException(AiError.BAD_REQUEST, msg, code, status)
                }
            }
            401, 403 -> AiException(AiError.INVALID_KEY, msg, code, status)
            404 -> AiException(AiError.MODEL_NOT_FOUND, msg, code, status)
            408 -> AiException(AiError.TIMEOUT, msg, code, status)
            429 -> {
                val isHardQuota = lower.contains("billing") ||
                    (lower.contains("quota exceeded") && lower.contains("per day")) ||
                    lower.contains("plan and billing")
                if (isHardQuota) AiException(AiError.QUOTA, msg, code, status)
                else AiException(AiError.RATE_LIMIT, msg, code, status)
            }
            503 -> AiException(AiError.HIGH_DEMAND, msg, code, status)
            in 500..599 -> {
                if (lower.contains("high demand") || lower.contains("overloaded") || status == "UNAVAILABLE") {
                    AiException(AiError.HIGH_DEMAND, msg, code, status)
                } else {
                    AiException(AiError.SERVER, msg, code, status)
                }
            }
            else -> AiException(AiError.OTHER, msg, code, status)
        }
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$base/models?pageSize=200")
                .header("x-goog-api-key", key())
                .build()
            val arr = JSONObject(exec(req)).optJSONArray("models") ?: JSONArray()
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { m ->
                    val g = m.optJSONArray("supportedGenerationMethods")
                    g != null && (0 until g.length()).any { g.getString(it) == "generateContent" }
                }
                .map { it.getString("name").removePrefix("models/") }
                .filter { name ->
                    name.startsWith("gemini") &&
                        listOf("embedding", "imagen", "veo", "aqa", "bisheng", "robotics", "computer-use").none { name.contains(it, true) }
                }
                .distinct()
                .sorted()
            if (list.isNotEmpty()) {
                verifiedModelsCache = list
            }
            list
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
            val primaryModel = cfg.model.trim().ifBlank { "gemini-3.6-flash" }
            try {
                executeWithRetry(system, history, cfg.copy(model = primaryModel), json, onStatus)
            } catch (primaryErr: AiException) {
                val shouldFallback = cfg.autoFallback && (
                    primaryErr.kind == AiError.HIGH_DEMAND ||
                        primaryErr.kind == AiError.RATE_LIMIT ||
                        primaryErr.kind == AiError.MODEL_NOT_FOUND ||
                        primaryErr.kind == AiError.SERVER
                    )
                if (!shouldFallback) throw primaryErr

                val fallback = selectVerifiedFallbackModel(primaryModel, cfg.fallbackModel)
                    ?: throw primaryErr

                onStatus?.invoke("প্রাথমিক মডেলে চাপ বেশি — বিকল্প মডেল ($fallback) দিয়ে চেষ্টা করছি…")
                val reply = executeWithRetry(
                    system = system,
                    history = history,
                    cfg = cfg.copy(model = fallback, maxRetries = 1),
                    json = json,
                    onStatus = onStatus
                )
                reply.copy(modelUsed = fallback, usedFallback = true)
            }
        }
    }

    private suspend fun selectVerifiedFallbackModel(failedModel: String, preferredFallback: String?): String? {
        var available = verifiedModelsCache
        if (available.isEmpty()) {
            available = listModels().getOrNull().orEmpty()
        }
        val textModels = available.filter { m ->
            m != failedModel &&
                listOf("tts", "image", "live", "transcribe", "audio", "embedding", "omni").none { m.contains(it, true) }
        }
        if (textModels.isEmpty()) return null

        val pref = preferredFallback?.trim()?.takeIf { it.isNotBlank() && it != failedModel }
        if (pref != null && pref in textModels) return pref

        val priorityOrder = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash"
        )
        for (candidate in priorityOrder) {
            if (candidate != failedModel && candidate in textModels) return candidate
        }

        return textModels.firstOrNull { it.contains("flash", true) } ?: textModels.firstOrNull()
    }

    private suspend fun executeWithRetry(
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig,
        json: Boolean,
        onStatus: ((String) -> Unit)?
    ): AiReply {
        var currentCfg = cfg
        val maxRetries = cfg.maxRetries.coerceIn(0, 5)
        var attempt = 0
        val backoffBaseMs = longArrayOf(1400L, 2800L, 5500L, 9000L, 14000L)

        while (true) {
            try {
                return callSingle(system, history, currentCfg, json)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                if (e.kind == AiError.BAD_REQUEST &&
                    currentCfg.temperature != null &&
                    (e.message ?: "").contains("temperature", ignoreCase = true)
                ) {
                    currentCfg = currentCfg.copy(temperature = null)
                    continue
                }

                if (!e.isRetryable || attempt >= maxRetries) {
                    throw e
                }

                val baseDelay = backoffBaseMs[attempt.coerceAtMost(backoffBaseMs.lastIndex)]
                val jitter = Random.nextLong(150L, 650L)
                val waitMs = baseDelay + jitter
                attempt++

                val reasonBn = when (e.kind) {
                    AiError.HIGH_DEMAND -> "সার্ভারে অতিরিক্ত চাপ (503)"
                    AiError.RATE_LIMIT -> "রেট লিমিট (429)"
                    AiError.TIMEOUT -> "টাইমআউট"
                    AiError.NETWORK -> "নেটওয়ার্ক সমস্যা"
                    else -> "সার্ভার ব্যস্ত"
                }
                val sec = "%.1f".format(waitMs / 1000.0)
                onStatus?.invoke("$reasonBn —${sec} সেকেন্ড পর আবার চেষ্টা করছি ($attempt/$maxRetries)…")
                delay(waitMs)
            }
        }
    }

    private fun callSingle(system: String, history: List<ChatMsg>, cfg: GenConfig, json: Boolean): AiReply {
        val msgs = history
            .filter { (it.role == "user" || it.role == "model") && !it.isError && it.text.isNotBlank() }
            .dropWhile { it.role != "user" }
        val contents = JSONArray()
        msgs.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        val gc = JSONObject().put("maxOutputTokens", cfg.maxTokens)
        cfg.temperature?.let { gc.put("temperature", it.toDouble()) }
        if (json) gc.put("responseMimeType", "application/json")

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gc)

        val req = Request.Builder()
            .url("$base/models/${cfg.model}:generateContent")
            .header("x-goog-api-key", key())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resp = JSONObject(exec(req))
        val cands = resp.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val br = resp.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw AiException(
                AiError.BLOCKED,
                if (br.isNotBlank()) "নিরাপত্তা ফিল্টারে উত্তর ব্লক হয়েছে ($br)" else "উত্তর পাওয়া যায়নি (কোনো candidate নেই)"
            )
        }
        val c0 = cands.getJSONObject(0)
        val parts = c0.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (!p.optBoolean("thought", false)) {
                        append(p.optString("text"))
                    }
                }
            }
        }
        if (text.isBlank()) {
            val fr = c0.optString("finishReason", "UNKNOWN")
            throw AiException(
                AiError.BLOCKED,
                "খালি উত্তর এসেছে (finishReason=$fr) — Settings > AI Brain থেকে সর্বোচ্চ আউটপুট টোকেন বাড়িয়ে দেখুন।"
            )
        }
        val u = resp.optJSONObject("usageMetadata")
        return AiReply(
            text = text,
            promptTokens = u?.optInt("promptTokenCount") ?: 0,
            outputTokens = u?.optInt("candidatesTokenCount") ?: 0,
            modelUsed = cfg.model,
            usedFallback = false
        )
    }
}
