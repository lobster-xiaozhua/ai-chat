package com.example.aichat.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.data.model.Conversation
import com.example.aichat.ui.theme.Primary

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationDrawer(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onExport: (String) -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSearch: (String) -> Unit
) {
    var menuConvId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("历史对话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, contentDescription = "新建对话", tint = Primary) }
        }

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearch(it)
            },
            placeholder = { Text("搜索会话…", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无对话", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { conv ->
                    val isActive = conv.id == activeConversationId
                    val bgColor = if (isActive) Primary.copy(alpha = 0.1f) else Color.Transparent
                    Surface(
                        color = bgColor,
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onSelect(conv.id) },
                            onLongClick = { menuConvId = conv.id }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (conv.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "已置顶",
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp).padding(end = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conv.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(formatTime(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    // 长按弹出菜单：重命名 / 删除 / 置顶 / 导出
                    DropdownMenu(
                        expanded = menuConvId == conv.id,
                        onDismissRequest = { menuConvId = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (conv.isPinned) "取消置顶" else "置顶") },
                            onClick = { menuConvId = null; onTogglePin(conv.id, conv.isPinned) },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = { menuConvId = null; showRenameDialog = conv.id },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("导出") },
                            onClick = { menuConvId = null; onExport(conv.id) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = Color(0xFFD32F2F)) },
                            onClick = { menuConvId = null; showDeleteConfirmId = conv.id },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        // 重命名对话框
        showRenameDialog?.let { convId ->
            val conv = conversations.firstOrNull { it.id == convId }
            if (conv != null) {
                var newName by remember { mutableStateOf(conv.title) }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = null },
                    title = { Text("重命名对话") },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newName.isNotBlank()) onRename(convId, newName)
                            showRenameDialog = null
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = null }) { Text("取消") }
                    }
                )
            }
        }

        // 删除确认对话框
        showDeleteConfirmId?.let { convId ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmId = null },
                title = { Text("删除对话") },
                text = { Text("确定要删除这个对话吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(convId)
                            showDeleteConfirmId = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        )
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmId = null }) { Text("取消") }
                }
            )
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToAccount() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Primary, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("U", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("账号中心", modifier = Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToSettings() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("设置", modifier = Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000} 分钟前"
        diff < 86400000 -> "${diff / 3600000} 小时前"
        else -> "${diff / 86400000} 天前"
    }
}
