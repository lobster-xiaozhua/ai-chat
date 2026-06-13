package com.example.aichat.data.repository

import com.example.aichat.data.remote.OpenAiApiService
import com.example.aichat.data.remote.dto.ChatCompletionRequest
import com.example.aichat.data.remote.dto.ChatCompletionResponse
import com.example.aichat.data.remote.dto.ContentPart
import com.example.aichat.data.remote.dto.FunctionCallContent
import com.example.aichat.data.remote.dto.FunctionDefinition
import com.example.aichat.data.remote.dto.MessageContent
import com.example.aichat.data.remote.dto.RequestMessage
import com.example.aichat.data.remote.dto.ResponseFormat
import com.example.aichat.data.remote.dto.ResponseMessage
import com.example.aichat.data.remote.dto.Tool
import com.example.aichat.data.remote.dto.ToolCall
import com.example.aichat.data.remote.sse.EventSourceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/* ========================================================================
 * 错误类型（统一分级）
 * ====================================================================== */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(msg: String = "未授权：请检查 API Key 是否正确") : ApiException(msg)
    class RateLimited(msg: String = "请求过于频繁，请稍后再试") : ApiException(msg)
    class ServerError(code: Int, msg: String = "服务器错误 ($code)") : ApiException(msg)
    class InvalidRequest(msg: String = "请求无效：$msg") : ApiException(msg)
    class Network(cause: Throwable?) : ApiException("网络异常，请检查连接", cause)
    class Unknown(cause: Throwable?) : ApiException("未知错误：${cause?.message}", cause)
}

/* ========================================================================
 * 用户消息（供 ViewModel 调用）
 *   text: 主文本
 *   imageUrls: 附加图片 URL（可为空，有图片时自动走多模态）
 * ====================================================================== */
data class UserMessage(
    val text: String,
    val imageUrls: List<String> = emptyList()
)

/* ========================================================================
 * 工具调用注册表：应用层可以注册自定义工具（例如 weather, calc）
 *
 * 目前占位：没有真正注册任何工具，仅提供完整的数据管道。
 *   - execute 中若工具未注册，会返回一段占位 JSON
 *   - 工具调用循环最多 3 轮，防止死循环
 * ====================================================================== */
@Singleton
class ToolRegistry @Inject constructor() {

    private val tools = mutableMapOf<String, FunctionEntry>()

    fun register(entry: FunctionEntry) {
        tools[entry.def.name] = entry
    }

    fun get(name: String): FunctionEntry? = tools[name]

    fun list(): List<Tool> = tools.values.map { Tool(function = it.def) }

    /** 执行单个工具调用；未注册的工具返回占位 JSON */
    suspend fun execute(call: ToolCall): String = withContext(Dispatchers.Default) {
        val entry = tools[call.function.name]
            ?: return@withContext """{"status": "tool_not_registered", "name": "${call.function.name}"}"""
        try {
            entry.handler(call.function.arguments)
        } catch (t: Throwable) {
            """{"status": "error", "message": "${t.message}"}"""
        }
    }
}

data class FunctionEntry(
    val def: FunctionDefinition,
    val handler: suspend (String) -> String
)

/* ========================================================================
 * 主 Repository
 * ====================================================================== */
