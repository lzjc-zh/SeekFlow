package com.deepseek.lzjc.data.repository

import com.deepseek.lzjc.data.api.ChatCompletionRequest
import com.deepseek.lzjc.data.api.ChatCompletionResponse
import com.deepseek.lzjc.data.api.ChatMessage
import com.deepseek.lzjc.data.provider.ProviderConfig
import com.deepseek.lzjc.data.provider.ProviderStore
import com.deepseek.lzjc.data.provider.ProviderType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天仓库：向当前选中供应商的 OpenAI 兼容 chat/completions 端点发送消息。
 * DeepSeek 官方与中转站均使用标准的 /chat/completions 接口。
 */
@Singleton
class ChatRepository @Inject constructor(
    private val providerStore: ProviderStore
) {

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        modelOverride: String? = null,
        providerId: String? = null
    ): Result<ChatMessage> {
        val providers = providerStore.getEnabledProviders()
        val config = if (providerId != null) {
            providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
        } else {
            providers.firstOrNull { it.apiKey.isNotBlank() }
        }

        if (config == null) {
            return Result.failure(Exception("No provider configured"))
        }
        if (config.apiKey.isBlank()) {
            return Result.failure(Exception("API Key not set for ${config.name}"))
        }

        val model = modelOverride?.takeIf { it.isNotBlank() }
            ?: config.chatModel.takeIf { it.isNotBlank() }
            ?: defaultModel(config.type)

        return try {
            val api = Retrofit.Builder()
                .baseUrl(normalizeChatBaseUrl(config))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApi::class.java)

            val response = api.chatCompletion(
                "Bearer ${config.apiKey}",
                ChatCompletionRequest(model = model, messages = messages, stream = false)
            )
            val reply = response.choices.firstOrNull()?.message
                ?: return Result.failure(Exception("No response from model"))
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun defaultModel(type: ProviderType): String =
        if (type == ProviderType.DEEPSEEK_OFFICIAL) "deepseek-chat" else "gpt-4o-mini"

    /**
     * 构造 chat/completions 的 baseUrl：
     * - DeepSeek 官方：https://api.deepseek.com/（接口为 /chat/completions）
     * - 中转站：若 baseUrl 已含 /v1 则直接使用，否则补 /v1/
     */
    private fun normalizeChatBaseUrl(config: ProviderConfig): String {
        val url = config.normalizedBaseUrl
        return when {
            url.endsWith("/v1/") -> url
            url.endsWith("/v1") -> "$url/"
            config.type == ProviderType.OPENAI_COMPATIBLE -> "${url}v1/"
            else -> url
        }
    }
}

/** 聊天接口（OpenAI 兼容标准） */
interface ChatApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
