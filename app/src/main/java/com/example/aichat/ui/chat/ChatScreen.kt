package com.example.aichat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichat.data.model.Conversation
import com.example.aichat.ui.theme.Primary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {}
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val conversationListViewModel: ConversationListViewModel = hiltViewModel()

    val messages by chatViewModel.messages.collectAsState()
    val isGenerating by chatViewModel.isGenerating.collectAsState()
    val error by chatViewModel.error.collectAsState()
    val currentModel by chatViewModel.currentModel.collectAsState()
    val conversations by conversationListViewModel.conversations.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var activeConversationId by remember {
        mutableStateOf<String?>(null)
    }

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
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (activeConversationId == null) {
        activeConversationId = conversationListViewModel.createNewConversation()
        chatViewModel.setConversation(activeConversationId!!)
    }

    if (drawerOpen) {
        // 侧边栏遮罩 + 抽屉
        Box(modifier = Modifier.fillMaxSize()) {
            // 主内容
            MainChatContent(
                messages = messages,
                isGenerating = isGenerating,
                currentModel = currentModel,
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        chatViewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                onStop = { chatViewModel.stopGeneration() },
                onMenuClick = { drawerOpen = true },
                onModelClick = { showModelSelector = true }
            )

            // 遮罩层
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { drawerOpen = false }
            ) {}

            // 侧边栏
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .fillMaxSize(),
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
                    onRename = { id, title ->
                        conversationListViewModel.renameConversation(id, title)
                    },
                    onDelete = { id ->
                        conversationListViewModel.deleteConversation(id)
                        if (activeConversationId == id) {
                            activeConversationId = conversationListViewModel.createNewConversation()
                            chatViewModel.setConversation(activeConversationId!!)
                        }
                    },
                    onNavigateToAccount = {
                        drawerOpen = false
                        onNavigateToAccount()
                    },
                    onNavigateToSettings = {
                        drawerOpen = false
                        onNavigateToSettings()
                    }
                )
            }
        }
    } else {
        MainChatContent(
            messages = messages,
            isGenerating = isGenerating,
            currentModel = currentModel,
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    chatViewModel.sendMessage(inputText.trim())
                    inputText = ""
                }
            },
            onStop = { chatViewModel.stopGeneration() },
            onMenuClick = { drawerOpen = true },
            onModelClick = { showModelSelector = true }
        )
    }

    // 模型选择弹窗
    if (showModelSelector) {
        Surface(
            color = Color.Black.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxSize()
                .clickable { showModelSelector = false }
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "选择模型",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        listOf(
                            "deepseek-chat" to "DeepSeek Chat",
                            "deepseek-coder" to "DeepSeek Coder",
                            "gpt-4o-mini" to "GPT-4o Mini"
                        ).forEach { (id, name) ->
                            val selected = currentModel == id
                            Surface(
                                onClick = {
                                    chatViewModel.setModel(id)
                                    showModelSelector = false
                                },
                                color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    name,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
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
    messages: List<com.example.aichat.data.model.Message>,
    isGenerating: Boolean,
    currentModel: String,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新对话", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = onInputChange,
                isGenerating = isGenerating,
                currentModel = currentModel,
                onSend = onSend,
                onStop = onStop,
                onModelClick = onModelClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                EmptyState(onExampleClick = { example ->
                    onInputChange(example)
                    onSend()
                })
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        MessageBubble(
                            content = msg.content,
                            isUser = isUser
                        )
                        if (isGenerating && msg == messages.last() && !isUser) {
                            BlinkingCursor()
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
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit
) {
    Column {
        Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onModelClick,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    currentModel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 13.sp
                )
            }
        }
        // 输入区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("输入消息...", fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(22.dp),
                maxLines = 4
            )
            Surface(
                onClick = if (isGenerating) onStop else onSend,
                shape = CircleShape,
                color = if (isGenerating) Color(0xFFD32F2F) else Primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isGenerating) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "停止",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "✨ AI 助手",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Text(
            "输入消息开始对话，或试试这些示例：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        val examples = listOf(
            "帮我写一个 Kotlin 协程的例子",
            "用简单的方式解释 Transformer 架构",
            "写一首关于夏日的短诗"
        )
        examples.forEach { example ->
            Surface(
                onClick = { onExampleClick(example) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    example,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun BlinkingCursor() {
    // 简单的光标效果，通过文字添加
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("历史对话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = "新建对话", tint = Primary)
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(conversations) { conv ->
                val isActive = conv.id == activeConversationId
                val bgColor = if (isActive) Primary.copy(alpha = 0.1f) else Color.Transparent
                Surface(
                    onClick = { onSelect(conv.id) },
                    color = bgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            conv.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            formatTime(conv.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAccount() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("U", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("账号中心", modifier = Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSettings() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
