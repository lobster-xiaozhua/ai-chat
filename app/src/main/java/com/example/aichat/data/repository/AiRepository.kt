package com.example.aichat.data.repository

import com.example.aichat.data.remote.OpenAiApiService
import com.example.aichat.data.remote.dto.ChatCompletionRequest
import com.example.aichat.data.remote.dto.FunctionCallContent
import com.example.aichat.data.remote.dto.FunctionDefinition
import com.example.aichat.data.remote.dto.RequestMessage
import com.example.aichat.data.remote.dto.ResponseFormat
import com.example.aichat.data.remote.dto.Tool
import com.example.aichat.data.remote.dto.ToolCall
import com.example.aichat.data.remote.dto.buildMultipartContent
import com.example.aichat.data.remote.dto.buildTextContent
import com.example.aichat.data.remote.sse.EventSourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Call
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/* =============================================================================
 * 错误类型（统一分级，不暴露敏感信息）
 * ========================================================================== */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(msg: String = "未授权：请检查 API Key 是否正确") : ApiException(msg)
    class RateLimited(msg: String = "请求过于频繁，请稍后再试") : ApiException(msg)
    class ServerError(code: Int, msg: String = "服务器错误 ($code)") : ApiException(msg)
    class InvalidRequest(msg: String = "请求无效：$msg") : ApiException(msg)
    class Network(cause: Throwable?) : ApiException("网络异常，请检查连接", cause)
    class Unknown(cause: Throwable?) : ApiException("未知错误：${cause?.message}", cause)
}

/* =============================================================================
 * 工具注册表（供应用层注册自定义工具；当前为空 → 仅做演示）
 * ========================================================================== */

@Singleton
class ToolRegistry @Inject constructor() {

    /**
     * ConcurrentHashMap：支持注册阶段（主线程）与执行阶段（网络协程）并发读写；
     * · put/remove 仅允许注册阶段调用，执行阶段只读
     * · 注册完成后，在任何协程中读 list/get/execute 都是线程安全的
     */
    private val tools = ConcurrentHashMap<String, FunctionEntry>()

    /**
     * 注册工具。只允许在 Application.onCreate 或单例初始化阶段调用。
     * 注册顺序不保证，同名后注册会覆盖先注册。
     */
    fun register(entry: FunctionEntry) {
        tools[entry.def.name] = entry
    }

    fun get(name: String): FunctionEntry? = tools[name]

    fun list(): List<Tool> = tools.values.map { Tool(function = it.def) }

    /**
     * 执行工具调用；未注册返回占位 JSON。
     * · 在 Dispatchers.Default 上执行，避免阻塞网络协程。
     * · handler 可能抛异常，这里兜底包装为 JSON 错误响应。
     */
    suspend fun execute(call: ToolCall): String = withContext(Dispatchers.Default) {
        val entry = tools[call.function.name]
            ?: return@withContext """{"status":"tool_not_registered","name":"${call.function.name}"}"""
        try {
            entry.handler(call.function.arguments)
        } catch (t: Throwable) {
            """{"status":"error","message":"${t.message}"}"""
        }
    }
}

data class FunctionEntry(
    val def: FunctionDefinition,
    val handler: suspend (String) -> String
)

/* =============================================================================
 * 主 Repository：流式对话（支持 JSON 模式 / 多模态 / 工具调用）
 * ========================================================================== */

