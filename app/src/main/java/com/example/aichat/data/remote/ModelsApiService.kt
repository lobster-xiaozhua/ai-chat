package com.example.aichat.data.remote

import com.example.aichat.data.remote.dto.ModelsResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * 模型列表 API —— 直接使用 OkHttp
 */
class ModelsApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun getModels(url: String, auth: String): ModelsResponse {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("获取模型列表失败: ${response.code}")
        }
        val body = response.body?.string() ?: throw Exception("响应为空")
        return json.decodeFromString<ModelsResponse>(body)
    }
}
