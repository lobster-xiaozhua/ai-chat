# UI/UX 及底层 Bug 修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复审查报告中所有致命和中等级别的 UI/UX 及底层 Bug，不引入新 Bug。

**Architecture:** 按文件分组修改，每个 Task 聚焦一个文件或一个紧密关联的跨文件改动。修改顺序从底层（ViewModel/数据层）到上层（UI），确保每步可编译。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, StateFlow

---

## 文件变更总览

| 文件 | 变更类型 | 职责 |
|------|----------|------|
| `ChatViewModel.kt` | 修改 | 消除 historyForApi 双源问题；消息 ID 同步；首次消息更新标题；pendingImages/Documents 改用 StateFlow |
| `ChatScreen.kt` | 修改 | TopAppBar 标题动态显示；输入框 maxLines 限制；发送按钮/Plus按钮改图标；ModeChip 图标修正；示例发送清空输入框；重新生成传消息ID；模型选择弹窗改 ModalBottomSheet；emoji 替换为 Material Icons；图片预览显示全部 |
| `MessageBubble.kt` | 修改 | Primary 颜色改用 MaterialTheme |
| `MarkdownRenderer.kt` | 修改 | 删除文件底部硬编码 Primary，改用参数传入 |
| `SettingsScreen.kt` | 修改 | Primary/error 颜色改用 MaterialTheme；移除无效的语言/字号设置项 |
| `AccountScreen.kt` | 修改 | 精简为功能开发中提示页面 |
| `ConversationListViewModel.kt` | 修改 | 新增 updateConversationTitle 方法 |
| `ChatRepository.kt` | 修改 | 无需修改（sendMessageAndUpdateConversation 已有，只是未被调用） |

---

### Task 1: ChatViewModel — 消除 historyForApi 双源不一致

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ChatViewModel.kt`

**问题:** historyForApi 与 _messages 是两套独立数据源，删除/重新生成消息时索引计算可能错位。

**方案:** 将 historyForApi 改为 _messages 的派生计算属性，每次发送消息时从 _messages 重新构建，而非独立维护。

- [ ] **Step 1: 添加 buildHistoryFromMessages 辅助方法，移除 historyForApi 字段**

在 ChatViewModel 中：

```kotlin
// 删除: private var historyForApi = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())

// 新增: 从消息列表构建 API 历史
private fun List<Message>.toApiHistory(): List<Pair<String, String>> =
    filter { it.role == "user" || it.role == "assistant" }
        .map { it.role to it.content }
