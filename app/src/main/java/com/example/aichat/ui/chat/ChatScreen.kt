package com.example.aichat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichat.data.model.Message
import com.example.aichat.ui.theme.Primary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToModelPicker: () -> Unit = {}
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val conversationListViewModel: ConversationListViewModel = hiltViewModel()
    val context = LocalContext.current

    val selectedModelIds = chatViewModel.selectedModelIds.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val error by chatViewModel.error.collectAsState()
    val currentModel by chatViewModel.currentModel.collectAsState()
    val streamingText by chatViewModel.streamingAssistant.collectAsState()
    val conversations by conversationListViewModel.conversations.collectAsState()
    val lastGenerationFailed by chatViewModel.lastGenerationFailed.collectAsState()

    val pendingImageUrls = chatViewModel.pendingImageUrls
    val pendingDocumentUrls = chatViewModel.pendingDocumentUrls
    val pendingDocumentNames = chatViewModel.pendingDocumentNames

    val imagePicker = rememberImagePicker(onPicked = { newUris ->
        chatViewModel.addImageUrls(newUris)
    })
    val documentPicker = rememberDocumentPicker { urisWithNames ->
        chatViewModel.addDocumentUrls(urisWithNames)
    }

    var inputText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    var showPlusSheet by remember { mutableStateOf(false) }

    // 消息长按菜单状态
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showMsgMenu by remember { mutableStateOf(false) }

    val activeConversationId by chatViewModel.currentConversationId.collectAsState()

    LaunchedEffect(activeConversationId) {
        if (activeConversationId == null) {
            val newId = conversationListViewModel.createNewConversation()
            chatViewModel.setConversation(newId)
        } else {
            // 切换会话时恢复草稿
            val id = activeConversationId ?: return@LaunchedEffect
            val draft = chatViewModel.restoreDraft(id)
            if (draft.isNotBlank()) inputText = draft
        }
    }

    val quickModels = selectedModelIds.value.ifEmpty { listOf(currentModel) }
        .distinct().let { if (it.isEmpty()) listOf(currentModel) else it }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.clearError()
        }
    }

    LaunchedEffect(streamingText) {
        if (streamingText != null) {
            coroutineScope.launch { listState.scrollToItem(messages.size) }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                activeConversationId = activeConversationId,
                onSelect = { id ->
                    // 保存当前草稿
                    activeConversationId?.let { chatViewModel.saveDraft(it, inputText) }
                    chatViewModel.setConversation(id)
                    // 恢复目标会话草稿
                    val draft = chatViewModel.restoreDraft(id)
                    inputText = draft
                    coroutineScope.launch { drawerState.close() }
                },
                onNewChat = {
                    activeConversationId?.let { chatViewModel.saveDraft(it, inputText) }
                    coroutineScope.launch {
                        val newId = conversationListViewModel.createNewConversation()
                        chatViewModel.setConversation(newId)
                        inputText = chatViewModel.restoreDraft(newId)
                    }
                    coroutineScope.launch { drawerState.close() }
                },
                onRename = { id, title -> conversationListViewModel.renameConversation(id, title) },
                onDelete = { id ->
                    conversationListViewModel.deleteConversation(id)
                    if (activeConversationId == id) {
                        coroutineScope.launch {
                            val newId = conversationListViewModel.createNewConversation()
                            chatViewModel.setConversation(newId)
                        }
                        inputText = ""
                    }
                },
                onTogglePin = { id, pinned -> conversationListViewModel.togglePin(id, pinned) },
                onExport = { id ->
                    coroutineScope.launch {
                        val markdown = conversationListViewModel.exportConversationAsMarkdown(id)
                        if (markdown.isNotBlank()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, markdown)
                                type = "text/markdown"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "导出对话"))
                        } else {
                            snackbarHostState.showSnackbar("导出失败：会话为空")
                        }
                    }
                },
                onNavigateToAccount = { coroutineScope.launch { drawerState.close() }; onNavigateToAccount() },
                onNavigateToSettings = { coroutineScope.launch { drawerState.close() }; onNavigateToSettings() },
                onSearch = { query -> conversationListViewModel.searchConversations(query) }
            )
        }
    ) {
        MainChatContent(
            messages = messages,
            streamingText = streamingText,
            isGenerating = isGenerating,
            lastGenerationFailed = lastGenerationFailed,
            currentModel = currentModel,
            listState = listState,
            inputText = inputText,
            pendingImageUrls = pendingImageUrls,
            pendingDocumentUrls = pendingDocumentUrls,
            pendingDocumentNames = pendingDocumentNames,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() || pendingImageUrls.isNotEmpty() || pendingDocumentUrls.isNotEmpty()) {
                    chatViewModel.sendMessage(inputText.trim())
                    inputText = ""
                }
            },
            onStop = { chatViewModel.stopGeneration() },
            onMenuClick = { coroutineScope.launch { drawerState.open() } },
            onModelClick = {
                coroutineScope.launch { drawerState.close() }
                showModelSelector = true
            },
            thinkMode = chatViewModel.thinkMode.collectAsState().value,
            searchMode = chatViewModel.searchMode.collectAsState().value,
            jsonMode = chatViewModel.jsonMode.collectAsState().value,
            onToggleThink = { chatViewModel.toggleThink() },
            onToggleSearch = { chatViewModel.toggleSearch() },
            onToggleJsonMode = { chatViewModel.toggleJsonMode() },
            onRemoveImage = { url -> chatViewModel.removeImageUrl(url) },
            onRemoveDocument = { url -> chatViewModel.removeDocumentUrl(url) },
            onMessageLongClick = { msg ->
                selectedMessage = msg
                showMsgMenu = true
            },
            onRegenerate = { chatViewModel.regenerateLastAssistant() },
            onSendExample = { text -> inputText = text; chatViewModel.sendMessage(text.trim()) },
            snackbarHostState = snackbarHostState,
            plusExpanded = showPlusSheet,
            onTogglePlus = { showPlusSheet = !showPlusSheet },
            onPickPhoto = { imagePicker.pickImages(maxItems = 9); showPlusSheet = false },
            onPickFromCamera = { imagePicker.pickFromCamera(); showPlusSheet = false },
            onPickDocument = { documentPicker.pickDocuments(); showPlusSheet = false }
        )
    }

    // 消息长按菜单
    val msg = selectedMessage
    if (showMsgMenu && msg != null) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { showMsgMenu = false; selectedMessage = null }
        ) {
            DropdownMenuItem(
                text = { Text("复制") },
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("message", msg.content))
                    showMsgMenu = false
                    selectedMessage = null
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("删除", color = Color(0xFFD32F2F)) },
                onClick = {
                    chatViewModel.deleteMessage(msg.id)
                    showMsgMenu = false
                    selectedMessage = null
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp)) }
            )
            if (msg.role == "assistant") {
                DropdownMenuItem(
                    text = { Text("重新生成") },
                    onClick = {
                        chatViewModel.regenerateLastAssistant()
                        showMsgMenu = false
                        selectedMessage = null
                    },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }

    // 模型选择器
    if (showModelSelector) {
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxSize().clickable { showModelSelector = false }
        ) {
            Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
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
                                color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant,
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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Primary, modifier = Modifier.padding(16.dp))
                                Text("选择更多模型 (搜索 / 多选)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainChatContent(
    messages: List<Message>,
    streamingText: String?,
    isGenerating: Boolean,
    lastGenerationFailed: Boolean,
    currentModel: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    pendingImageUrls: List<String>,
    pendingDocumentUrls: List<String>,
    pendingDocumentNames: Map<String, String>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    thinkMode: Boolean,
    searchMode: Boolean,
    jsonMode: Boolean,
    onToggleThink: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleJsonMode: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onRemoveDocument: (String) -> Unit,
    onMessageLongClick: (Message) -> Unit,
    onRegenerate: () -> Unit,
    onSendExample: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    plusExpanded: Boolean,
    onTogglePlus: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFromCamera: () -> Unit,
    onPickDocument: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("新对话", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, contentDescription = "菜单") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = onInputChange,
                isGenerating = isGenerating,
                currentModel = currentModel,
                thinkMode = thinkMode,
                searchMode = searchMode,
                jsonMode = jsonMode,
                onToggleThink = onToggleThink,
                onToggleSearch = onToggleSearch,
                onToggleJsonMode = onToggleJsonMode,
                pendingImageUrls = pendingImageUrls,
                onRemoveImage = onRemoveImage,
                pendingDocumentUrls = pendingDocumentUrls,
                pendingDocumentNames = pendingDocumentNames,
                onRemoveDocument = onRemoveDocument,
                onSend = onSend,
                onStop = onStop,
                onModelClick = onModelClick,
                plusExpanded = plusExpanded,
                onTogglePlus = onTogglePlus,
                onPickPhoto = onPickPhoto,
                onPickFromCamera = onPickFromCamera,
                onPickDocument = onPickDocument,
                modifier = Modifier.navigationBarsPadding().imePadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (messages.isEmpty() && streamingText == null && pendingImageUrls.isEmpty() && pendingDocumentUrls.isEmpty()) {
                EmptyState(onSend = { text -> onSendExample(text) })
            } else {
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.scrollToItem(messages.size - 1)
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items = messages, key = { msg -> msg.id.takeIf { it != 0L } ?: msg.timestamp.toString() + msg.role }) { msg ->
                        MessageBubble(
                            content = msg.content,
                            isUser = msg.role == "user",
                            imageUrls = msg.imageUrlList(),
                            onLongClick = { onMessageLongClick(msg) }
                        )
                    }
                    if (streamingText != null) {
                        item(contentType = "streaming") {
                            MessageBubble(content = streamingText!!, isUser = false, isStreaming = true)
                        }
                    }
                    // 生成失败时显示重新生成按钮
                    if (lastGenerationFailed && !isGenerating && messages.isNotEmpty()) {
                        item(contentType = "regenerate") {
                            Surface(
                                onClick = onRegenerate,
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("重新生成", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun friendlyModelName(model: String): String = when {
    model == "nvidia/nemotron-nano-12b-v2-vl" -> "Nemotron Nano 12B"
    model == "nvidia/nemotron-4-340b-instruct" -> "Nemotron 4 340B"
    model.startsWith("nvidia/") -> "Nemotron"
    model.startsWith("meta/llama-3.1") -> "Llama 3.1"
    model.startsWith("meta/llama-3.2") -> "Llama 3.2 Vision"
    model.startsWith("meta/") -> "Llama"
    model.startsWith("mistralai/") -> "Mistral"
    model.startsWith("qwen/") -> "Qwen"
    model == "deepseek-chat" -> "DeepSeek Chat"
    model == "deepseek-coder" -> "DeepSeek Coder"
    else -> model
}
