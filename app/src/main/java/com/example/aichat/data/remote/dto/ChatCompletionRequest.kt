package com.example.aichat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ============================================================
 * Chat Completion Request / Response
 * 兼容 OpenAI / NVIDIA API 规范
 * ========================================================== */

/* ---------- 请求 ---------- */

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = true,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null
)

@Serializable
data class ResponseFormat(val type: String) // "text" | "json_object"

/* ---------- 消息 ---------- */

/**
 * content 字段采用 kotlinx 原生的 `JsonElement`：
 *   - 纯文本: `JsonPrimitive("hello")` 或 `buildTextContent("hello")`
 *   - 多模态: `buildContent { text("..."); image("https://...") }`
 *
 * 这种方式比自定义 KSerializer 更简洁：
 *   1. JsonElement 已在 kotlinx-serialization-json 中内建可序列化
 *   2. 调用者用 builder 函数灵活构造文本/多模态 content
 *   3. 没有 sealed class + 自定义序列化器的编译陷阱
 */
@Serializable
data class RequestMessage(
    val role: String,                      // "system" | "user" | "assistant" | "tool"
    val content: JsonElement,              // 纯文本 JSON 字符串 或 多模态 JSON 数组
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

/* ---------- content 构建器：一行代码构建文本/多模态内容 ---------- */

/** 构建纯文本 content（绝大多数场景使用这个） */
fun buildTextContent(text: String): JsonElement = JsonPrimitive(text)

/** 构建多模态 content（文本 + 图片） */
fun buildMultipartContent(block: ContentBuilder.() -> Unit): JsonElement {
    val builder = ContentBuilder().apply(block)
    return buildJsonArray {
        builder.parts.forEach { add(it) }
    }
}

class ContentBuilder {
    internal val parts = mutableListOf<JsonObject>()

    fun text(text: String) {
        parts += buildJsonObject {
            put("type", "text")
            put("text", text)
        }
    }

    fun image(url: String, detail: String? = null) {
        parts += buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
                put("url", url)
                detail?.let { put("detail", it) }
            })
        }
    }
}

/* ---------- 工具调用 ---------- */

@Serializable
data class Tool(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null  // JSON Schema
)

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String = "function",
    val function: FunctionCallContent
)

@Serializable
data class FunctionCallContent(
    val name: String,
    val arguments: String  // JSON 字符串
)

/* ---------- 流式响应 ---------- */

@Serializable
data class ChatCompletionChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList()
)

@Serializable
data class ChunkChoice(
    val index: Int,
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

/* ---------- 非流式响应（工具调用二次请求用）---------- */

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<ResponseChoice> = emptyList()
)

@Serializable
data class ResponseChoice(
    val index: Int,
    val message: ResponseMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)