@Singleton
class AiRepository @Inject constructor(
    private val apiService: OpenAiApiService,
    private val toolRegistry: ToolRegistry
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 流式对话（对外统一入口）。
     *
     * @param messages  用户历史消息（文本 + 可选图片）
     * @param systemPrompt 系统提示词
     * @param baseUrl  API 根地址
     * @param model    模型名
     * @param apiKey   认证 Key
     * @param temperature 采样温度
     * @param jsonMode 是否强制输出 JSON
     * @param enableTools 是否启用已注册的工具调用（默认关闭）
     */
    fun streamChat(
        messages: List<Pair<String, String>>,    // (role, text)
        systemPrompt: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        temperature: Double,
        jsonMode: Boolean = false,
        enableTools: Boolean = false,
        userImageUrls: List<String> = emptyList()
    ): Flow<String> = channelFlow {

        val url = buildChatCompletionsUrl(baseUrl)
        val auth = "Bearer $apiKey"
        val registeredTools = if (enableTools) toolRegistry.list() else null

        // —— 构建初始消息列表
        val requestMessages = buildMessageList(
            systemPrompt = systemPrompt,
            historyPairs = messages,
            userImageUrls = userImageUrls
        )

        // —— 主循环：最多 3 轮工具调用
        var currentMessages = requestMessages
        var depth = 0
        val maxDepth = 3

        while (depth < maxDepth) {
            depth++

            val request = ChatCompletionRequest(
                model = model,
                messages = currentMessages,
                temperature = temperature,
                stream = true,
                responseFormat = if (jsonMode) ResponseFormat("json_object") else null,
                tools = registeredTools,
                toolChoice = null // "auto" 由默认值控制
            )

            // 解析本轮的流式输出，收集完整文本 + 可能的 tool_calls
            val roundResult = runStreamingRound(url, auth, request)

            when (roundResult) {
                is RoundResult.Text -> {
                    // 普通文本完成 → 直接 emit 给 UI 并结束
                    send(roundResult.content)
                    return@channelFlow
                }
                is RoundResult.ToolCalls -> {
                    // 模型调用工具：
                    //  1. emit 给 UI 一段提示文字（可选，不打扰正常对话）
                    //  2. 逐个执行工具并把结果塞回 messages
                    //  3. 继续下一轮
                    send("正在调用工具: ${roundResult.calls.joinToString { it.function.name }}...\n")

                    val toolResultMessages = mutableListOf<RequestMessage>()

                    // 先补一个 assistant 消息，其中带 tool_calls
                    toolResultMessages += RequestMessage(
                        role = "assistant",
                        content = MessageContent.Text(""),
                        toolCalls = roundResult.calls
                    )

                    // 再按工具调用的 id，逐个塞 tool 结果消息
                    for (call in roundResult.calls) {
                        val result = toolRegistry.execute(call)
                        toolResultMessages += RequestMessage(
                            role = "tool",
                            content = MessageContent.Text(result),
                            toolCallId = call.id ?: UUID.randomUUID().toString()
                        )
                    }

                    currentMessages = currentMessages + toolResultMessages
                    // 继续循环，再请求一轮
                }
                is RoundResult.Failed -> {
                    throw roundResult.error
                }
            }
        }

        // 超过最大深度 → 兜底
        send("\n(已达到最大工具调用深度，停止)")
    }.flowOn(Dispatchers.IO)

    /* ---------- 内部：执行一轮流式请求 ---------- */

    private suspend fun runStreamingRound(
        url: String,
        auth: String,
        request: ChatCompletionRequest
    ): RoundResult = withContext(Dispatchers.IO) {

        val call = apiService.streamChatCompletion(url, auth, request)

        val job = currentCoroutineContext()[Job]
        job?.invokeOnCompletion {
            if (call.isExecuted) runCatching { call.cancel() }
        }

        val response: Response<ResponseBody> = try {
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
            } catch (_: Exception) {
                return@collect
            }

            chunk.choices.firstOrNull()?.let { choice ->
                choice.delta?.content?.takeIf { it.isNotEmpty() }?.let {
                    textBuilder.append(it)
                }
                // 收集 tool_calls（流式中 tool_calls 可能分多块到达）
                choice.delta?.toolCalls?.forEach { call ->
                    sawToolCalls = true
                    val idx = call.id?.toIntOrNull() ?: toolCallBuilder.size
                    val existing = toolCallBuilder.getOrPut(idx) { ToolCallBuilder() }
                    if (call.id != null) existing.id = call.id
                    if (call.function.name.isNotEmpty()) existing.name += call.function.name
                    if (call.function.arguments.isNotEmpty()) existing.arguments += call.function.arguments
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

    /* ---------- 内部：一轮请求的结果 ---------- */
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
            list += RequestMessage(
                role = "system",
                content = MessageContent.Text(systemPrompt)
            )
        }

        historyPairs.forEachIndexed { idx, (role, text) ->
            // 历史消息一律走纯文本（旧记录不包含图片）
            if (idx == historyPairs.size - 1 && role == "user" && userImageUrls.isNotEmpty()) {
                // 最后一条用户消息 → 把图片附件拼上去，组成多模态
                val parts = mutableListOf<ContentPart>().apply {
                    if (text.isNotEmpty()) add(ContentPart.Text(text))
                    userImageUrls.forEach { add(ContentPart.Image(it)) }
                }
                list += RequestMessage(role = "user", content = MessageContent.Parts(parts))
            } else {
                list += RequestMessage(role = role, content = MessageContent.Text(text))
            }
        }

        return list
    }

    /* ---------- 内部：URL 规范化 ---------- */

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return "$trimmed/chat/completions"
    }
}
