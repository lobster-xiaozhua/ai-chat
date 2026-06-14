package com.example.aichat.ui.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.LinkedHashMap

/**
 * 渲染带简单 Markdown 的文本气泡。
 *
 * 性能优化关键点：
 *   1. isStreaming=true 模式 —— 流式生成过程中不做正则解析，直接显示原始文本
 *      （每 token 都重解析开销很大，而且流式内容的格式标记可能被中途截断）
 *   2. isStreaming=false 模式 —— 对 (text, color) 做结果缓存，同一段文本的
 *      AnnotatedString 只会解析一次。
 */
@Composable
fun MarkdownRenderer(text: String, textColor: Color, isStreaming: Boolean = false) {
    val annotated = if (isStreaming) {
        // 流式：不做 Markdown 解析 —— 直接构造 String，最快
        AnnotatedString(text)
    } else {
        rememberParsed(text, textColor)
    }
    SelectionContainer {
        Text(text = annotated, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

/**
 * 用一个全局 LRU 缓存保存已解析过的 AnnotatedString。
 * 容量 ~ 32 条；每个会话通常不超过 10 条消息，所以足够。
 */
private object MarkdownCache {
    private val maxSize = 32
    private val cache = object : LinkedHashMap<Pair<String, Int>, AnnotatedString>(
        maxSize + 1, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, AnnotatedString>): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: Pair<String, Int>): AnnotatedString? = cache[key]

    @Synchronized
    fun put(key: Pair<String, Int>, value: AnnotatedString) { cache[key] = value }

    @Synchronized
    fun clear() = cache.clear()
}

@Composable
private fun rememberParsed(text: String, color: Color): AnnotatedString {
    // Compose 的 remember：只要 text 不变就不会重新计算；
    // 但 text 是 Compose 框架传入的引用，每次 UI 重组会做 equals 比较，OK。
    // 额外再加一层 MarkdownCache，保证跨重组 / 跨 Composable 都能命中。
    val key = text to color.hashCode()
    val cached = MarkdownCache.get(key)
    return cached ?: parseMarkdown(text, color).also { MarkdownCache.put(key, it) }
}

private fun parseMarkdown(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        val codeBlockPattern = Regex("```(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val inlineCodePattern = Regex("`([^`]+)`")
        val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
        val italicPattern = Regex("\\*(.*?)\\*")
        val linkPattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")

        var remaining = text

        // 处理代码块（优先级最高）
        while (remaining.contains("```")) {
            val startIdx = remaining.indexOf("```")
            if (startIdx > 0) {
                val beforeCode = remaining.substring(0, startIdx)
                append(parseInline(beforeCode, color))
            }
            val endIdx = remaining.indexOf("```", startIdx + 3)
            if (endIdx == -1) {
                append(remaining.substring(startIdx))
                break
            }
            val codeContent = remaining.substring(startIdx + 3, endIdx)
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = color.copy(alpha = 0.08f)))
            append("\n$codeContent\n")
            pop()
            remaining = remaining.substring(endIdx + 3)
        }

        if (remaining.isNotEmpty()) {
            append(parseInline(remaining, color))
        }
    }
}

private fun parseInline(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text

        // 链接
        val linkMatches = linkPat.findAll(remaining)
        val linkList = linkMatches.toList()
        if (linkList.isNotEmpty()) {
            var cursor = 0
            for (m in linkList) {
                if (cursor < m.range.first) {
                    append(parseNonLink(remaining.substring(cursor, m.range.first), color))
                }
                val display = m.groupValues[1]
                pushStyle(SpanStyle(color = Primary, fontWeight = FontWeight.Medium))
                append(display)
                pop()
                cursor = m.range.last + 1
            }
            if (cursor < remaining.length) {
                append(parseNonLink(remaining.substring(cursor), color))
            }
        } else {
            append(parseNonLink(remaining, color))
        }
    }
}

private fun parseNonLink(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text

        // 内联代码（`xxx`）：匹配后内容原样输出，不继续解析内部格式
        while (remaining.contains('`')) {
            val startIdx = remaining.indexOf('`')
            val endIdx = remaining.indexOf('`', startIdx + 1)
            if (endIdx == -1) break
            val before = remaining.substring(0, startIdx)
            val code = remaining.substring(startIdx + 1, endIdx)
            append(parseFormatting(before, color))
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = color.copy(alpha = 0.08f)))
            append(code)
            pop()
            remaining = remaining.substring(endIdx + 1)
        }

        if (remaining.isNotEmpty()) {
            append(parseFormatting(remaining, color))
        }
    }
}

private fun parseFormatting(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        // 粗体
        while (remaining.contains("**")) {
            val startIdx = remaining.indexOf("**")
            val endIdx = remaining.indexOf("**", startIdx + 2)
            if (endIdx == -1) break
            val before = remaining.substring(0, startIdx)
            val bold = remaining.substring(startIdx + 2, endIdx)
            append(before)
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(bold)
            pop()
            remaining = remaining.substring(endIdx + 2)
        }
        // 斜体（*xxx*，注意粗体已经被处理掉，** 不再干扰）
        while (remaining.contains('*')) {
            val startIdx = remaining.indexOf('*')
            val endIdx = remaining.indexOf('*', startIdx + 1)
            if (endIdx == -1) break
            val before = remaining.substring(0, startIdx)
            val italic = remaining.substring(startIdx + 1, endIdx)
            append(before)
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append(italic)
            pop()
            remaining = remaining.substring(endIdx + 1)
        }
        append(remaining)
    }
}

// 链接正则 —— 模块级 private，只创建一次
private val linkPat = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")

private val Primary = Color(0xFF6750A4)
