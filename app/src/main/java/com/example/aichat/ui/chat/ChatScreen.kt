package com.example.aichat.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.model.Message
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

    val pendingImageUrls by chatViewModel.pendingImageUrls.collectAsState()
    val pendingDocumentUrls by chatViewModel.pendingDocumentUrls.collectAsState()
    val pendingDocumentNames by chatViewModel.pendingDocumentNames.collectAsState()

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
    val currentConvTitle = conversations.firstOrNull { it.id == activeConversationId }?.title ?: "新对话"

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
            conversationTitle = currentConvTitle,
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
            onSendExample = { text ->
                inputText = ""
                chatViewModel.sendMessage(text.trim())
            },
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
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    chatViewModel.deleteMessage(msg.id)
                    showMsgMenu = false
                    selectedMessage = null
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            )
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
        }
    }

    // Plus 折叠面板（输入栏上方展开，非全屏遮罩）

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainChatContent(
    messages: List<Message>,
    streamingText: String?,
    isGenerating: Boolean,
    lastGenerationFailed: Boolean,
    currentModel: String,
    conversationTitle: String,
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
                title = { Text(conversationTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
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

@Composable
private fun ChatInputBar(
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
                        color = if (plusExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "更多选项" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (plusExpanded) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Add, contentDescription = "更多选项", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f).padding(end = 8.dp).heightIn(min = 24.dp, max = 120.dp),
                        maxLines = 6,
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
                        color = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isGenerating) {
                                Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.ArrowForward, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeChip(label = "Think", isOn = thinkMode, onClick = onToggleThink, icon = Icons.Default.Psychology)
                    ModeChip(label = "Search", isOn = searchMode, onClick = onToggleSearch, icon = Icons.Default.Language)

                    Spacer(modifier = Modifier.weight(1f))

                    ModeChip(label = friendlyModelName(currentModel), isOn = false, onClick = onModelClick, isModelChip = true)
                }
            }
        }

        if (pendingImageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingImageUrls.forEach { url ->
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
                    modifier = Modifier.size(14.dp),
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
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
private fun InlinePlusPanel(
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
                SheetAction(icon = Icons.Default.CameraAlt, label = "相机", onClick = onPickFromCamera)
                SheetAction(icon = Icons.Default.PhotoLibrary, label = "相册", onClick = onPickPhoto)
                SheetAction(icon = Icons.Default.Description, label = "文档", onClick = onPickDocument)
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

@Composable
private fun EmptyState(onSend: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("AI 助手", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))
        Text("输入消息开始对话，或试试这些示例：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        val examples = listOf("帮我写一个 Kotlin 协程的例子", "用简单的方式解释 Transformer 架构", "写一首关于夏日的短诗")
        examples.forEach { example ->
            Surface(onClick = { onSend(example) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(example, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationDrawer(
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
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, contentDescription = "新建对话", tint = MaterialTheme.colorScheme.primary) }
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
                    val bgColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuConvId = null; showDeleteConfirmId = conv.id },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
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
                            contentColor = MaterialTheme.colorScheme.error
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
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) {
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

private fun friendlyModelName(model: String): String = when {
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
