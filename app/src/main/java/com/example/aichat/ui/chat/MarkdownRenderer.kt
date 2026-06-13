package com.example.aichat.ui.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(text: String, textColor: Color) {
    val annotated = parseMarkdown(text, textColor)
    SelectionContainer {
        Text(
            text = annotated,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

private fun parseMarkdown(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        val codeBlockPattern = Regex("```(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val inlineCodePattern = Regex("`([^`]+)`")
        val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
        val italicPattern = Regex("\\*(.*?)\\*")
        val linkPattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")

        var remaining = text

        // 处理代码块
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
            pushStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                background = color.copy(alpha = 0.08f)
            ))
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
        val tokens = mutableListOf<Triple<String, SpanStyle, Boolean>>()
        tokens.add(Triple("``", SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = color.copy(alpha = 0.1f)
        ), false))
        tokens.add(Triple("**", SpanStyle(fontWeight = FontWeight.Bold), false))
        tokens.add(Triple("*", SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), false))

        val linkPattern = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")
        val inlineCodePattern = Regex("`([^`]+)`")
        val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
        val italicPattern = Regex("\\*(.*?)\\*")

        var rest = text
        var hasMatch: Boolean
        do {
            hasMatch = false
            val linkMatch = linkPattern.find(rest)
            val codeMatch = inlineCodePattern.find(rest)
            val boldMatch = boldPattern.find(rest)
            val italicMatch = italicPattern.find(rest)

            val matches = listOfNotNull(
                linkMatch?.let { "link" to it },
                codeMatch?.let { "code" to it },
                boldMatch?.let { "bold" to it },
                italicMatch?.let { "italic" to it }
            ).sortedBy { it.second.range.first }

            if (matches.isNotEmpty()) {
                val (type, match) = matches.first()
                val before = rest.substring(0, match.range.first)
                append(before)

                when (type) {
                    "link" -> {
                        pushStyle(SpanStyle(
                            color = androidx.compose.ui.graphics.Color(0xFF5B8DEF),
                            textDecoration = TextDecoration.Underline
                        ))
                        append(match.groupValues[1])
                        pop()
                    }
                    "code" -> {
                        pushStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = color.copy(alpha = 0.1f)
                        ))
                        append(match.groupValues[1])
                        pop()
                    }
                    "bold" -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(match.groupValues[1])
                        pop()
                    }
                    "italic" -> {
                        pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        append(match.groupValues[1])
                        pop()
                    }
                }
                rest = rest.substring(match.range.last + 1)
                hasMatch = true
            }
        } while (hasMatch)

        append(rest)
    }
}
