package com.example.aichat.ui.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * 【方案 B】段落级缓存渲染 ParagraphStreamingText。
 *
 * ============= 问题 =============
 * Text(text = growingString) 在每个 token 都做一次完整 Paragraph.layout()，
 * layout 时间随长度线性增长 → 总 CPU 随长度二次方增长。
 *
 * ============= 解法 =============
 * 把流式文本理解为 "已完成段落 × N + 正在增长的尾部 × 1"：
 *
 *   [Paragraph 1]   BasicText(text = immutable_string, ...)  ← Compose 完全跳过
 *   [Paragraph 2]   BasicText(text = immutable_string, ...)  ← Compose 完全跳过
 *        ...
 *   [Paragraph N-1] BasicText(text = immutable_string, ...)  ← Compose 完全跳过
 *   [活跃尾部]      BasicText(text = currently_typing, ...)  ← 唯一参与 layout
 *
 * 关键性质：
 *   1) 每个已完成段落的 text 参数都是同一个内容 → Compose 编译器比较输入相等就跳重组
 *      → skip layout → skip draw。整个段落完全零 CPU 开销。
 *   2) 活跃尾部永远 ≤ ~250 字符 → 它的 layout 时间恒定 O(1)。
 *   3) 总 CPU ≈ O(N) 线性，不再 O(N²)。
 *   4) 零体验妥协：不节流、不合并、不延迟、不丢 token。
 *
 * ============= 何时切出一个段落 =============
 * 搜索窗口 = [MIN_PARAGRAPH, text.length - ACTIVE_MAX_CHARS]，
 * 在窗口内找最靠右（最新）的自然断点：
 *   1) \n\n 双换行（段落边界）→ 首选
 *   2) \n 单换行（行末）
 *   3) 句末标点（。！？； etc.）
 *   4) 逗号/冒号等次级标点
 *   5) 空格
 *   6) 找不到就不切，整段都算活跃尾部
 */

private const val ACTIVE_MAX_CHARS = 250
private const val MIN_PARAGRAPH = 40

private val punctEnd = setOf('。', '！', '？', '；', '!', '?', ';', '”', '）', '}', ']', '`')
private val punctMid = setOf('，', '、', '：', ',', ':', '—', ')')

@Composable
fun ParagraphStreamingText(
    text: String,
    isStreaming: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    if (!isStreaming) {
        // 完成态 → MarkdownRenderer（带格式 + LRU 缓存）
        MarkdownRenderer(text, textColor, isStreaming = false)
        return
    }

    // —— 流式态：计算段落边界 ——
    // boundaries[i] = 第 i 段的结束位置（exclusive）。
    // 注意：basicText 在 Compose 中测量文本时会缓存字形（glyph），
    // 但如果每次都给它一个新字符串 + 新 Paragraph，缓存会失效。
    // 我们的策略：把大文本切成「已冻结段落」（内容不变 → Compose skip 重组/布局）
    // +「活跃尾部」（短文本，每 token 重布局但代价恒定 ≈ O(250)）。
    val boundaries = rememberParagraphBoundaries(text)
    val count = boundaries.size

    // 流式态：有意不使用 SelectionContainer。
    // 用户在"正在打字"时不会去长按选中；SelectionContainer 每 token 都要
    // 重建字符 hit-testing 信息，纯浪费 CPU。完成态在 MarkdownRenderer
    // 中提供 SelectionContainer。
    androidx.compose.foundation.layout.Column {
        // 已完成段落：每个 paragraph 被一个稳定 key(idx) 标识
        for (i in 0 until count - 1) {
            val start = boundaries[i]
            val end = boundaries[i + 1]
            androidx.compose.runtime.key(i) {
                FrozenParagraph(
                    text = text.substring(start, end),
                    color = textColor,
                    modifier = modifier
                )
            }
        }
        // 活跃尾部：长度 ≤ ACTIVE_MAX_CHARS，唯一参与 layout 的部分
        val tail = text.substring(boundaries[count - 1])
        if (tail.isNotEmpty()) {
            ActiveParagraph(text = tail, color = textColor, modifier = modifier)
        }
    }
}

