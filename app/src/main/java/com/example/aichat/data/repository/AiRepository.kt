package com.example.aichat.data.repository

import com.example.aichat.data.remote.OpenAiApiService
import com.example.aichat.data.remote.dto.ChatCompletionChunk
import com.example.aichat.data.remote.dto.ChatCompletionRequest
import com.example.aichat.data.remote.dto.RequestMessage
import com.example.aichat.data.remote.sse.EventSourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val apiService: OpenAiApiService,
    private val settingsRepository: SettingsRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun streamChat(
        messages: List<Pair<String, String>>,
        systemPrompt: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        temperature: Double
    ): Flow<String> = flow {
        val requestMessages = mutableListOf<RequestMessage>()
        if (systemPrompt.isNotBlank()) {
            requestMessages.add(RequestMessage("system", systemPrompt))
        }
        requestMessages.addAll(messages.map { (role, content) ->
            RequestMessage(role, content)
        })

        val request = ChatCompletionRequest(
            model = model,
            messages = requestMessages,
            temperature = temperature,
            stream = true
        )

        val response = apiService.streamChatCompletion(
            url = "$baseUrl/chat/completions",
            auth = "Bearer $apiKey",
            body = request
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw IllegalStateException("API 调用失败 (${response.code()}): $errorBody")
        }

        val body = response.body() ?: throw IllegalStateException("响应为空")
        val parser = EventSourceParser()
        parser.parse(body.source()).collect { data ->
            val chunk = json.decodeFromString(ChatCompletionChunk.serializer(), data)
            val content = chunk.choices.firstOrNull()?.delta?.content ?: ""
            if (content.isNotEmpty()) emit(content)
        }
    }.flowOn(Dispatchers.IO)
}
