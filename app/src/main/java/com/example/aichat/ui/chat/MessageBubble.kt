package com.example.aichat.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aichat.ui.theme.Primary

@Composable
fun MessageBubble(
    content: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start
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
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (content.isEmpty()) {
                    // 光标占位 —— 流式开始但第一个 token 尚未到达
                    Text("…", color = textColor.copy(alpha = 0.4f))
                } else if (isStreaming) {
                    // 流式：段落级缓存 —— 每个已完成段落独立渲染 + 永久冻结
                    // 只有最末尾的活跃段参与 layout，长度恒定 → 不再"越往后越慢"
                    ParagraphStreamingText(text = content, isStreaming = true, textColor = textColor)
                } else {
                    // 完成态：Markdown + 选中支持
                    MarkdownRenderer(content, textColor, isStreaming = false)
                }
            }
        }
    }
}
