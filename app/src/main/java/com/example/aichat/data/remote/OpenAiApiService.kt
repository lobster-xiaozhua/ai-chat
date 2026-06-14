package com.example.aichat.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.Call

interface OpenAiApiService {

    @POST
    @Streaming
    fun streamChatCompletion(
        @retrofit2.http.Url url: String,
        @Header("Authorization") auth: String,
        @Body body: com.example.aichat.data.remote.dto.ChatCompletionRequest
    ): Call<ResponseBody>
}