```

- [ ] **Step 2: 修改 setConversation 方法**

将 `historyForApi = ...` 替换为不再需要赋值：

```kotlin
fun setConversation(conversationId: String) {
    if (_currentConversationId.value == conversationId && _messages.value.isNotEmpty()) return
    generationJob?.cancel()
    generationJob = null
    _isGenerating.value = false
    _streamingAssistant.value = null
    _error.value = null
    _lastGenerationFailed.value = false
    clearPendingImages()
    clearDocuments()

    viewModelScope.launch {
        val history = chatRepository.getMessagesAsList(conversationId)
        _messages.value = history
        _currentConversationId.value = conversationId
        // historyForApi 不再需要，sendMessage 时从 _messages 派生
        Log.d(TAG, "Loaded conversation $conversationId with ${history.size} messages")
    }
}
```

- [ ] **Step 3: 修改 sendMessage 方法**

将所有 `historyForApi` 引用替换为 `_messages.value.toApiHistory()`：

```kotlin
fun sendMessage(text: String) {
    val images = _pendingImageUrls.toList()
    val documents = _pendingDocumentUrls.toList()
    val docNames = _pendingDocumentNames.toMap()
    val convId = _currentConversationId.value ?: return
    if (text.isBlank() && images.isEmpty() && documents.isEmpty()) return

    _pendingImageUrls.clear()
    _pendingDocumentUrls.clear()
    _pendingDocumentNames.clear()
    clearDraft(convId)
    _lastGenerationFailed.value = false

    val now = System.currentTimeMillis()
    val initialTextForHistory = text
    val userMsg = Message(
        conversationId = convId,
        role = "user",
        content = initialTextForHistory,
        imageUrls = Message.encodeImageUrls(images),
        timestamp = now
    )

    _messages.value = _messages.value + userMsg
    // 不再需要: historyForApi.add(...)
    viewModelScope.launch {
        val insertedId = chatRepository.insertMessage(userMsg)
        // 更新消息的真实 ID
        if (insertedId > 0) {
            _messages.value = _messages.value.map {
                if (it.timestamp == now && it.role == "user" && it.id == 0L) it.copy(id = insertedId) else it
            }
        }
    }

    // 首次消息时更新会话标题
    val userMsgCount = _messages.value.count { it.role == "user" }
    if (userMsgCount == 1) {
        viewModelScope.launch {
            val title = text.take(20).replace("\n", " ")
            conversationListViewModel.updateConversationTitle(convId, title)
        }
    }

    _isGenerating.value = true
    _error.value = null
    _streamingAssistant.value = ""

    generationJob = viewModelScope.launch {
        val builder = StringBuilder(2048)
        var lastEmitNs = 0L
        val throttleNs = 50_000_000L
        var tokensSinceEmit = 0

        try {
            val apiKey = settingsRepository.getApiKey()
            if (apiKey.isBlank()) throw com.example.aichat.data.repository.ApiException.Unauthorized("请先在设置中配置 API Key")

            val temperature = _temperatureStr.value.toDoubleOrNull() ?: 1.0
            val dataUriList = if (images.isNotEmpty()) images.toDataUriList(context, maxKb = 512) else emptyList()

            val docParts = mutableListOf<String>()
            for (uriString in documents) {
                val uri = try { android.net.Uri.parse(uriString) } catch (_: Exception) { null } ?: continue
                val extracted = uri.extractText(context)
                val displayName = docNames[uriString] ?: uri.getFileName(context)
                if (extracted != null) {
                    docParts.add("[文档：$displayName]\n---\n$extracted\n---\n")
                } else {
                    docParts.add("[文档：$displayName（内容无法自动读取，请用户提供文本）]\n")
                }
            }
            val combinedUserText = if (docParts.isNotEmpty()) {
                (docParts.joinToString("") + "\n" + text).trim()
            } else text

            // 构建 API 历史：用 _messages 派生，文档内容合并到 user 消息
            val apiHistory = _messages.value.toApiHistory().toMutableList()
            if (docParts.isNotEmpty()) {
                val lastUserIdx = apiHistory.indexOfLast { it.first == "user" }
                if (lastUserIdx >= 0) {
                    apiHistory[lastUserIdx] = "user" to combinedUserText
                }
            }

            aiRepository.streamChat(
                messages = apiHistory.toList(),
                systemPrompt = _systemPrompt.value,
                baseUrl = _baseUrl.value,
                model = _currentModel.value,
                apiKey = apiKey,
                temperature = temperature,
                jsonMode = _jsonMode.value,
                userImageUrls = dataUriList,
                thinkMode = _thinkMode.value,
                searchMode = _searchMode.value
            ).collect { token ->
                builder.append(token)
                tokensSinceEmit++
                val now2 = System.nanoTime()
                if (now2 - lastEmitNs > throttleNs || tokensSinceEmit >= 3) {
                    _streamingAssistant.value = builder.toString()
                    lastEmitNs = now2
                    tokensSinceEmit = 0
                }
            }

            val finalContent = builder.toString()
            if (finalContent.isNotEmpty()) {
                _streamingAssistant.value = finalContent
                val finalMsg = Message(
                    conversationId = convId,
                    role = "assistant",
                    content = finalContent,
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + finalMsg
                viewModelScope.launch {
                    val insertedId = chatRepository.insertMessage(finalMsg)
                    if (insertedId > 0) {
                        _messages.value = _messages.value.map {
                            if (it.timestamp == finalMsg.timestamp && it.role == "assistant" && it.id == 0L) it.copy(id = insertedId) else it
                        }
                    }
                }
            }
        } catch (e: com.example.aichat.data.repository.ApiException) {
            _error.value = e.message ?: "发生错误"
            _lastGenerationFailed.value = true
            restorePendingAttachments(images, documents, docNames)
            Log.w(TAG, "API error: ${e.message}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            val partial = builder.toString()
            if (partial.isNotEmpty()) {
                val finalMsg = Message(
                    conversationId = convId,
                    role = "assistant",
                    content = partial,
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + finalMsg
                viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
            } else {
                restorePendingAttachments(images, documents, docNames)
            }
            throw e
        } catch (e: Exception) {
            _error.value = "消息生成失败：${e.message}"
            _lastGenerationFailed.value = true
            restorePendingAttachments(images, documents, docNames)
            Log.w(TAG, "Generation failed: ${e.message}")
        } finally {
            _streamingAssistant.value = null
            _isGenerating.value = false
        }
    }
}
```

- [ ] **Step 4: 简化 deleteMessage 方法**

不再需要计算 historyIdx，直接从 _messages 删除即可：

```kotlin
fun deleteMessage(messageId: Long) {
    val currentMessages = _messages.value
    val target = currentMessages.firstOrNull { it.id == messageId }
    if (target == null) {
        Log.w(TAG, "deleteMessage: message $messageId not found")
        return
    }
    _messages.value = currentMessages.filter { it.id != messageId }
    viewModelScope.launch {
        runCatching { chatRepository.deleteMessageById(messageId) }
            .onFailure { Log.w(TAG, "Failed to delete message $messageId: ${it.message}") }
            .onSuccess { Log.d(TAG, "Deleted message $messageId") }
    }
}
```

- [ ] **Step 5: 简化 regenerateLastAssistant 方法**

不再需要手动维护 historyForApi 索引：

```kotlin
fun regenerateLastAssistant() {
    val currentMessages = _messages.value
    val lastAssistant = currentMessages.lastOrNull { it.role == "assistant" } ?: run {
        Log.w(TAG, "regenerateLastAssistant: no assistant message found")
        return
    }
    val lastUserMsg = currentMessages.lastOrNull { it.role == "user" }

    _messages.value = currentMessages.filter { it.id != lastAssistant.id && it.id != lastUserMsg?.id }

    viewModelScope.launch {
        runCatching { chatRepository.deleteMessageById(lastAssistant.id) }
        lastUserMsg?.let { runCatching { chatRepository.deleteMessageById(it.id) } }
    }

    if (lastUserMsg != null) {
        Log.d(TAG, "Regenerating from user message: '${lastUserMsg.content.take(50)}...'")
        sendMessage(lastUserMsg.content)
    } else {
        Log.w(TAG, "regenerateLastAssistant: no user message to regenerate from")
        _error.value = "没有可重新生成的用户消息"
    }
}
```

- [ ] **Step 6: 添加 conversationListViewModel 依赖和 updateConversationTitle**

在 ChatViewModel 构造函数中添加 ConversationListViewModel 依赖：

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    private val conversationListViewModel: ConversationListViewModel,
    @ApplicationContext private val context: Context
) : ViewModel() {
```

**注意:** 由于 Hilt 不支持直接注入 @HiltViewModel 到另一个 ViewModel，需要改用 ChatRepository 直接更新标题。在 ChatViewModel 中添加：

```kotlin
// 替代方案：直接通过 ChatRepository 更新标题
private suspend fun updateConversationTitle(convId: String, title: String) {
    try {
        val conv = chatRepository.getConversations().first().firstOrNull { it.id == convId }
        if (conv != null && conv.title == "新对话") {
            chatRepository.updateConversation(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
            Log.d(TAG, "Updated conversation title: $title")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to update conversation title: ${e.message}")
    }
}
```

不添加 conversationListViewModel 依赖，保持原有注入不变。

- [ ] **Step 7: 将 pendingImages/Documents 改用 StateFlow**

替换 Compose SnapshotStateList/Map 为 StateFlow：

```kotlin
// 替换:
// private val _pendingImageUrls = mutableStateListOf<String>()
// val pendingImageUrls: SnapshotStateList<String> = _pendingImageUrls
// 为:
private val _pendingImageUrls = MutableStateFlow<List<String>>(emptyList())
val pendingImageUrls: StateFlow<List<String>> = _pendingImageUrls.asStateFlow()

// 替换:
// private val _pendingDocumentUrls = mutableStateListOf<String>()
// val pendingDocumentUrls: SnapshotStateList<String> = _pendingDocumentUrls
// private val _pendingDocumentNames = mutableStateMapOf<String, String>()
// val pendingDocumentNames: SnapshotStateMap<String, String> = _pendingDocumentNames
// 为:
private val _pendingDocumentUrls = MutableStateFlow<List<String>>(emptyList())
val pendingDocumentUrls: StateFlow<List<String>> = _pendingDocumentUrls.asStateFlow()
private val _pendingDocumentNames = MutableStateFlow<Map<String, String>>(emptyMap())
val pendingDocumentNames: StateFlow<Map<String, String>> = _pendingDocumentNames.asStateFlow()
```

更新所有引用方法：

```kotlin
fun addImageUrls(urls: List<String>) {
    _pendingImageUrls.value = (_pendingImageUrls.value + urls).distinct()
}

fun removeImageUrl(url: String) {
    _pendingImageUrls.value = _pendingImageUrls.value.filter { it != url }
}

fun clearPendingImages() {
    _pendingImageUrls.value = emptyList()
}

fun addDocumentUrls(urisWithNames: List<Pair<String, String>>) {
    val currentUrls = _pendingDocumentUrls.value
    val currentNames = _pendingDocumentNames.value.toMutableMap()
    val newUrls = currentUrls.toMutableList()
    for ((uri, name) in urisWithNames) {
        if (uri !in currentUrls) {
            newUrls.add(uri)
            currentNames[uri] = name.ifEmpty { "未命名文档" }
        }
    }
    _pendingDocumentUrls.value = newUrls
    _pendingDocumentNames.value = currentNames
}

fun removeDocumentUrl(uri: String) {
    _pendingDocumentUrls.value = _pendingDocumentUrls.value.filter { it != uri }
    val names = _pendingDocumentNames.value.toMutableMap()
    names.remove(uri)
    _pendingDocumentNames.value = names
}

fun clearDocuments() {
    _pendingDocumentUrls.value = emptyList()
    _pendingDocumentNames.value = emptyMap()
}
```

更新 restorePendingAttachments：

```kotlin
private fun restorePendingAttachments(
    images: List<String>,
    documents: List<String>,
    docNames: Map<String, String>
) {
    _pendingImageUrls.value = (_pendingImageUrls.value + images).distinct()
    val currentUrls = _pendingDocumentUrls.value
    val currentNames = _pendingDocumentNames.value.toMutableMap()
    val newUrls = currentUrls.toMutableList()
    for (uri in documents) {
        if (uri !in currentUrls) {
            newUrls.add(uri)
            docNames[uri]?.let { currentNames[uri] = it }
        }
    }
    _pendingDocumentUrls.value = newUrls
    _pendingDocumentNames.value = currentNames
}
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/chat/ChatViewModel.kt
git commit -m "fix: 消除 historyForApi 双源不一致，消息 ID 同步，pendingImages 改用 StateFlow"
```

---

### Task 2: ConversationListViewModel — 添加 updateConversationTitle

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ConversationListViewModel.kt`

- [ ] **Step 1: 添加 updateConversationTitle 方法**

```kotlin
fun updateConversationTitle(id: String, newTitle: String) {
    viewModelScope.launch {
        val conv = _conversations.value.firstOrNull { it.id == id } ?: return@launch
        if (conv.title != "新对话") return@launch
        chatRepository.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "Auto-updated conversation $id title to '$newTitle'")
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/chat/ConversationListViewModel.kt
git commit -m "feat: ConversationListViewModel 添加自动更新标题方法"
```

---

### Task 3: ChatScreen — 修复致命 UI Bug

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ChatScreen.kt`

- [ ] **Step 1: TopAppBar 标题动态显示**

将 `MainChatContent` 中 TopAppBar 的 title 从硬编码 "新对话" 改为接收参数：

在 `MainChatContent` 参数列表中添加 `conversationTitle: String`：

```kotlin
@Composable
private fun MainChatContent(
    messages: List<Message>,
    streamingText: String?,
    isGenerating: Boolean,
    lastGenerationFailed: Boolean,
    currentModel: String,
    conversationTitle: String,  // 新增
    // ... 其他参数不变
)
```

TopAppBar title 改为：

```kotlin
title = { Text(conversationTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
```

在 `ChatScreen` 中传入当前会话标题。需要从 conversations 中查找：

```kotlin
val currentConvTitle = conversations.firstOrNull { it.id == activeConversationId }?.title ?: "新对话"
```

调用处：

```kotlin
MainChatContent(
    // ... 其他参数
    conversationTitle = currentConvTitle,
    // ...
)
```

- [ ] **Step 2: 输入框添加 maxLines 限制**

将 BasicTextField 替换为支持 maxLines 的版本。添加 `minHeight` 和 `maxHeight` 约束：

```kotlin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp

// BasicTextField modifier 中添加高度约束
BasicTextField(
    value = inputText,
    onValueChange = onInputChange,
    modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
        .heightIn(min = 24.dp, max = 120.dp),
    textStyle = androidx.compose.ui.text.TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp
    ),
    maxLines = 6,
    // ... decorationBox 不变
)
```

- [ ] **Step 3: 发送按钮改用 Material Icon**

将 `"→"` 文字替换为 `Icons.Default.ArrowForward`（需添加 import）：

```kotlin
// 替换:
// Text("→", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
// 为:
Icon(Icons.Default.ArrowForward, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(16.dp))
```

添加 import：

```kotlin
import androidx.compose.material.icons.filled.ArrowForward
```

- [ ] **Step 4: Plus 按钮改用 Material Icon**

将 `"+"` / `"✕"` 文字替换为图标：

```kotlin
// 替换:
// Text(if (plusExpanded) "✕" else "+", ...)
// 为:
if (plusExpanded) {
    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Primary, modifier = Modifier.size(18.dp))
} else {
    Icon(Icons.Default.Add, contentDescription = "更多选项", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
}
```

添加 import：

```kotlin
import androidx.compose.material.icons.filled.Close
```

- [ ] **Step 5: ModeChip 图标修正**

Think 用 `Icons.Default.Psychology`（大脑图标），Search 用 `Icons.Default.Language`：

```kotlin
// 替换 ModeChip 调用:
ModeChip(label = "Think", isOn = thinkMode, onClick = onToggleThink, icon = "✓")
ModeChip(label = "Search", isOn = searchMode, onClick = onToggleSearch, icon = "🌐")
// 为:
ModeChip(label = "Think", isOn = thinkMode, onClick = onToggleThink, icon = Icons.Default.Psychology)
ModeChip(label = "Search", isOn = searchMode, onClick = onToggleSearch, icon = Icons.Default.Language)
```

修改 ModeChip 组件签名和实现，将 `icon: String` 改为 `icon: androidx.compose.ui.graphics.vector.ImageVector?`：

```kotlin
@Composable
private fun ModeChip(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isModelChip: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = when {
            isOn && !isModelChip -> MaterialTheme.colorScheme.primary
            isModelChip && !isOn -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
```

添加 import：

```kotlin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Language
```

- [ ] **Step 6: 示例发送清空输入框**

修复 `onSendExample` lambda，先清空再发送：

```kotlin
// 替换:
// onSendExample = { text -> inputText = text; chatViewModel.sendMessage(text.trim()) },
// 为:
onSendExample = { text ->
    inputText = ""
    chatViewModel.sendMessage(text.trim())
},
```

- [ ] **Step 7: 重新生成传消息 ID 而非总是最后一条**

在 ChatViewModel 中添加 `regenerateMessage` 方法：

```kotlin
fun regenerateMessage(messageId: Long) {
    val currentMessages = _messages.value
    val targetAssistant = currentMessages.firstOrNull { it.id == messageId } ?: run {
        Log.w(TAG, "regenerateMessage: message $messageId not found")
        return
    }
    if (targetAssistant.role != "assistant") return

    // 找到该 assistant 消息之前的最近一条 user 消息
    val assistantIdx = currentMessages.indexOf(targetAssistant)
    val userMsg = currentMessages.take(assistantIdx).lastOrNull { it.role == "user" }

    _messages.value = currentMessages.filter { it.id != targetAssistant.id && it.id != userMsg?.id }

    viewModelScope.launch {
        runCatching { chatRepository.deleteMessageById(targetAssistant.id) }
        userMsg?.let { runCatching { chatRepository.deleteMessageById(it.id) } }
    }

    if (userMsg != null) {
        Log.d(TAG, "Regenerating message $messageId from user: '${userMsg.content.take(50)}...'")
        sendMessage(userMsg.content)
    } else {
        Log.w(TAG, "regenerateMessage: no user message found before $messageId")
        _error.value = "没有可重新生成的用户消息"
    }
}
```

在 ChatScreen 中，长按菜单的"重新生成"改为调用 `regenerateMessage(msg.id)`：

```kotlin
if (msg.role == "assistant") {
    DropdownMenuItem(
        text = { Text("重新生成") },
        onClick = {
            chatViewModel.regenerateMessage(msg.id)
            showMsgMenu = false
            selectedMessage = null
        },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}
```

- [ ] **Step 8: InlinePlusPanel emoji 替换为 Material Icons**

修改 SheetAction 组件，将 `icon: String` 改为 `icon: ImageVector`：

```kotlin
@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick, onClickLabel = label).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
    }
}
```

调用处替换：

```kotlin
// 替换:
// SheetAction(icon = "📷", label = "相机", onClick = onPickFromCamera)
// SheetAction(icon = "🖼️", label = "相册", onClick = onPickPhoto)
// SheetAction(icon = "📄", label = "文档", onClick = onPickDocument)
// 为:
SheetAction(icon = Icons.Default.CameraAlt, label = "相机", onClick = onPickFromCamera)
SheetAction(icon = Icons.Default.PhotoLibrary, label = "相册", onClick = onPickPhoto)
SheetAction(icon = Icons.Default.Description, label = "文档", onClick = onPickDocument)
```

添加 import：

```kotlin
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Description
```

- [ ] **Step 9: 图片预览显示全部（移除 take(6) 限制）**

```kotlin
// 替换:
// pendingImageUrls.take(6).forEach { url ->
// 为:
pendingImageUrls.forEach { url ->
```

- [ ] **Step 10: Primary 颜色改用 MaterialTheme.colorScheme.primary**

在 ChatScreen 中，将所有 `Primary` 引用替换为 `MaterialTheme.colorScheme.primary`。涉及的行：

- 模型选择弹窗中 `Primary` → `MaterialTheme.colorScheme.primary`
- ModeChip 中 `Primary` → `MaterialTheme.colorScheme.primary`
- Plus 按钮 `Primary.copy(alpha = 0.15f)` → `MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)`
- Plus 按钮 `Primary` → `MaterialTheme.colorScheme.primary`
- 发送按钮 `Primary` → `MaterialTheme.colorScheme.primary`
- ConversationDrawer 中 `Primary` → `MaterialTheme.colorScheme.primary`
- EmptyState 中 "✨ AI 助手" → "AI 助手"（移除 emoji）

移除 `import com.example.aichat.ui.theme.Primary`

- [ ] **Step 11: 错误颜色改用 MaterialTheme.colorScheme.error**

将所有 `Color(0xFFD32F2F)` 替换为 `MaterialTheme.colorScheme.error`。涉及行：

- 删除菜单项文字颜色
- 删除菜单项图标 tint
- 停止按钮颜色
- ConversationDrawer 删除菜单项

- [ ] **Step 12: 模型选择弹窗改用 ModalBottomSheet**

将自定义的 Surface+Box 遮罩替换为标准 ModalBottomSheet：

```kotlin
// 替换整个 if (showModelSelector) { ... } 块为:
if (showModelSelector) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { showModelSelector = false },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("选择模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            if (quickModels.size == 1 && quickModels[0] == currentModel) {
                Text("尚未选择常用模型，点击下方按钮前往选择。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            quickModels.forEach { id ->
                val selected = currentModel == id
                Surface(
                    onClick = { chatViewModel.setModel(id); showModelSelector = false },
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(friendlyModelName(id), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp).weight(1f), fontSize = 13.sp)
                        if (selected) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { showModelSelector = false; onNavigateToModelPicker() },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
                    Text("选择更多模型 (搜索 / 多选)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```

- [ ] **Step 13: 更新 pendingImageUrls/pendingDocumentUrls 的读取方式**

由于 Task 1 中将它们改为了 StateFlow，ChatScreen 中的读取需要更新：

```kotlin
// 替换:
// val pendingImageUrls = chatViewModel.pendingImageUrls
// val pendingDocumentUrls = chatViewModel.pendingDocumentUrls
// val pendingDocumentNames = chatViewModel.pendingDocumentNames
// 为:
val pendingImageUrls by chatViewModel.pendingImageUrls.collectAsState()
val pendingDocumentUrls by chatViewModel.pendingDocumentUrls.collectAsState()
val pendingDocumentNames by chatViewModel.pendingDocumentNames.collectAsState()
```

同时更新 MainChatContent 和 ChatInputBar 的参数类型，将 `SnapshotStateList` / `SnapshotStateMap` 改为 `List` / `Map`。

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/chat/ChatScreen.kt
git commit -m "fix: ChatScreen 致命 UI Bug 修复（标题/输入框/图标/重新生成/颜色）"
```

---

### Task 4: MessageBubble — Primary 颜色改用 MaterialTheme

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/MessageBubble.kt`

- [ ] **Step 1: 替换 Primary 引用**

```kotlin
// 替换:
// val bgColor = if (isUser) Primary else MaterialTheme.colorScheme.surfaceVariant
// 为:
val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
```

移除 `import com.example.aichat.ui.theme.Primary`

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/chat/MessageBubble.kt
git commit -m "fix: MessageBubble Primary 颜色改用 MaterialTheme.colorScheme.primary"
```

---

### Task 5: MarkdownRenderer — 移除硬编码 Primary

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/MarkdownRenderer.kt`

- [ ] **Step 1: 删除文件底部硬编码 Primary，链接颜色改为参数传入**

删除文件最后一行：

```kotlin
// 删除: private val Primary = Color(0xFF6750A4)
```

修改 `MarkdownRenderer` 签名，添加 linkColor 参数：

```kotlin
@Composable
fun MarkdownRenderer(text: String, textColor: Color, isStreaming: Boolean = false, linkColor: Color = MaterialTheme.colorScheme.primary) {
```

在 `rememberParsed` 中传入 linkColor：

```kotlin
val key = text to textColor.hashCode()
val cached = MarkdownCache.get(key)
return cached ?: parseMarkdown(text, textColor, linkColor).also { MarkdownCache.put(key, it) }
```

更新 `parseMarkdown` 和 `parseInline` 签名，添加 linkColor 参数，将 `Primary` 替换为 `linkColor`：

```kotlin
private fun parseMarkdown(text: String, color: Color, linkColor: Color): AnnotatedString {
    // ... 内部调用 parseInline(remaining, color, linkColor)
}

private fun parseInline(text: String, color: Color, linkColor: Color): AnnotatedString {
    // ... pushStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))
}
```

添加 import：

```kotlin
import androidx.compose.material3.MaterialTheme
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/chat/MarkdownRenderer.kt
git commit -m "fix: MarkdownRenderer 移除硬编码 Primary，链接颜色改为参数传入"
```

---

### Task 6: SettingsScreen — 移除无效设置项，颜色修正

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 移除语言和字号设置项（功能未实现）**

删除通用设置分组中的语言和字号 DropdownSelector：

```kotlin
// 删除以下代码块:
//                    Spacer(modifier = Modifier.height(8.dp))
//                    DropdownSelector(
//                        label = "语言",
//                        ...
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    DropdownSelector(
//                        label = "字号",
//                        ...
//                    )
```

- [ ] **Step 2: Primary 颜色改用 MaterialTheme.colorScheme.primary**

替换所有 `Primary` 引用为 `MaterialTheme.colorScheme.primary`：

- Slider colors 中 `thumbColor = Primary` → `MaterialTheme.colorScheme.primary`
- Slider colors 中 `activeTrackColor = Primary` → `MaterialTheme.colorScheme.primary`
- 数据统计中 `color = Primary` → `MaterialTheme.colorScheme.primary`

移除 `import com.example.aichat.ui.theme.Primary`

- [ ] **Step 3: 错误颜色改用 MaterialTheme.colorScheme.error**

替换 `Color(0xFFD32F2F)` 为 `MaterialTheme.colorScheme.error`：

- 清除对话文字颜色
- 清除确认对话框按钮颜色
- 清理缓存对话框按钮颜色

- [ ] **Step 4: 灰色文字改用主题颜色**

替换 `Color.Gray` 为 `MaterialTheme.colorScheme.onSurfaceVariant`：

- DropdownSelector 中的 "▼"
- "自定义大模型配置" 文字
- "›" 箭头

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/settings/SettingsScreen.kt
git commit -m "fix: SettingsScreen 移除无效设置项，颜色改用 MaterialTheme"
```

---

### Task 7: AccountScreen — 精简为功能开发中提示

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/settings/AccountScreen.kt`

- [ ] **Step 1: 精简页面内容**

将整个页面替换为简洁的"功能开发中"提示，移除虚假的账号信息：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("账号管理", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("账号功能开发中", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("敬请期待", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设置", modifier = Modifier.weight(1f))
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

添加缺失的 import：

```kotlin
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
```

移除 `import com.example.aichat.ui.theme.Primary`

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/settings/AccountScreen.kt
git commit -m "fix: AccountScreen 精简为功能开发中提示，移除虚假账号信息"
```

---

### Task 8: AboutScreen — 修复隐私政策 404 和开源许可链接

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/settings/AboutScreen.kt`

- [ ] **Step 1: 修复隐私政策链接**

将 `https://example.com/privacy` 替换为 GitHub 仓库的隐私政策页面或移除链接：

```kotlin
AboutRow(
    title = "隐私政策",
    onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lobster-xiaozhua/ai-chat/blob/main/PRIVACY.md")))
    }
)
```

- [ ] **Step 2: 修复开源许可链接**

将通用链接替换为 Android 内置的许可页面或 GitHub 仓库的 LICENSE：

```kotlin
AboutRow(
    title = "开源许可",
    onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lobster-xiaozhua/ai-chat/blob/main/LICENSE")))
    }
)
```

- [ ] **Step 3: Primary 颜色改用 MaterialTheme**

替换 AboutScreen 中所有 `Primary` 引用为 `MaterialTheme.colorScheme.primary`。

移除 `import com.example.aichat.ui.theme.Primary`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/aichat/ui/settings/AboutScreen.kt
git commit -m "fix: AboutScreen 修复隐私政策/开源许可链接，颜色改用 MaterialTheme"
```

---

### Task 9: 最终验证 — 编译检查

- [ ] **Step 1: 运行 Gradle 编译**

```bash
cd /workspace && ./gradlew assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 修复编译错误（如有）**

根据编译输出修复遗漏的 import 或类型不匹配。

- [ ] **Step 3: 推送所有变更**

```bash
git push
```

---

## 自审清单

1. **Spec 覆盖率:**
   - ✅ historyForApi 双源不一致 → Task 1
   - ✅ 消息 ID 不同步 → Task 1 Step 3
   - ✅ 会话标题不更新 → Task 1 Step 3 + Task 2
   - ✅ TopAppBar 标题不更新 → Task 3 Step 1
   - ✅ 输入框无限撑高 → Task 3 Step 2
   - ✅ 发送按钮用文字 → Task 3 Step 3
   - ✅ Plus 按钮用文字 → Task 3 Step 4
   - ✅ ModeChip 图标语义错误 → Task 3 Step 5
   - ✅ 示例发送残留输入框 → Task 3 Step 6
   - ✅ 重新生成错误消息 → Task 3 Step 7
   - ✅ emoji 替换为图标 → Task 3 Step 8
   - ✅ 图片预览只显示6张 → Task 3 Step 9
   - ✅ Primary 硬编码 → Task 3/4/5/6/8
   - ✅ 错误颜色硬编码 → Task 3/6
   - ✅ 语言/字号设置无效 → Task 6
   - ✅ 账号页面假数据 → Task 7
   - ✅ 隐私政策 404 → Task 8
   - ✅ ViewModel 持有 Compose 状态 → Task 1 Step 7
   - ✅ 模型选择弹窗不规范 → Task 3 Step 12

2. **占位符扫描:** 无 TBD/TODO/占位符

3. **类型一致性:**
   - pendingImageUrls: `SnapshotStateList<String>` → `StateFlow<List<String>>` (Task 1 + Task 3 Step 13)
   - pendingDocumentUrls: 同上
   - pendingDocumentNames: `SnapshotStateMap<String, String>` → `StateFlow<Map<String, String>>`
   - ModeChip icon: `String` → `ImageVector?`
   - SheetAction icon: `String` → `ImageVector`
   - MarkdownRenderer 新增 `linkColor` 参数
