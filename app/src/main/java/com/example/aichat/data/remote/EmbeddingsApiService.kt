package com.example.aichat.data.remote

import com.example.aichat.data.remote.dto.EmbeddingRequest
import com.example.aichat.data.remote.dto.EmbeddingResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * 文本嵌入 API —— 与 Chat Completion 同样的 OpenAI 风格调用方式
 *
 * 真实示例：
 *   POST https://integrate.api.nvidia.com/v1/embeddings
 *   Authorization: Bearer $API_KEY
 *   Body: { "model": "nvidia/nv-embedqa-e5-v5", "input": ["hello"] }
 */
interface EmbeddingsApiService {

    @POST
    fun embeddings(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Body body: EmbeddingRequest
    ): retrofit2.Call<EmbeddingResponse>
}
