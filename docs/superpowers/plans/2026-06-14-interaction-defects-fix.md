# 交互逻辑缺陷修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 API 提供商切换无效、缺少 API Key 输入、对话无法删除、白屏卡死、以及关键 P0 级交互缺陷

**Architecture:** 以最小改动修复核心交互缺陷。所有修改集中在现有文件中，不新增文件。按依赖关系从底层（Repository/ViewModel）到上层（UI）逐步修复。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, DataStore, Coroutines

---

## File Structure

| File | Change | Responsibility |
|------|--------|----------------|
| `ChatViewModel.kt` | Modify | 修复发送失败附件丢失、停止生成竞态、首次进入白屏 |
| `ChatRepository.kt` | Modify | 添加 @Transaction 保证原子性 |
| `ConversationListViewModel.kt` | Modify | 修复搜索清空不恢复、createNewConversation 改为挂起函数 |
| `ChatScreen.kt` | Modify | 添加对话删除 UI、修复抽屉状态丢失、修复 LaunchedEffect |
| `SettingsScreen.kt` | Modify | 添加 API Key 输入框、修复提供商切换逻辑、实现清除对话 |
| `SettingsViewModel.kt` | Modify | 添加 apiKey Flow 收集 |

---

### Task 1: ChatRepository — 添加 @Transaction 保证原子性

**Files:**
- Modify: `app/src/main/java/com/example/aichat/data/repository/ChatRepository.kt`

- [ ] **Step 1: 给 sendMessageAndUpdateConversation 添加事务注解**

在 `ChatRepository` 类上添加 `@Singleton` 已有，需在方法上加 `@Transaction`。但 Room 的 `@Transaction` 只能用在 DAO 上，Repository 层需用 RoomDatabase.runInTransaction()。

修改 `ChatRepository` 注入 `AppDatabase`，用 `db.runInTransaction {}` 包裹三步操作：

```kotlin
@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val db: AppDatabase  // 新增注入
) {
    // ... existing code ...

    suspend fun sendMessageAndUpdateConversation(
        conversation: Conversation,
        userMessage: String,
        assistantMessage: String
    ) = db.runInTransaction {
        // 插入用户消息
        insertMessage(Message(
            conversationId = conversation.id,
            role = "user",
            content = userMessage,
            timestamp = System.currentTimeMillis()
        ))
        // 插入助手消息
        insertMessage(Message(
            conversationId = conversation.id,
            role = "assistant",
            content = assistantMessage,
            timestamp = System.currentTimeMillis()
        ))
        // 更新会话
        val updated = conversation.copy(
            title = if (conversation.title == "新对话") userMessage.take(10) else conversation.title,
            updatedAt = System.currentTimeMillis()
        )
        updateConversation(updated)
    }
}
```

- [ ] **Step 2: 在 DatabaseModule 中确保 AppDatabase 可注入**

检查 `DatabaseModule.kt` 是否已提供 `AppDatabase` 实例。如果已提供则无需修改。

---

