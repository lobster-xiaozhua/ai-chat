package com.example.aichat.data.remote.sse

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okio.BufferedSource

class EventSourceParser {

    fun parse(source: BufferedSource): Flow<String> = callbackFlow {
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                // 忽略注释行（以 : 开头）
                if (line.startsWith(":")) continue
                // 处理 data: 字段
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        channel.close()
                        break
                    }
                    // 过滤空 data
                    if (data.isNotEmpty()) {
                        trySend(data)
                    }
                }
            }
        } catch (e: Exception) {
            // 协程取消属于正常情况
            if (e is kotlinx.coroutines.CancellationException) throw e
            channel.close(e)
        } finally {
            source.close()
        }
        awaitClose { source.close() }
    }
}
