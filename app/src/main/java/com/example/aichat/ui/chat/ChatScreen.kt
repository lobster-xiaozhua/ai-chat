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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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

    // 待发送图片（从图片选择器获取的 content:// URI 字符串列表）
    val pendingImageUrls by chatViewModel.pendingImageUrls.collectAsState()

    // —— 图片选择器（Android 官方 Photo Picker）
    val imagePicker = rememberImagePicker(onPicked = { newUris ->
        chatViewModel.addImageUrls(newUris)
    })

    var inputText by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var activeConversationId by remember { mutableStateOf<String?>(null) }

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

    if (activeConversationId == null) {
        activeConversationId = conversationListViewModel.createNewConversation()
        chatViewModel.setConversation(activeConversationId!!)
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
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank() || pendingImageUrls.isNotEmpty()) {
                        chatViewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                onStop = { chatViewModel.stopGeneration() },
                onMenuClick = { drawerOpen = true },
                onModelClick = { showModelSelector = true },
                onAddImage = { imagePicker.pickImages(maxItems = 9) },
                onRemoveImage = { chatViewModel.removeImageUrl(it) }
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
                        activeConversationId = id
                        chatViewModel.setConversation(id)
                        drawerOpen = false
                    },
                    onNewChat = {
                        activeConversationId = conversationListViewModel.createNewConversation()
                        chatViewModel.setConversation(activeConversationId!!)
                        drawerOpen = false
                    },
                    onRename = { id, title -> conversationListViewModel.renameConversation(id, title) },
                    onDelete = { id ->
                        conversationListViewModel.deleteConversation(id)
                        if (activeConversationId == id) {
                            activeConversationId = conversationListViewModel.createNewConversation()
                            chatViewModel.setConversation(activeConversationId!!)
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
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank() || pendingImageUrls.isNotEmpty()) {
                    chatViewModel.sendMessage(inputText.trim())
                    inputText = ""
                }
            },
            onStop = { chatViewModel.stopGeneration() },
            onMenuClick = { drawerOpen = true },
            onModelClick = { showModelSelector = true },
            onAddImage = { imagePicker.pickImages(maxItems = 9) },
            onRemoveImage = { chatViewModel.removeImageUrl(it) }
        )
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
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    onAddImage: () -> Unit,
    onRemoveImage: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val chatViewModel: ChatViewModel = hiltViewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新对话", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, contentDescription = "菜单") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = onInputChange,
                isGenerating = isGenerating,
                currentModel = currentModel,
                jsonMode = chatViewModel.jsonMode.collectAsState().value,
                onToggleJsonMode = { chatViewModel.toggleJsonMode() },
                pendingImageUrls = pendingImageUrls,
                onRemoveImage = onRemoveImage,
                onSend = onSend,
                onStop = onStop,
                onModelClick = onModelClick,
                onAddImage = onAddImage
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (messages.isEmpty() && streamingText == null && pendingImageUrls.isEmpty()) {
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

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    currentModel: String,
    jsonMode: Boolean,
    onToggleJsonMode: () -> Unit,
    pendingImageUrls: List<String>,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onAddImage: () -> Unit
) {
    Column {
        Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // —— 模型切换按钮
            Surface(onClick = onModelClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(end = 4.dp)) {
                Text(friendlyModelName(currentModel), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
            }
            // —— JSON 模式切换
            Surface(onClick = onToggleJsonMode, shape = RoundedCornerShape(18.dp), color = if (jsonMode) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                Text(text = if (jsonMode) "{ } JSON" else "文本", color = if (jsonMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            // —— 添加图片按钮
            Surface(onClick = onAddImage, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("图", fontSize = 13.sp)
                }
            }
        }

        // —— 已选图片缩略图预览
        if (pendingImageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
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
                        // 右上角关闭按钮
                        Surface(onClick = { onRemoveImage(url) }, shape = CircleShape, color = Color(0xFF666666), modifier = Modifier.size(18.dp).align(Alignment.TopEnd)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("×", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text(text = if (jsonMode) "请输入，回复将为 JSON..." else if (pendingImageUrls.isNotEmpty()) "描述这些图片..." else "输入消息...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f).padding(end = 12.dp).height(56.dp),
                shape = RoundedCornerShape(22.dp),
                maxLines = 4
            )
            Surface(
                onClick = if (isGenerating) onStop else onSend,
                shape = CircleShape,
                color = if (isGenerating) Color(0xFFD32F2F) else Primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isGenerating) {
                        Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("→", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
