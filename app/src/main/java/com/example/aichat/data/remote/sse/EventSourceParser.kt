package com.example.aichat.data.remote.sse

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okio.BufferedSource

/**
 * 标准 SSE (Server-Sent Events) 解析器。
 *
 * 协议要点：
 *   - 每个事件由一个或多个 `data:` 行组成，以空行结束
 *   - 多个 `data:` 行通过换行符 `\n` 拼接
 *   - `data: `（带空格）与 `data:`（无空格）均合法
 *   - 以 `:` 开头的行为注释，忽略
 *   - `data: [DONE]` 标记流结束
 */
class EventSourceParser {

    fun parse(source: BufferedSource): Flow<String> = callbackFlow {
        val buffer = StringBuilder()

        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue

                // 注释行（以 : 开头）直接忽略
                if (line.startsWith(":")) continue

                // 空行 => 事件结束，提交已缓冲的 data
                if (line.isEmpty()) {
                    if (buffer.isNotEmpty()) {
                        val data = buffer.toString().trimEnd('\n')
                        if (data.isNotEmpty()) {
                            trySend(data)
                        }
                        buffer.clear()
                    }
                    continue
                }

                // 解析 data: 行（支持 "data:xxx" 与 "data: xxx"）
                if (line.startsWith("data:")) {
                    val value = if (line.length > 5) {
                        // Skip "data:" and any leading spaces (SSE spec allows "data: " with optional spaces)
                        line.substring(5).dropWhile { it == ' ' }
                    } else ""

                    // 检测结束标记（可能出现在多行 data 的最后一行）
                    val trimmed = value.trim()
                    if (trimmed == "[DONE]") {
                        // 先 flush 掉之前 buffer 的内容（虽通常 [DONE] 单独一行）
                        if (buffer.isNotEmpty()) {
                            val data = buffer.toString().trimEnd('\n')
                            if (data.isNotEmpty()) trySend(data)
                            buffer.clear()
                        }
                        channel.close()
                        runCatching { source.close() }
                        break
                    }

                    // 多行 data 通过换行符拼接
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(value)
                }
                // 其它字段（event / id / retry）简单忽略，Chat Completion 不用
            }

            // source 耗尽但 [DONE] 未收到 → flush 残余 buffer
            if (buffer.isNotEmpty()) {
                val data = buffer.toString().trimEnd('\n')
                if (data.isNotEmpty()) trySend(data)
                buffer.clear()
            }
        } catch (e: Exception) {
            // 协程取消（用户点停止生成）属于正常流程，重新抛出
            if (e is kotlinx.coroutines.CancellationException) throw e
            channel.close(e)
        } finally {
            runCatching { source.close() }
        }

        awaitClose { runCatching { source.close() } }
    }
}