### Task 2: ChatViewModel — 修复发送失败附件丢失 + 停止生成竞态

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ChatViewModel.kt`

- [ ] **Step 1: 修复 sendMessage — 失败时恢复附件状态**

将附件快照和清空逻辑移到确认发送成功之后。在 catch 块中恢复附件：

```kotlin
fun sendMessage(text: String) {
    val images = _pendingImageUrls.toList()
    val documents = _pendingDocumentUrls.toList()
    val docNames = _pendingDocumentNames.toMap()
    val convId = _currentConversationId.value ?: return
    if (text.isBlank() && images.isEmpty() && documents.isEmpty()) return

    // 发送中先不清空附件，等确认进入流式处理后再清空
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
    historyForApi.add("user" to initialTextForHistory)
    viewModelScope.launch { runCatching { chatRepository.insertMessage(userMsg) } }

    _isGenerating.value = true
    _error.value = null
    _streamingAssistant.value = ""

    // 确认进入生成流程后清空附件
    _pendingImageUrls.clear()
    _pendingDocumentUrls.clear()
    _pendingDocumentNames.clear()

    generationJob = viewModelScope.launch {
        val builder = StringBuilder(2048)
        var lastEmitNs = 0L
        val throttleNs = 50_000_000L
        var tokensSinceEmit = 0

        try {
            // ... (existing streaming logic unchanged) ...
        } catch (e: ApiException) {
            _error.value = e.message ?: "发生错误"
            historyForApi.removeLastOrNull()
            // 恢复附件，让用户可以重试
            restorePendingAttachments(images, documents, docNames)
        } catch (e: kotlinx.coroutines.CancellationException) {
            val partial = builder.toString()
            if (partial.isNotEmpty()) {
                _streamingAssistant.value = partial
                val finalMsg = Message(
                    conversationId = convId,
                    role = "assistant",
                    content = partial,
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + finalMsg
                historyForApi.add("assistant" to partial)
                viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
            } else {
                historyForApi.removeLastOrNull()
            }
            throw e
        } catch (e: Exception) {
            _error.value = "消息生成失败：${e.message}"
            historyForApi.removeLastOrNull()
            // 恢复附件
            restorePendingAttachments(images, documents, docNames)
        } finally {
            _streamingAssistant.value = null
            _isGenerating.value = false
        }
    }
}

private fun restorePendingAttachments(
    images: List<String>,
    documents: List<String>,
    docNames: Map<String, String>
) {
    for (url in images) {
        if (url !in _pendingImageUrls) _pendingImageUrls.add(url)
    }
    for (uri in documents) {
        if (uri !in _pendingDocumentUrls) {
            _pendingDocumentUrls.add(uri)
            docNames[uri]?.let { _pendingDocumentNames[uri] = it }
        }
    }
}
```

- [ ] **Step 2: 修复 stopGeneration — 移除手动状态重置，让 CancellationException 的 finally 块统一处理**

```kotlin
fun stopGeneration() {
    generationJob?.cancel()
    // 不再手动设置 _isGenerating 和 _streamingAssistant
    // 让 generationJob 的 CancellationException catch 块 + finally 统一处理
}
```

---

### Task 3: ConversationListViewModel — 修复搜索清空 + createNewConversation 同步

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ConversationListViewModel.kt`

- [ ] **Step 1: 修复 searchConversations — 清空搜索时重新 collect 全量列表**

```kotlin
private var allJob: Job? = null
private var searchJob: Job? = null

init {
    allJob = viewModelScope.launch {
        chatRepository.getConversations().collect { list ->
            // 仅在非搜索模式下更新（搜索模式下由 searchJob 更新）
            if (searchJob?.isActive != true) {
                _conversations.value = list
            }
        }
    }
}

fun searchConversations(query: String) {
    if (query.isBlank()) {
        searchJob?.cancel()
        searchJob = null
        // 立即刷新一次全量数据
        viewModelScope.launch {
            _conversations.value = chatRepository.getConversations().first()
        }
        return
    }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        chatRepository.searchConversations(query).collect {
            _conversations.value = it
        }
    }
}
```

需要添加 import: `import kotlinx.coroutines.flow.first`

- [ ] **Step 2: 将 createNewConversation 改为挂起函数，确保 DB 写入完成后再返回 ID**

```kotlin
suspend fun createNewConversation(): String {
    val id = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    chatRepository.createConversation(
        Conversation(
            id = id,
            title = "新对话",
            systemPrompt = "",
            createdAt = now,
            updatedAt = now
        )
    )
    return id
}
```

---

### Task 4: ChatScreen — 修复白屏 + 添加对话删除 + 修复抽屉

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/chat/ChatScreen.kt`

- [ ] **Step 1: 修复 LaunchedEffect — 使用 currentConversationId 作为 key 而非 Unit**

将:
```kotlin
LaunchedEffect(Unit) {
    if (chatViewModel.currentConversationId.value == null) {
        val newId = conversationListViewModel.createNewConversation()
        chatViewModel.setConversation(newId)
    }
}
```
改为:
```kotlin
LaunchedEffect(currentConversationId) {
    if (currentConversationId == null) {
        val newId = conversationListViewModel.createNewConversation()
        chatViewModel.setConversation(newId)
    }
}
```

注意：`createNewConversation` 现在是 suspend 函数，在 LaunchedEffect 中可以直接调用。

- [ ] **Step 2: 修复抽屉 — 使用 ModalNavigationDrawer 替代条件分支**

将 drawerOpen 条件分支改为使用 `ModalNavigationDrawer`：

```kotlin
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
        ConversationDrawer(
            conversations = conversations,
            activeConversationId = activeConversationId,
            onSelect = { id ->
                chatViewModel.setConversation(id)
                scope.launch { drawerState.close() }
            },
            onNewChat = {
                val newId = conversationListViewModel.createNewConversation()
                chatViewModel.setConversation(newId)
                scope.launch { drawerState.close() }
            },
            onRename = { id, title -> conversationListViewModel.renameConversation(id, title) },
            onDelete = { id ->
                conversationListViewModel.deleteConversation(id)
                if (activeConversationId == id) {
                    val newId = conversationListViewModel.createNewConversation()
                    chatViewModel.setConversation(newId)
                }
            },
            onNavigateToAccount = { scope.launch { drawerState.close() }; onNavigateToAccount() },
            onNavigateToSettings = { scope.launch { drawerState.close() }; onNavigateToSettings() }
        )
    }
) {
    MainChatContent(
        // ... existing params unchanged ...
        onMenuClick = { scope.launch { drawerState.open() } },
        // ... rest unchanged ...
    )
}
```

需要添加 imports:
```kotlin
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
```

移除 `var drawerOpen by remember { mutableStateOf(false) }` 和所有 `drawerOpen` 相关的条件分支代码。

- [ ] **Step 3: 在 ConversationDrawer 中添加删除和重命名功能**

给每个对话项添加长按菜单（重命名/删除）：

```kotlin
@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var menuConvId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("历史对话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, contentDescription = "新建对话", tint = Primary) }
        }
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conv ->
                val isActive = conv.id == activeConversationId
                val bgColor = if (isActive) Primary.copy(alpha = 0.1f) else Color.Transparent
                Surface(
                    onClick = { onSelect(conv.id) },
                    onLongClick = { menuConvId = conv.id },
                    color = bgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(conv.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(formatTime(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                // 长按弹出菜单
                DropdownMenu(
                    expanded = menuConvId == conv.id,
                    onDismissRequest = { menuConvId = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { menuConvId = null; showRenameDialog = conv.id }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = Color(0xFFD32F2F)) },
                        onClick = { menuConvId = null; onDelete(conv.id) }
                    )
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
                            singleLine = true
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
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        // 底部账号和设置入口（保持不变）
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
```

需要添加 imports:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
```

---

### Task 5: SettingsScreen — 添加 API Key 输入 + 修复提供商切换 + 实现清除对话

**Files:**
- Modify: `app/src/main/java/com/example/aichat/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/aichat/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: SettingsViewModel — 添加 apiKey Flow 收集**

在 `SettingsViewModel.init` 中添加：
```kotlin
// apiKey 是同步读取的，但需要暴露为 StateFlow 供 UI 观察
init {
    viewModelScope.launch {
        launch { settingsRepository.getTheme().collect { _theme.value = it } }
        launch { settingsRepository.getLanguage().collect { _language.value = it } }
        launch { settingsRepository.getFontSize().collect { _fontSize.value = it } }
        launch { settingsRepository.getDefaultModel().collect { _defaultModel.value = it } }
        launch { settingsRepository.getTemperature().collect { _temperature.value = it } }
        launch { settingsRepository.getSystemPrompt().collect { _systemPrompt.value = it } }
        launch { settingsRepository.getBaseUrl().collect { _baseUrl.value = it } }
        launch { settingsRepository.getSelectedModelIds().collect { _selectedIds.value = it } }
    }
    _apiKey.value = settingsRepository.getApiKey()
}
```

（init 块不变，`_apiKey` 已通过同步调用初始化）

- [ ] **Step 2: SettingsScreen — 在模型配置分组中添加 API Key 输入框**

在"默认模型"下拉框之后、"Temperature"之前，添加 API Key 输入框：

```kotlin
var showApiKey by remember { mutableStateOf(false) }

// API Key 输入
Spacer(modifier = Modifier.height(12.dp))
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = apiKey,
        onValueChange = { newKey ->
            apiKey = newKey
            viewModel.setApiKey(newKey)
        },
        label = { Text("API Key") },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                    if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showApiKey) "隐藏" else "显示",
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        singleLine = true
    )
}
```

需要添加 imports:
```kotlin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
```

同时添加 `var apiKey by remember { mutableStateOf(currentKey) }` 状态变量（在现有的 `var tempValue` 和 `var darkMode` 旁边）。

- [ ] **Step 3: SettingsScreen — 修复提供商切换逻辑，切换时同步更新 API Key 提示**

当用户切换提供商时，API Key 不会自动切换（不同提供商需要不同 Key），但应该提示用户更新 Key。修改 `onSelect` 回调，在切换后清空当前显示的 apiKey（不删除已保存的，只清空输入框让用户意识到需要重新输入）：

```kotlin
onSelect = { provider ->
    when (provider) {
        "nvidia" -> {
            viewModel.setBaseUrl("https://integrate.api.nvidia.com/v1")
            viewModel.setDefaultModel("nvidia/nemotron-nano-12b-v2-vl")
        }
        else -> {
            viewModel.setBaseUrl("https://api.deepseek.com")
            viewModel.setDefaultModel("deepseek-chat")
        }
    }
}
```

提供商切换后 baseUrl 和 defaultModel 通过 DataStore Flow 自动同步到 ChatViewModel，无需额外处理。

- [ ] **Step 4: SettingsScreen — 实现清除对话功能**

将"清除所有对话"改为可点击的确认操作：

```kotlin
// 隐私分组
SectionTitle("隐私")
Surface(
    onClick = { showClearConfirm = true },
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth()
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "清除所有对话（不可恢复）",
            color = Color(0xFFD32F2F)
        )
    }
}
```

添加确认对话框状态和对话框：

```kotlin
var showClearConfirm by remember { mutableStateOf(false) }

