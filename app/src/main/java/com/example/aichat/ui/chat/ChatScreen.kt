package com.example.aichat.ui.chat

import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.aichat.data.model.Conversation
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

    // 已选模型 + 历史消息
    val selectedModelIds = chatViewModel.selectedModelIds.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val error by chatViewModel.error.collectAsState()
    val currentModel by chatViewModel.currentModel.collectAsState()
    val streamingText by chatViewModel.streamingAssistant.collectAsState()
    val conversations by conversationListViewModel.conversations.collectAsState()

    // 待发送图片 / 文档（从选择器获取的 content:// URI 字符串列表）
    // 直接使用 ViewModel 中的 SnapshotStateList/SnapshotStateMap。
    // 这些是 @Stable 容器：增/删不会导致已存在的 item 被重组。
    val pendingImageUrls = chatViewModel.pendingImageUrls
    val pendingDocumentUrls = chatViewModel.pendingDocumentUrls
    val pendingDocumentNames = chatViewModel.pendingDocumentNames

    // —— 图片选择器（Android 官方 Photo Picker）—— 在此处注册，供 Photo 按钮调用
    val imagePicker = rememberImagePicker(onPicked = { newUris ->
        chatViewModel.addImageUrls(newUris)
    })

    // —— 文档选择器（OpenMultipleDocuments，支持 text + pdf + json 等）—— 在此处注册
    val context = androidx.compose.ui.platform.LocalContext.current
    val documentPicker = rememberDocumentPicker { urisWithNames ->
        chatViewModel.addDocumentUrls(urisWithNames)
    }

    var inputText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showPlusSheet by remember { mutableStateOf(false) }

    // 从 ViewModel 读取当前活跃会话 ID；首次进入为空时创建一个新对话
    val activeConversationId by chatViewModel.currentConversationId.collectAsState()

    // 确保首次进入时有一个对话（只执行一次）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (chatViewModel.currentConversationId.value == null) {
            val newId = conversationListViewModel.createNewConversation()
            chatViewModel.setConversation(newId)
        }
    }

    // 快速切换模型
    val quickModels = selectedModelIds.value.ifEmpty { listOf(currentModel) }
        .distinct().let { if (it.isEmpty()) listOf(currentModel) else it }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.clearError()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }
    LaunchedEffect(streamingText) {
        if (streamingText != null) {
            coroutineScope.launch { listState.scrollToItem(messages.size) }
        }
    }

    if (drawerOpen) {
        Box(modifier = Modifier.fillMaxSize()) {
            MainChatContent(
                messages = messages,
                streamingText = streamingText,
                isGenerating = isGenerating,
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
                onMenuClick = { drawerOpen = true },
                onModelClick = { showModelSelector = true },
                onOpenPlusSheet = { showPlusSheet = true }
            )
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxSize().clickable { drawerOpen = false }
            ) {}
            Surface(
                modifier = Modifier.fillMaxWidth(0.78f).fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ConversationDrawer(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    onSelect = { id ->
                        chatViewModel.setConversation(id)
                        drawerOpen = false
                    },
                    onNewChat = {
                        val newId = conversationListViewModel.createNewConversation()
                        chatViewModel.setConversation(newId)
                        drawerOpen = false
                    },
                    onRename = { id, title -> conversationListViewModel.renameConversation(id, title) },
                    onDelete = { id ->
                        conversationListViewModel.deleteConversation(id)
                        if (activeConversationId == id) {
                            val newId = conversationListViewModel.createNewConversation()
                            chatViewModel.setConversation(newId)
                        }
                    },
                    onNavigateToAccount = { drawerOpen = false; onNavigateToAccount() },
                    onNavigateToSettings = { drawerOpen = false; onNavigateToSettings() }
                )
            }
        }
    } else {
        MainChatContent(
            messages = messages,
            streamingText = streamingText,
            isGenerating = isGenerating,
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
            onMenuClick = { drawerOpen = true },
            onModelClick = { showModelSelector = true },
            onOpenPlusSheet = { showPlusSheet = true }
        )
    }

    // —— Plus 二级面板（点击 + 触发）
    if (showPlusSheet) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlusBottomSheet(
                onDismiss = { showPlusSheet = false },
                onPickPhoto = { imagePicker.pickImages(maxItems = 9); showPlusSheet = false },
                onPickFromCamera = { imagePicker.pickFromCamera(); showPlusSheet = false },
                onPickDocument = { documentPicker.pickDocuments(); showPlusSheet = false },
                jsonMode = chatViewModel.jsonMode.collectAsState().value,
                onToggleJsonMode = { chatViewModel.toggleJsonMode() }
            )
        }
    }

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
                                        androidx.compose.material3.Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 12.dp))
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
                                androidx.compose.material3.Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Primary, modifier = Modifier.padding(16.dp))
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
private fun MainChatContent(
    messages: List<Message>,
    streamingText: String?,
    isGenerating: Boolean,
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
    onOpenPlusSheet: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val chatViewModel: ChatViewModel = hiltViewModel()
    val thinkMode = chatViewModel.thinkMode.collectAsState().value
    val searchMode = chatViewModel.searchMode.collectAsState().value
    val jsonMode = chatViewModel.jsonMode.collectAsState().value

    Scaffold(
        // 让 Scaffold 不自动处理 insets — 我们手动精确处理
        // （MagicOS 10+ / Android 16 在内容区域绘制在系统栏下方，必须手动控制）
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
            // navigationBarsPadding：避免被三键/手势导航栏遮挡
            // imePadding：输入法弹出时输入栏自动上移
            ChatInputBar(
                inputText = inputText,
                onInputChange = onInputChange,
                isGenerating = isGenerating,
                currentModel = currentModel,
                thinkMode = thinkMode,
                searchMode = searchMode,
                jsonMode = jsonMode,
                onToggleThink = { chatViewModel.toggleThink() },
                onToggleSearch = { chatViewModel.toggleSearch() },
                onToggleJsonMode = { chatViewModel.toggleJsonMode() },
                pendingImageUrls = pendingImageUrls,
                onRemoveImage = { url -> chatViewModel.removeImageUrl(url) },
                pendingDocumentUrls = pendingDocumentUrls,
                pendingDocumentNames = pendingDocumentNames,
                onRemoveDocument = { url -> chatViewModel.removeDocumentUrl(url) },
                onSend = onSend,
                onStop = onStop,
                onModelClick = onModelClick,
                onOpenPlusSheet = onOpenPlusSheet,
                modifier = Modifier.navigationBarsPadding().imePadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // innerPadding 只包含 topBar / bottomBar 的高度 ——
        // 我们在 topBar / bottomBar 里已经加了系统栏 padding，
        // 所以这里不需要再加；但需要在内容区域底部留一点导航栏的 space
        // （输入法弹出时被 imePadding 覆盖了，输入法收起时手势导航栏会显示）
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (messages.isEmpty() && streamingText == null && pendingImageUrls.isEmpty() && pendingDocumentUrls.isEmpty()) {
                EmptyState(onExampleClick = { example -> onInputChange(example); onSend() })
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items = messages, key = { msg -> msg.id.takeIf { it != 0L } ?: (msg.timestamp.toString() + msg.role + msg.content.hashCode()) }) { msg ->
                        MessageBubble(
                            content = msg.content,
                            isUser = msg.role == "user",
                            imageUrls = msg.imageUrlList()
                        )
                    }
                    if (streamingText != null) {
                        item(contentType = "streaming") {
                            MessageBubble(content = streamingText!!, isUser = false, isStreaming = true)
                        }
                    }
                }
            }
        }
    }
}

