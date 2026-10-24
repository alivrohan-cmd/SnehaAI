package com.sneha.ai.ai

data class ChatMsg(
    val role: String,
    val text: String,
    val ts: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val isError: Boolean = false,
    val retryPrompt: String? = null
)

data class GenConfig(
    val model: String,
    val temperature: Float?,
    val maxTokens: Int,
    val fallbackModel: String? = null,
    val autoFallback: Boolean = true,
    val maxRetries: Int = 3
)

data class AiReply(
    val text: String,
    val promptTokens: Int,
    val outputTokens: Int,
    val modelUsed: String = "",
    val usedFallback: Boolean = false
)

enum class AiError(val retryable: Boolean) {
    NO_KEY(false),
    INVALID_KEY(false),
    QUOTA(false),
    RATE_LIMIT(true),
    HIGH_DEMAND(true),
    TIMEOUT(true),
    NETWORK(true),
    MODEL_NOT_FOUND(false),
    SERVER(true),
    BAD_REQUEST(false),
    BLOCKED(false),
    OTHER(false)
}

class AiException(
    val kind: AiError,
    message: String,
    val httpCode: Int? = null,
    val rawStatus: String? = null
) : Exception(message) {

    val isRetryable: Boolean get() = kind.retryable

    fun userMessage(): String = when (kind) {
        AiError.NO_KEY ->
            "Gemini API key সেট করা নেই। Settings > AI Brain-এ গিয়ে আপনার API key দিন।"
        AiError.INVALID_KEY ->
            "API key সঠিক নয় বা ব্লক/অনুমতিহীন। Google AI Studio থেকে নতুন API key নিয়ে Settings > AI Brain-এ দিন।"
        AiError.QUOTA ->
            "আপনার API অ্যাকাউন্টের দৈনিক কোটা বা বিলিং লিমিট শেষ হয়েছে। কিছুক্ষণ পরে চেষ্টা করুন বা অন্য মডেল বেছে নিন।"
        AiError.RATE_LIMIT ->
            "অল্প সময়ে অনেকগুলো অনুরোধ পাঠানো হয়েছে (HTTP 429)। কয়েক সেকেন্ড অপেক্ষা করে 'আবার চেষ্টা করুন' চাপুন।"
        AiError.HIGH_DEMAND ->
            "এই মডেলে এখন অতিরিক্ত চাপ চলছে (HTTP 503 High Demand)। একটু অপেক্ষা করে আবার চেষ্টা করুন বা Settings থেকে হালকা মডেল বেছে নিন।"
        AiError.TIMEOUT ->
            "Gemini সার্ভার থেকে উত্তর আসতে বেশি সময় লাগছে (Timeout)। আপনার ইন্টারনেট দেখে আবার চেষ্টা করুন।"
        AiError.NETWORK ->
            "ইন্টারনেট সংযোগে সমস্যা হয়েছে। ডেটা বা Wi-Fi চালু আছে কিনা দেখে আবার চেষ্টা করুন।"
        AiError.MODEL_NOT_FOUND ->
            "নির্বাচিত মডেলটি আপনার API key-তে পাওয়া যায়নি (HTTP 404)। Settings > AI Brain-এ গিয়ে 'Test Connection' চাপুন ও তালিকা থেকে মডেল বাছুন।"
        AiError.SERVER ->
            "Gemini সার্ভারে সাময়িক ত্রুটি হয়েছে (HTTP ${httpCode ?: 500})। একটু পরে আবার চেষ্টা করুন।"
        AiError.BAD_REQUEST ->
            "অনুরোধে ত্রুটি (HTTP 400): ${message ?: "অবৈধ প্যারামিটার"}"
        AiError.BLOCKED ->
            message ?: "নিরাপত্তা নীতিমালার কারণে উত্তর ব্লক হয়েছে বা খালি এসেছে।"
        AiError.OTHER ->
            "অপ্রত্যাশিত ত্রুটি: ${message ?: "অজানা সমস্যা"}"
    }
}

interface AiProvider {
    val id: String
    val name: String
    suspend fun listModels(): Result<List<String>>
    suspend fun generate(
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig,
        json: Boolean
    ): Result<AiReply> = generate(system, history, cfg, json, null)

    suspend fun generate(
        system: String,
        history: List<ChatMsg>,
        cfg: GenConfig,
        json: Boolean,
        onStatus: ((String) -> Unit)?
    ): Result<AiReply>
}
