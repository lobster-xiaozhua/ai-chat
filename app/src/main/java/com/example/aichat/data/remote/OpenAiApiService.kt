package com.example.aichat.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject

/**
 * OpenAI 兼容 API 服务 —— 直接使用 OkHttp，无需 Retrofit
 */
class OpenAiApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    /**
     * 发起流式 Chat Completion 请求
     * @return OkHttp Response（调用方负责关闭 response body）
     */
    fun streamChatCompletion(url: String, auth: String, requestBody: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute()
    }
}
