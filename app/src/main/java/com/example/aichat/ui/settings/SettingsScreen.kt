package com.example.aichat.ui.settings

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichat.ui.theme.Primary
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToCustomModel: () -> Unit = {},
    onNavigateToModelPicker: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val theme by viewModel.theme.collectAsState()
    val language by viewModel.language.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val defaultModel by viewModel.defaultModel.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val currentApiKey by viewModel.apiKey.collectAsState()
    val context = LocalContext.current

    var tempValue by remember(temperature) { mutableStateOf(temperature.toFloatOrNull() ?: 1.0f) }
    var darkMode by remember(theme) { mutableStateOf(theme == "dark") }
    var systemMode by remember(theme) { mutableStateOf(theme != "dark" && theme != "light") }
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf("") }

    // 数据统计
    var convCount by remember { mutableStateOf(0) }
    var msgCount by remember { mutableStateOf(0) }
    var storageSize by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        convCount = viewModel.getConversationCount()
        msgCount = viewModel.getMessageCount()
        storageSize = calculateStorageSize(context)
    }

    // API Key 防抖写入
    LaunchedEffect(apiKey) {
        delay(500)
        viewModel.setApiKey(apiKey)
    }

    var pendingSystemPrompt by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    LaunchedEffect(pendingSystemPrompt) {
        delay(500)
        viewModel.setSystemPrompt(pendingSystemPrompt)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 通用设置分组
            SectionTitle("通用")

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("深色模式", modifier = Modifier.weight(1f))
                        Switch(
                            checked = darkMode,
                            onCheckedChange = {
                                darkMode = it
                                systemMode = false
                                viewModel.setTheme(if (it) "dark" else "light")
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("跟随系统", modifier = Modifier.weight(1f))
                        Switch(
                            checked = systemMode,
                            onCheckedChange = {
                                systemMode = it
                                if (it) {
                                    darkMode = false
                                    viewModel.setTheme("system")
                                } else {
                                    viewModel.setTheme("light")
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DropdownSelector(
                        label = "语言",
                        value = if (language == "zh") "中文" else "English",
                        options = listOf("中文" to "zh", "English" to "en"),
                        onSelect = { viewModel.setLanguage(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DropdownSelector(
                        label = "字号",
                        value = when (fontSize) {
                            "small" -> "小"
                            "large" -> "大"
                            else -> "中"
                        },
                        options = listOf("小" to "small", "中" to "medium", "大" to "large"),
                        onSelect = { viewModel.setFontSize(it) }
                    )
                }
            }

            // 模型配置分组
            SectionTitle("模型")

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DropdownSelector(
                        label = "API 提供商",
                        value = when {
                            defaultModel.startsWith("nvidia/") || defaultModel.startsWith("meta/")
                                || defaultModel.startsWith("mistralai/") -> "NVIDIA API"
                            else -> "DeepSeek"
                        },
                        options = listOf(
                            "DeepSeek" to "deepseek",
                            "NVIDIA API (TensorRT-LLM)" to "nvidia"
                        ),
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
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DropdownSelector(
                        label = "默认模型",
                        value = defaultModel,
                        options = run {
                            val nvidiaModels = listOf(
                                "Nemotron Nano 12B (VL)" to "nvidia/nemotron-nano-12b-v2-vl",
                                "Nemotron 4 340B Instruct" to "nvidia/nemotron-4-340b-instruct",
                                "Llama 3.1 8B Instruct" to "meta/llama-3.1-8b-instruct",
                                "Llama 3.2 11B Vision" to "meta/llama-3.2-11b-vision-instruct",
                                "Mistral 7B Instruct v0.3" to "mistralai/mistral-7b-instruct-v0.3",
                                "Qwen 2.5 72B Instruct" to "qwen/qwen2.5-72b-instruct"
                            )
                            val deepseekModels = listOf(
                                "DeepSeek Chat" to "deepseek-chat",
                                "DeepSeek Coder" to "deepseek-coder"
                            )
                            if (defaultModel.startsWith("nvidia/") || defaultModel.startsWith("meta/")
                                || defaultModel.startsWith("mistralai/") || defaultModel.startsWith("qwen/")) {
                                nvidiaModels
                            } else {
                                deepseekModels
                            }
                        },
                        onSelect = { viewModel.setDefaultModel(it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { newKey ->
                            apiKey = newKey
                        },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Temperature: ${String.format("%.1f", tempValue)}", fontSize = 14.sp)
                    Slider(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        valueRange = 0f..2f,
                        steps = 19,
                        onValueChangeFinished = {
                            viewModel.setTemperature(String.format("%.1f", tempValue))
                        },
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = onNavigateToModelPicker,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("从提供商获取所有可用模型 (搜索 / 多选)",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("›", color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = onNavigateToCustomModel,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自定义大模型配置 (API 地址 / 模型名 / Key)", modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.Gray)
                            Text("›", color = Color.Gray)
                        }
                    }
                }
            }

            // 系统提示词分组
            SectionTitle("系统提示词")
            OutlinedTextField(
                value = pendingSystemPrompt,
                onValueChange = { pendingSystemPrompt = it },
                placeholder = { Text("定义 AI 的角色和行为...", fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // 数据统计分组
            SectionTitle("数据统计")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("会话总数", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text("$convCount", fontSize = 14.sp, color = Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("消息总数", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text("$msgCount", fontSize = 14.sp, color = Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("存储占用", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Text(storageSize, fontSize = 14.sp, color = Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    }
                }
            }

            // 隐私分组
            SectionTitle("隐私")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Surface(
                        onClick = {
                            cacheSizeText = calculateStorageSize(context)
                            showCacheDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("清理缓存", modifier = Modifier.weight(1f))
                            Text(cacheSizeText.ifBlank { calculateStorageSize(context) }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Text("清除所有对话（不可恢复）", color = Color(0xFFD32F2F))
                        }
                    }
                }
            }

            // 关于
            SectionTitle("其他")
            Surface(
                onClick = onNavigateToAbout,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("关于", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // 清除确认对话框
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
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    )
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    // 清理缓存对话框
    if (showCacheDialog) {
        AlertDialog(
            onDismissRequest = { showCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("当前缓存大小：$cacheSizeText\n\n确认清理缓存？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearCache(context)
                        cacheSizeText = calculateStorageSize(context)
                        storageSize = cacheSizeText
                        showCacheDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    )
                ) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCacheDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(value, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Text("▼", fontSize = 12.sp, color = Color.Gray)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (display, id) ->
                DropdownMenuItem(
                    text = { Text(display, fontSize = 14.sp) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun calculateStorageSize(context: Context): String {
    var totalSize = 0L
    // cacheDir
    totalSize += getDirSize(context.cacheDir)
    // databases
    val dbDir = context.getDatabasePath("aichat_db").parentFile
    if (dbDir != null && dbDir.exists()) totalSize += getDirSize(dbDir)
    return formatSize(totalSize)
}

private fun getDirSize(dir: File): Long {
    if (!dir.exists()) return 0L
    var size = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
        size += if (file.isDirectory) getDirSize(file) else file.length()
    }
    return size
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size.toDouble() / (1024 * 1024))} MB"
        else -> "${"%.1f".format(size.toDouble() / (1024 * 1024 * 1024))} GB"
    }
}

private fun clearCache(context: Context) {
    try {
        // 先清理子目录再删除 cacheDir 自身
        val cameraDir = File(context.cacheDir, "camera")
        if (cameraDir.exists()) cameraDir.deleteRecursively()
        val updatesDir = File(context.cacheDir, "updates")
        if (updatesDir.exists()) updatesDir.deleteRecursively()
        // 删除 cacheDir 下所有文件（不删除 cacheDir 本身，避免系统重建延迟）
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        Log.d("SettingsScreen", "Cache cleared successfully")
    } catch (e: Exception) {
        Log.w("SettingsScreen", "Failed to clear cache: ${e.message}")
    }
}