/* ==========================================================================
 * ChatInputBar —— 统一卡片式输入栏
 *
 * ┌────────────────────────────────────────────────────────────┐
 * │  [Think✓] [Search 🌐]           [Nemotron Nano ▼] [+] [🎤] │
 * │  ┌──────────────────────────────────────────────────────┐  │
 * │  │  Type a message or hold to speak            [→]      │  │
 * │  └──────────────────────────────────────────────────────┘  │
 * │  [ image thumbnail row if any ]                            │
 * └────────────────────────────────────────────────────────────┘
 *
 *  · 左侧：Think / Search（模式开关，默认常驻可见，场景化）
 *  · 右侧：模型选择 / +（展开二级面板）/ 语音
 *  · 下层：文本输入 + 发送按钮
 *  · 最下层：已选图片缩略图（发送前可移除）
 * ========================================================================== */
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
    onOpenPlusSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {

                // —— 顶部：文本输入 + 发送按钮（视觉焦点，第一排）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
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
                                    else append("Type a message or hold to speak")
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

                // —— 底部一行：左 (Think / Search) + 右 (模型 / +)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModeChip(label = "Think", isOn = thinkMode, onClick = onToggleThink, icon = "✓")
                    ModeChip(label = "Search", isOn = searchMode, onClick = onToggleSearch, icon = "🌐")

                    Spacer(modifier = Modifier.weight(1f))

                    ModeChip(label = friendlyModelName(currentModel), isOn = false, onClick = onModelClick, icon = "", isModelChip = true)
                    Surface(
                        onClick = onOpenPlusSheet,
                        shape = CircleShape,
                        color = if (jsonMode || pendingImageUrls.isNotEmpty() || pendingDocumentUrls.isNotEmpty()) Primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("+", color = if (jsonMode || pendingImageUrls.isNotEmpty() || pendingDocumentUrls.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // —— 已选图片缩略图预览（位于卡片下方，独立一行）
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
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Surface(onClick = { onRemoveImage(url) }, shape = CircleShape, color = Color(0xFF666666), modifier = Modifier.size(18.dp).align(Alignment.TopEnd)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // —— 已选文档 Chip 预览（位于图片下方，独立一行）
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
                            Text(
                                text = name.take(20),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(onClick = { onRemoveDocument(url) }, shape = CircleShape, color = Color(0xFF666666), modifier = Modifier.size(16.dp).padding(start = 4.dp)) {
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

/**
 * 模式开关 Chip —— Think / Search 等通用按钮样式
 */
@Composable
private fun ModeChip(
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

/* ==========================================================================
 * PlusBottomSheet —— 点击 + 弹出的底部半屏面板
 *
 *  放置低频但重要的扩展操作：
 *   · 📷 Camera     —— 拍照（系统相机 Intent）
 *   · 🖼️ Photo      —— 从相册选图（PhotoPicker）
 *   · 📄 Document   —— 文件选择
 *   · { } JSON      —— 结构化输出模式开关
 *
 *  Note: Think / Search 属于发送时的"模式"，放在输入栏顶层，
 *        不因扩展操作而丢失当前模式上下文。
 * ========================================================================== */
@Composable
fun PlusBottomSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFromCamera: () -> Unit,
    onPickDocument: () -> Unit,
    jsonMode: Boolean,
    onToggleJsonMode: () -> Unit
) {
    Surface(color = Color.Black.copy(alpha = 0.45f), modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)) {}
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f), modifier = Modifier.size(38.dp, 4.dp)) {}
                }
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SheetAction(icon = "📷", label = "Camera", onClick = onPickFromCamera)
                    SheetAction(icon = "🖼️", label = "Photo", onClick = onPickPhoto)
                    SheetAction(icon = "📄", label = "Document", onClick = onPickDocument)
                }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("{ } JSON 结构化输出", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    androidx.compose.material3.Switch(checked = jsonMode, onCheckedChange = { onToggleJsonMode() })
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(icon, fontSize = 24.sp)
            }
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun EmptyState(onExampleClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✨ AI 助手", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))
        Text("输入消息开始对话，或试试这些示例：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        val examples = listOf("帮我写一个 Kotlin 协程的例子", "用简单的方式解释 Transformer 架构", "写一首关于夏日的短诗")
        examples.forEach { example ->
            Surface(onClick = { onExampleClick(example) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(example, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
            }
        }
    }
}

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
                Surface(onClick = { onSelect(conv.id) }, color = bgColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(conv.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(formatTime(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
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
