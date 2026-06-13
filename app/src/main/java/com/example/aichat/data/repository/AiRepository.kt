package com.example.aichat.data.repository

import com.example.aichat.data.remote.OpenAiApiService
import com.example.aichat.data.remote.dto.ChatCompletionRequest
import com.example.aichat.data.remote.dto.ChatCompletionChunk
import com.example.aichat.data.remote.dto.RequestMessage
import com.example.aichat.data.remote.sse.EventSourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分级错误类型 —— 不暴露 API Key / 敏感信息。
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(msg: String = "未授权：请检查 API Key 是否正确") : ApiException(msg)
    class RateLimited(msg: String = "请求过于频繁，请稍后再试") : ApiException(msg)
    class ServerError(code: Int, msg: String = "服务器错误 ($code)") : ApiException(msg)
    class InvalidRequest(msg: String = "请求无效：$msg") : ApiException(msg)
    class Network(cause: Throwable?) : ApiException("网络异常，请检查连接", cause)
    class Unknown(cause: Throwable?) : ApiException("未知错误：${cause?.message}", cause)
}

@Singleton
class AiRepository @Inject constructor(
    private val apiService: OpenAiApiService
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 流式请求对话补全。
     *
     * 取消机制：Flow 被 cancel 时 → 关闭 OkHttp source → 抛 IOException → unwinding。
     * 若在上层调用了 job.cancel()，也会联动取消内部读取循环，HTTP 连接真正中断。
     */
    fun streamChat(
        messages: List<Pair<String, String>>,
        systemPrompt: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        temperature: Double
    ): Flow<String> = flow {

        val requestMessages = mutableListOf<RequestMessage>().apply {
            if (systemPrompt.isNotBlank()) add(RequestMessage("system", systemPrompt))
            addAll(messages.map { (role, content) -> RequestMessage(role, content) })
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = requestMessages,
            temperature = temperature,
            stream = true
        )

        val url = buildChatCompletionsUrl(baseUrl)
        val auth = "Bearer $apiKey"

        val call = apiService.streamChatCompletion(url, auth, request)

        // 注册：协程取消时真正中断 HTTP
        val job = kotlinx.coroutines.currentCoroutineContext()[Job]
        job?.invokeOnCompletion {
            if (call.isExecuted || !call.isCanceled) {
                runCatching { call.cancel() }
            }
        }

        val response: Response<ResponseBody> = try {
            call.execute()
        } catch (e: java.io.IOException) {
            // 被协程取消触发的 IOException（用户点"停止生成"）
            if (job?.isCancelled == true) return@flow
            throw ApiException.Network(e)
        } catch (e: Exception) {
            throw ApiException.Unknown(e)
        }

        // 处理错误响应（不把 API Key 写入消息）
        if (!response.isSuccessful) {
            val code = response.code()
            val errorBody = try {
                response.errorBody()?.string().orEmpty().take(200)
            } catch (_: Exception) {
                ""
            }
            response.errorBody()?.close()

            throw when (code) {
                401, 403 -> ApiException.Unauthorized()
                429 -> ApiException.RateLimited()
                in 400..499 -> ApiException.InvalidRequest(errorBody.ifBlank { "请求参数错误" })
                in 500..599 -> ApiException.ServerError(code)
                else -> ApiException.ServerError(code)
            }
        }

        val body = response.body()
            ?: throw ApiException.InvalidRequest("响应为空")

        val parser = EventSourceParser()
        parser.parse(body.source()).collect { rawData ->
            val chunk = try {
                json.decodeFromString(ChatCompletionChunk.serializer(), rawData)
            } catch (_: Exception) {
                // 个别事件 JSON 结构未知，跳过即可
                return@collect
            }
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (!content.isNullOrEmpty()) emit(content)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 规范化 URL，避免双斜杠 / 缺路径等问题。
     * 例如：
     *   "https://api.deepseek.com"      -> "https://api.deepseek.com/chat/completions"
     *   "https://api.deepseek.com/"     -> "https://api.deepseek.com/chat/completions"
     *   "https://api.deepseek.com/v1"   -> "https://api.deepseek.com/v1/chat/completions"
     */
    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return "$trimmed/chat/completions"
    }
}
