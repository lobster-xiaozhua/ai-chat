package com.example.aichat.data.remote

import com.example.aichat.data.remote.dto.ModelsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * 调用兼容 OpenAI 的 /v1/models 接口，获取当前提供商下的所有可用模型。
 *
 *   GET {baseUrl}/models
 *   Authorization: Bearer {apiKey}
 *
 * 适用于 NVIDIA API、DeepSeek、OpenAI 等所有符合此规范的后端。
 */
interface ModelsApiService {

    @GET
    suspend fun getModels(
        @Url url: String,
        @Header("Authorization") auth: String
    ): ModelsResponse
}
