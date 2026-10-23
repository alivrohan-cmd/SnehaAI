package com.sneha.ai.ai

data class ChatMsg(val role: String, val text: String, val ts: Long = System.currentTimeMillis())

/** temperature == null হলে অনুরোধে পাঠানো হয় না (নতুন Gemini মডেলে sampling প্যারামিটার বাদ দিতে হয়)। */
data class GenConfig(val model: String, val temperature: Float?, val maxTokens: Int)

data class AiReply(val text: String, val promptTokens: Int, val outputTokens: Int)

enum class AiError { NO_KEY, INVALID_KEY, QUOTA, NETWORK, SERVER, BAD_REQUEST, BLOCKED, OTHER }

class AiException(val kind: AiError, message: String) : Exception(message) {
    fun userMessage(): String = when (kind) {
        AiError.NO_KEY -> "Gemini API key সেট করা নেই। Settings > AI Brain-এ দিন।"
        AiError.INVALID_KEY -> "API key সঠিক নয় বা অনুমতি নেই: $message"
        AiError.QUOTA -> "কোটা/রেট লিমিট শেষ। কিছুক্ষণ পরে চেষ্টা করুন বা অন্য মডেল বেছে নিন।"
        AiError.NETWORK -> "ইন্টারনেট সংযোগে সমস্যা: $message"
        AiError.SERVER -> "Gemini সার্ভারে সমস্যা, পরে চেষ্টা করুন: $message"
        AiError.BAD_REQUEST -> "অনুরোধ ত্রুটি: $message"
        AiError.BLOCKED -> message ?: "উত্তর পাওয়া যায়নি"
        AiError.OTHER -> message ?: "অজানা ত্রুটি"
    }
}

/** নতুন প্রোভাইডার (Claude, OpenAI, লোকাল মডেল) যোগ করতে শুধু এই ইন্টারফেস ইমপ্লিমেন্ট করতে হবে। */
interface AiProvider {
    val id: String
    val name: String
    suspend fun listModels(): Result<List<String>>
    suspend fun generate(system: String, history: List<ChatMsg>, cfg: GenConfig, json: Boolean): Result<AiReply>
}
