package com.example.aichat.data.remote

import com.example.aichat.data.remote.dto.EmbeddingRequest
import com.example.aichat.data.remote.dto.EmbeddingResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * 文本嵌入 API —— 直接使用 OkHttp
 */
class EmbeddingsApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun embeddings(url: String, auth: String, body: EmbeddingRequest): EmbeddingResponse {
        val jsonBody = json.encodeToString(EmbeddingRequest.serializer(), body)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("嵌入请求失败: ${response.code}")
        }
        val responseBody = response.body?.string() ?: throw Exception("响应为空")
        return json.decodeFromString<EmbeddingResponse>(responseBody)
    }
}
