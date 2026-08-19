package com.deepseek.lzjc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.deepseek.lzjc.data.api.ChatCompletionRequest
import com.deepseek.lzjc.data.api.ChatMessage
import com.deepseek.lzjc.data.api.DeepSeekApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: DeepSeekApi,
    private val dataStore: DataStore<Preferences>
) {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String = "deepseek-v4-flash"
    ): Result<ChatMessage> {
        val key = dataStore.data.first()[UsageRepository.KEY_API_KEY] ?: ""
        if (key.isBlank()) {
            return Result.failure(Exception("API Key not set"))
        }

        return try {
            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                stream = false
            )
            val response = api.chatCompletion("Bearer $key", request)
            val reply = response.choices.firstOrNull()?.message
                ?: return Result.failure(Exception("No response from model"))
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
