package com.example.aichat.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = true
)

@Serializable
data class RequestMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val index: Int,
    val delta: Delta? = null,
    val finish_reason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)