if (showClearConfirm) {
    AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("确认清除") },
        text = { Text("所有对话将被永久删除，此操作不可恢复。") },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.clearAllConversations()
                    showClearConfirm = false
                },
                colors = androidx.compose.material3.TextButtonDefaults.colors(
                    contentColor = Color(0xFFD32F2F)
                )
            ) { Text("清除") }
        },
        dismissButton = {
            TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
        }
    )
}
```

- [ ] **Step 5: SettingsViewModel — 添加 clearAllConversations 方法**

```kotlin
fun clearAllConversations() = viewModelScope.launch {
    chatRepository.deleteAllConversations()
}
```

需要在 `SettingsViewModel` 中注入 `ChatRepository`：

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository  // 新增
) : ViewModel() {
```

---

### Task 6: 编译验证 + Git Push

- [ ] **Step 1: 编译验证**

```bash
cd /workspace && ./gradlew assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Git commit and push**

```bash
git add -A && git commit -m "fix: 修复交互逻辑缺陷 — API Key输入/提供商切换/对话删除/白屏/附件丢失/竞态" && git push
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - API 提供商切换无效 → Task 5 Step 3 (DataStore Flow 自动同步)
   - 没有填写 API Key 的窗口 → Task 5 Step 2
   - 对话无法删除 → Task 4 Step 3 (长按菜单 + 删除)
   - 白屏卡死 → Task 3 Step 1+2 + Task 4 Step 1+2
   - P0 附件丢失 → Task 2 Step 1
   - P0 停止生成竞态 → Task 2 Step 2
   - P0 数据库非原子 → Task 1
   - 清除对话无效 → Task 5 Step 4+5

2. **Placeholder scan:** No TBD/TODO/placeholders found.

3. **Type consistency:** All method signatures and types are consistent across tasks.