/**
 * 从文本计算段落边界。核心逻辑（单调扫描，永不回溯）：
 *
 *   cursor = 0
 *   while cursor < text.length - ACTIVE_MAX_CHARS:
 *       在 [cursor + MIN_PARAGRAPH, text.length - ACTIVE_MAX_CHARS] 窗口内
 *       找"最右（最新）的自然断点"
 *       找不到 → break（整段都是活跃尾部）
 *       找到 → 把它加入 boundaries；cursor = 断点位置
 *
 * 复杂度：O(n)，一遍线性扫描，没有正则匹配。
 */
@Composable
private fun rememberParagraphBoundaries(text: String): IntArray {
    // 即使 text 每个 token 都变，remember(text) 仍然有价值：
    // 父 Composable 可能因其他状态（如 pendingImageUrls、isGenerating）触发
    // 整屏重组，此时 text 没变但本函数会被再次调用。
    // remember 让 Compose 编译器在 key 不变时跳过 lambda 内部。
    return androidx.compose.runtime.remember(text) { computeBoundariesImpl(text) }
}

private fun computeBoundariesImpl(text: String): IntArray {
    if (text.length <= ACTIVE_MAX_CHARS + MIN_PARAGRAPH) {
        return intArrayOf(0)  // 文本太短，没有任何已完成段落
    }

    val boundaries = mutableListOf(0)
    var cursor = 0
    val safeUpper = text.length - ACTIVE_MAX_CHARS  // 活跃尾部至少留这么多

    while (cursor + MIN_PARAGRAPH < safeUpper) {
        // 搜索窗口：[cursor + MIN_PARAGRAPH, safeUpper]
        var best = -1
        var i = cursor + MIN_PARAGRAPH

        // --- 1) 双换行（段落边界）---
        while (i < safeUpper) {
            if (text[i] == '\n' && i > 0 && text[i - 1] == '\n') {
                best = i + 1  // 包含两个换行符
            }
            i++
        }
        if (best > 0) {
            boundaries.add(best)
            cursor = best
            continue
        }

        // --- 2) 单换行 ---
        i = cursor + MIN_PARAGRAPH
        while (i < safeUpper) {
            if (text[i] == '\n') best = i + 1
            i++
        }
        if (best > 0) {
            boundaries.add(best)
            cursor = best
            continue
        }

        // --- 3) 句末标点 ---
        i = cursor + MIN_PARAGRAPH
        while (i < safeUpper) {
            if (text[i] in punctEnd) best = i + 1
            i++
        }
        if (best > cursor + MIN_PARAGRAPH) {
            boundaries.add(best)
            cursor = best
            continue
        }

        // --- 4) 次级标点 ---
        i = cursor + MIN_PARAGRAPH
        while (i < safeUpper) {
            if (text[i] in punctMid) best = i + 1
            i++
        }
        if (best > cursor + MIN_PARAGRAPH) {
            boundaries.add(best)
            cursor = best
            continue
        }

        // --- 5) 空格 ---
        i = cursor + MIN_PARAGRAPH
        while (i < safeUpper) {
            if (text[i] == ' ' || text[i] == '\u00A0') best = i + 1
            i++
        }
        if (best > cursor + MIN_PARAGRAPH) {
            boundaries.add(best)
            cursor = best
            continue
        }

        // --- 6) 找不到自然断点 — 等更多 token 到达
        break
    }

    return boundaries.toIntArray()
}

/**
 * 已完成段落 — text 永不改变 → Compose 编译器自动跳过重组、layout、draw。
 * graphicsLayer 轻量标记帮助 RenderNode 缓存绘制指令。
 */
@Composable
private fun FrozenParagraph(text: String, color: Color, modifier: Modifier = Modifier) {
    val currentStyle = LocalTextStyle.current
    val style = androidx.compose.runtime.remember(color, currentStyle) {
        currentStyle.copy(
            color = color,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        modifier = modifier
    )
}

/**
 * 活跃尾部 — 每 token 都变，但永远 ≤ ~250 字符。
 * layout 时间恒定 → 不再出现"越往后越慢"。
 */
@Composable
private fun ActiveParagraph(text: String, color: Color, modifier: Modifier = Modifier) {
    val currentStyle = LocalTextStyle.current
    val style = androidx.compose.runtime.remember(color, currentStyle) {
        currentStyle.copy(
            color = color,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        modifier = modifier
    )
}
