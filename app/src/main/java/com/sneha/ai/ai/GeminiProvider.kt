package com.sneha.ai.ai

import com.sneha.ai.data.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val GEMINI_KEY_NAME = "gemini_api_key"

class GeminiProvider(private val keys: SecureKeyStore) : AiProvider {
    override val id = "gemini"
    override val name = "Google Gemini"

    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun key(): String =
        keys.load(GEMINI_KEY_NAME)?.takeIf { it.isNotBlank() } ?: throw AiException(AiError.NO_KEY, "no key")

    private fun exec(req: Request): String {
        return try {
            client.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    b
                } else {
                    val msg = try { JSONObject(b).getJSONObject("error").getString("message") } catch (e: Exception) { "HTTP ${r.code}" }
                    throw when (r.code) {
                        400 -> if (msg.contains("API key", true)) AiException(AiError.INVALID_KEY, msg) else AiException(AiError.BAD_REQUEST, msg)
                        401, 403 -> AiException(AiError.INVALID_KEY, msg)
                        404 -> AiException(AiError.BAD_REQUEST, "মডেল পাওয়া যায়নি ($msg) — Settings > AI Brain থেকে অন্য মডেল বাছুন")
                        429 -> AiException(AiError.QUOTA, msg)
                        in 500..599 -> AiException(AiError.SERVER, msg)
                        else -> AiException(AiError.OTHER, msg)
                    }
                }
            }
        } catch (e: IOException) {
            throw AiException(AiError.NETWORK, e.message ?: "IO")
        }
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", key()).build()
            val arr = JSONObject(exec(req)).optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { m ->
                    val g = m.optJSONArray("supportedGenerationMethods")
                    g != null && (0 until g.length()).any { g.getString(it) == "generateContent" }
                }
                .map { it.getString("name").removePrefix("models/") }
                .filter { it.startsWith("gemini") }
                .sorted()
        }
    }

    override suspend fun generate(system: String, history: List<ChatMsg>, cfg: GenConfig, json: Boolean): Result<AiReply> =
        withContext(Dispatchers.IO) {
            runCatching {
                try {
                    call(system, history, cfg, json)
                } catch (e: AiException) {
                    // কিছু নতুন মডেল temperature গ্রহণ করে না — একবার বাদ দিয়ে আবার চেষ্টা
                    if (e.kind == AiError.BAD_REQUEST && cfg.temperature != null && (e.message ?: "").contains("temperature", true)) {
                        call(system, history, cfg.copy(temperature = null), json)
                    } else throw e
                }
            }
        }

    private fun call(system: String, history: List<ChatMsg>, cfg: GenConfig, json: Boolean): AiReply {
        val msgs = history.dropWhile { it.role != "user" }
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
            throw AiException(AiError.BLOCKED, "উত্তর ব্লক হয়েছে $br".trim())
        }
        val c0 = cands.getJSONObject(0)
        val parts = c0.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
        }
        if (text.isBlank()) {
            throw AiException(AiError.BLOCKED, "খালি উত্তর (finishReason=${c0.optString("finishReason")}) — সর্বোচ্চ টোকেন বাড়িয়ে দেখুন")
        }
        val u = resp.optJSONObject("usageMetadata")
        return AiReply(text, u?.optInt("promptTokenCount") ?: 0, u?.optInt("candidatesTokenCount") ?: 0)
    }
}
