package com.example.aichat.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import com.example.aichat.ui.icons.ExtendedIcons
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * 消息气泡 —— 支持纯文本、流式文本、以及文本+图片（用户消息）。
 */
@OptIn(ExperimentalFoundationApi::class)
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
        val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                if (imageUrls.isNotEmpty()) {
                    ImageGrid(urls = imageUrls, isUser = isUser)
                }

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

@Composable
private fun SingleImage(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .aspectRatio(1.0f)
    ) {
        LocalImage(
            uri = url,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LocalImage(uri: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            val parsed = android.net.Uri.parse(uri)
            context.contentResolver.openInputStream(parsed)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Extended.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
