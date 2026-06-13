package com.example.aichat.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/* ============================================================
 * 文本嵌入 (Text Embeddings) 请求 / 响应
 * 兼容 OpenAI / NVIDIA NIM 规范
 * ========================================================== */

@Serializable
data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
    @SerialName("encoding_format") val encodingFormat: String = "float"
)

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingItem> = emptyList(),
    val model: String? = null,
    val usage: EmbeddingUsage? = null
)

@Serializable
data class EmbeddingItem(
    val index: Int,
    val embedding: List<Float>
)

@Serializable
data class EmbeddingUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