@Singleton
class AiRepository @Inject constructor(
    private val apiService: OpenAiApiService,
    private val toolRegistry: ToolRegistry
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun streamChat(
        messages: List<Pair<String, String>>,
        systemPrompt: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        temperature: Double,
        jsonMode: Boolean = false,
        enableTools: Boolean = false,
        userImageUrls: List<String> = emptyList(),
        thinkMode: Boolean = false,
        searchMode: Boolean = false
    ): Flow<String> = channelFlow {

        val url = buildChatCompletionsUrl(baseUrl)
        val auth = "Bearer $apiKey"
        val registeredTools = if (enableTools) toolRegistry.list() else null

        // —— 根据模式调整 system prompt + temperature：
        //    · Think → 在 system prompt 追加"逐步推理"提示；temperature 稍高
        //    · Search → 在 system prompt 追加"基于检索上下文回答"提示
        //    · Think 关闭 → 更简洁 / 直给回答
        val effectivePrompt = buildString {
            append(systemPrompt.ifBlank { "You are a helpful, concise assistant." })
            if (thinkMode) {
                append(" Before answering, break the problem into logical steps, show your reasoning, and then provide a final, well-supported answer. Take your time—accuracy matters more than speed.")
            } else {
                append(" Keep your answer concise and to the point.")
            }
            if (searchMode) {
                append(" The user has enabled search / retrieval mode; frame your answer as if consulting updated knowledge, note uncertainties where applicable.")
            }
            if (jsonMode) {
                append(" Always return a valid JSON object with clear key fields, never plain prose.")
            }
        }

        // Think 模式提高 temperature（允许更广的推理空间）
        val effectiveTemp = when {
            thinkMode && temperature <= 0.2 -> temperature + 0.4
            thinkMode -> (temperature + 0.2).coerceAtMost(2.0)
            else -> temperature
        }

        var currentMessages = buildMessageList(effectivePrompt, messages, userImageUrls)
        var depth = 0
        val maxDepth = 3

        while (depth < maxDepth) {
            depth++

            val request = ChatCompletionRequest(
                model = model,
                messages = currentMessages,
                temperature = effectiveTemp,
                stream = true,
                responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
                tools = registeredTools,
                toolChoice = null
            )

            when (val r = runStreamingRound(url, auth, request)) {
                is RoundResult.Text -> { send(r.content); return@channelFlow }
                is RoundResult.ToolCalls -> {
                    send("正在调用工具: ${r.calls.joinToString { it.function.name }}...\n")
                    currentMessages += RequestMessage(
                        role = "assistant",
                        content = buildTextContent(""),
                        toolCalls = r.calls
                    )
                    for (call in r.calls) {
                        val result = toolRegistry.execute(call)
                        currentMessages += RequestMessage(
                            role = "tool",
                            content = buildTextContent(result),
                            toolCallId = call.id ?: UUID.randomUUID().toString()
                        )
                    }
                }
                is RoundResult.Failed -> throw r.error
            }
        }

        send("\n(已达到最大工具调用深度)")
    }.flowOn(Dispatchers.IO)

    /* ---------- 内部：执行一轮流式请求 ---------- */

    private suspend fun runStreamingRound(
        url: String,
        auth: String,
        request: ChatCompletionRequest
    ): RoundResult = withContext(Dispatchers.IO) {

        val call = apiService.streamChatCompletion(url, auth, request)
        val job = currentCoroutineContext()[Job]
        job?.invokeOnCompletion { if (call.isExecuted) runCatching { call.cancel() } }

        val response = try {
            call.execute()
        } catch (e: java.io.IOException) {
            if (job?.isCancelled == true) return@withContext RoundResult.Text("")
            return@withContext RoundResult.Failed(ApiException.Network(e))
        } catch (e: Exception) {
            return@withContext RoundResult.Failed(ApiException.Unknown(e))
        }

        if (!response.isSuccessful) {
            val code = response.code()
            val err = response.errorBody()?.use { it.string().take(200) }.orEmpty()
            return@withContext RoundResult.Failed(
                when (code) {
                    401, 403 -> ApiException.Unauthorized()
                    429 -> ApiException.RateLimited()
                    in 400..499 -> ApiException.InvalidRequest(err.ifBlank { "参数错误" })
                    else -> ApiException.ServerError(code)
                }
            )
        }

        val body = response.body() ?: return@withContext RoundResult.Failed(
            ApiException.InvalidRequest("响应为空")
        )

        val textBuilder = StringBuilder()
        val toolCallBuilder = mutableMapOf<Int, ToolCallBuilder>()
        var sawToolCalls = false

        EventSourceParser().parse(body.source()).collect { raw ->
            val chunk = try {
                json.decodeFromString<com.example.aichat.data.remote.dto.ChatCompletionChunk>(raw)
            } catch (_: Exception) { return@collect }

            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { textBuilder.append(it) }
                choice.delta?.toolCalls?.forEach { call ->
                    sawToolCalls = true
                    val idx = call.id?.toIntOrNull() ?: toolCallBuilder.size
                    val existing = toolCallBuilder.getOrPut(idx) { ToolCallBuilder() }
                    if (call.id != null) existing.id = call.id
                    existing.name += call.function.name
                    existing.arguments += call.function.arguments
                }
            }
        }

        if (sawToolCalls && toolCallBuilder.isNotEmpty()) {
            val calls = toolCallBuilder.entries.sortedBy { it.key }.map { (_, b) ->
                ToolCall(
                    id = b.id.ifEmpty { UUID.randomUUID().toString() },
                    function = FunctionCallContent(name = b.name, arguments = b.arguments)
                )
            }
            return@withContext RoundResult.ToolCalls(calls)
        }
        return@withContext RoundResult.Text(textBuilder.toString())
    }

    private class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        var arguments: String = ""
    )

    private sealed class RoundResult {
        data class Text(val content: String) : RoundResult()
        data class ToolCalls(val calls: List<ToolCall>) : RoundResult()
        data class Failed(val error: Throwable) : RoundResult()
    }

    /* ---------- 内部：构建 messages 列表 ---------- */

    private fun buildMessageList(
        systemPrompt: String,
        historyPairs: List<Pair<String, String>>,
        userImageUrls: List<String>
    ): List<RequestMessage> {
        val list = mutableListOf<RequestMessage>()

        if (systemPrompt.isNotBlank()) {
            list += RequestMessage(role = "system", content = buildTextContent(systemPrompt))
        }

        historyPairs.forEachIndexed { idx, (role, text) ->
            if (idx == historyPairs.size - 1 && role == "user" && userImageUrls.isNotEmpty()) {
                // 最后一条用户消息 → 多模态（文本 + 图片）
                list += RequestMessage(
                    role = "user",
                    content = buildMultipartContent {
                        if (text.isNotEmpty()) text(text)
                        userImageUrls.forEach { image(it) }
                    }
                )
            } else {
                list += RequestMessage(role = role, content = buildTextContent(text))
            }
        }

        return list
    }

    /* ---------- 内部：URL 规范化 ---------- */

    private fun buildChatCompletionsUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + "/chat/completions"
}
