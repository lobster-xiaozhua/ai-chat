package com.example.aichat.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

@Serializable
data class RequestMessage(
    val role: String,                  // "system" | "user" | "assistant" | "tool"
    @Serializable(with = MessageContentSerializer::class)
    val content: MessageContent,       // 纯文本 或 多模态内容列表
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

/** content 的两种形式：纯字符串 / 多模态内容列表 */
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Parts(val parts: List<ContentPart>) : MessageContent()
}

/** 自定义序列化器：根据 JSON 实际形态自动选择 */
object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageContent")

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("MessageContent requires JsonEncoder")
        val element = when (value) {
            is MessageContent.Text -> JsonPrimitive(value.text)
            is MessageContent.Parts -> buildJsonArray {
                value.parts.forEach { part ->
                    add(part.toJsonElement())
                }
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): MessageContent {
        val jsonDecoder = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: error("MessageContent requires JsonDecoder")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> MessageContent.Text(element.content)
            is JsonArray -> {
                val parts = element.jsonArray.mapNotNull { parseContentPart(it) }
                MessageContent.Parts(parts)
            }
            else -> MessageContent.Text(element.toString())
        }
    }

    private fun parseContentPart(element: JsonElement): ContentPart? {
        val obj = element.jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "text" -> ContentPart.Text(obj["text"]?.jsonPrimitive?.content ?: "")
            "image_url" -> {
                val img = obj["image_url"]?.jsonObject
                ContentPart.Image(
                    url = img?.get("url")?.jsonPrimitive?.content ?: "",
                    detail = img?.get("detail")?.jsonPrimitive?.content
                )
            }
            else -> null
        }
    }
}

/** 多模态内容片段 */
sealed class ContentPart {
    data class Text(val text: String) : ContentPart()
    data class Image(val url: String, val detail: String? = null) : ContentPart()

    fun toJsonElement(): JsonElement = when (this) {
        is Text -> buildJsonObject {
            put("type", "text")
            put("text", this@toJsonElement.text)
        }
        is Image -> buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
                put("url", this@toJsonElement.url)
                this@toJsonElement.detail?.let { put("detail", it) }
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
