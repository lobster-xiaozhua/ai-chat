package com.example.aichat.ui.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.aichat.ui.theme.Primary

/**
 * 消息气泡 —— 支持纯文本、流式文本、以及文本+图片（用户消息）。
 *
 * @param content 文本内容
 * @param isUser 是否为用户消息（决定对齐与配色）
 * @param imageUrls 图片附件列表（content:// 或 data:// URI 字符串）
 */
@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    imageUrls: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val bgColor = if (isUser) Primary else MaterialTheme.colorScheme.surfaceVariant
        val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                // —— 图片区域（仅用户消息有图片）
                if (imageUrls.isNotEmpty()) {
                    ImageGrid(urls = imageUrls, isUser = isUser)
                }

                // —— 文本区域
                if (content.isNotEmpty() || (imageUrls.isEmpty() && !isStreaming)) {
                    Box(modifier = Modifier.padding(top = if (imageUrls.isNotEmpty() && content.isNotEmpty()) 6.dp else 0.dp)) {
                        if (content.isEmpty()) {
                            Text("…", color = textColor.copy(alpha = 0.4f))
                        } else if (isStreaming) {
                            ParagraphStreamingText(text = content, isStreaming = true, textColor = textColor)
                        } else {
                            MarkdownRenderer(content, textColor, isStreaming = false)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图片网格 —— 根据数量动态布局。
 *   · 1 张 → 整张横版 3:4 或 4:3
 *   · 2 张 → 左右各一张，1:1
 *   · 3+ 张 → 2xN 网格（每行 2 张，最后一张居中）
 */
@Composable
private fun ImageGrid(urls: List<String>, isUser: Boolean) {
    when (urls.size) {
        1 -> SingleImage(urls[0])
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SingleImage(url = urls[0], modifier = Modifier.weight(1f))
            SingleImage(url = urls[1], modifier = Modifier.weight(1f))
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            urls.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { url ->
                        SingleImage(url = url, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Box(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

/**
 * 单张图片 —— 通过 Coil 从 content:// URI / http URL / data URI 加载。
 * 使用固定高度 + 内容缩放，避免大图占用过多屏幕。
 */
@Composable
private fun SingleImage(url: String, modifier: Modifier = Modifier) {
    val imageUri = try {
        Uri.parse(url)
    } catch (_: Exception) {
        null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE8E8E8))
            .aspectRatio(1.0f)
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
