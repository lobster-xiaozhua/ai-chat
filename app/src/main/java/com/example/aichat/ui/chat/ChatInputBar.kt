package com.example.aichat.ui.chat

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aichat.ui.theme.Primary

@Composable
internal fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    currentModel: String,
    thinkMode: Boolean,
    searchMode: Boolean,
    jsonMode: Boolean,
    onToggleThink: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleJsonMode: () -> Unit,
    pendingImageUrls: List<String>,
    onRemoveImage: (String) -> Unit,
    pendingDocumentUrls: List<String>,
    pendingDocumentNames: Map<String, String>,
    onRemoveDocument: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    plusExpanded: Boolean,
    onTogglePlus: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFromCamera: () -> Unit,
    onPickDocument: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // —— 折叠面板：在输入栏上方展开 ——
        if (plusExpanded) {
            InlinePlusPanel(
                onPickPhoto = onPickPhoto,
                onPickFromCamera = onPickFromCamera,
                onPickDocument = onPickDocument,
                jsonMode = jsonMode,
                onToggleJsonMode = onToggleJsonMode
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    // "+" 按钮：输入框左侧，发送键旁边
                    Surface(
                        onClick = onTogglePlus,
                        shape = CircleShape,
                        color = if (plusExpanded) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "更多选项" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (plusExpanded) "✕" else "+",
                                color = if (plusExpanded) Primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                val tip = buildString {
                                    if (pendingImageUrls.isNotEmpty()) append("描述这些图片…")
                                    else if (pendingDocumentUrls.isNotEmpty()) append("描述这些文档…")
                                    else if (thinkMode) append("深度思考中，请输入…")
                                    else if (searchMode) append("联网搜索模式，请输入…")
                                    else append("输入消息…")
                                }
                                Text(tip, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    Surface(
                        onClick = if (isGenerating) onStop else onSend,
                        shape = CircleShape,
                        color = if (isGenerating) Color(0xFFD32F2F) else Primary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isGenerating) {
                                Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text("→", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeChip(label = "Think", isOn = thinkMode, onClick = onToggleThink, icon = "✓")
                    ModeChip(label = "Search", isOn = searchMode, onClick = onToggleSearch, icon = "🌐")

                    Spacer(modifier = Modifier.weight(1f))

                    ModeChip(label = friendlyModelName(currentModel), isOn = false, onClick = onModelClick, icon = "", isModelChip = true)
                }
            }
        }

        if (pendingImageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingImageUrls.take(6).forEach { url ->
                    Box(modifier = Modifier.size(64.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.matchParentSize()
                        ) {
                            AsyncImage(
                                model = Uri.parse(url),
                                contentDescription = "图片附件",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Surface(onClick = { onRemoveImage(url) }, shape = CircleShape, color = Color(0xFF666666), modifier = Modifier.size(18.dp).align(Alignment.TopEnd).semantics { contentDescription = "移除图片" }) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (pendingDocumentUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pendingDocumentUrls.forEach { url ->
                    val name = pendingDocumentNames[url] ?: "文档"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📄", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                            Text(text = name.take(20), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Surface(onClick = { onRemoveDocument(url) }, shape = CircleShape, color = Color(0xFF666666), modifier = Modifier.size(16.dp).padding(start = 4.dp).semantics { contentDescription = "移除文档" }) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("×", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModeChip(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    icon: String = "",
    isModelChip: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = when {
            isOn && !isModelChip -> Primary
            isModelChip && !isOn -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon.isNotEmpty()) {
                Text(icon, fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp), color = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (isOn || isModelChip) FontWeight.Medium else FontWeight.Normal,
                color = if (isOn && !isModelChip) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 输入栏上方的内联折叠面板 —— 替代全屏 PlusBottomSheet */
@Composable
internal fun InlinePlusPanel(
    onPickPhoto: () -> Unit,
    onPickFromCamera: () -> Unit,
    onPickDocument: () -> Unit,
    jsonMode: Boolean,
    onToggleJsonMode: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SheetAction(icon = "📷", label = "相机", onClick = onPickFromCamera)
                SheetAction(icon = "🖼️", label = "相册", onClick = onPickPhoto)
                SheetAction(icon = "📄", label = "文档", onClick = onPickDocument)
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("{ } JSON 结构化输出", modifier = Modifier.weight(1f), fontSize = 14.sp)
                androidx.compose.material3.Switch(checked = jsonMode, onCheckedChange = { onToggleJsonMode() })
            }
        }
    }
}

@Composable
internal fun SheetAction(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick, onClickLabel = label).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(icon, fontSize = 24.sp)
            }
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
    }
}
