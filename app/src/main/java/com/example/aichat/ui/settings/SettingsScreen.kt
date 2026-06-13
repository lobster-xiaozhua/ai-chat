package com.example.aichat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichat.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToCustomModel: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val theme by viewModel.theme.collectAsState()
    val language by viewModel.language.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val defaultModel by viewModel.defaultModel.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()

    var tempValue by remember(temperature) { mutableStateOf(temperature.toFloatOrNull() ?: 1.0f) }
    var darkMode by remember(theme) { mutableStateOf(theme == "dark") }

    Scaffold(
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
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                viewModel.setTheme(if (it) "dark" else "light")
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
                    // —— API 提供商选择：切换时自动填充推荐的 baseUrl + 推荐模型
                    DropdownSelector(
                        label = "API 提供商",
                        value = when {
                            defaultModel.startsWith("nvidia/") || defaultModel.startsWith("meta/")
                                || defaultModel.startsWith("mistralai/") -> "NVIDIA API"
                            else -> "DeepSeek"
                        },
                        options = listOf(
                            "DeepSeek" to "deepseek",
                            "NVIDIA API (TensorRT-LLM)" to "nvidia",
                            "本地 NIM 微服务" to "nim"
                        ),
                        onSelect = { provider ->
                            when (provider) {
                                "nvidia" -> {
                                    viewModel.setBaseUrl("https://integrate.api.nvidia.com/v1")
                                    viewModel.setDefaultModel("nvidia/nemotron-nano-12b-v2-vl")
                                }
                                "nim" -> {
                                    viewModel.setBaseUrl("http://localhost:8000/v1")
                                    viewModel.setDefaultModel("meta/llama3-8b-instruct")
                                }
                                else -> {
                                    viewModel.setBaseUrl("https://api.deepseek.com")
                                    viewModel.setDefaultModel("deepseek-chat")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // —— 默认模型：根据当前 provider 提供一份推荐列表
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
                            // 根据当前模型名推断 provider，显示对应推荐列表
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
                value = systemPrompt,
                onValueChange = { viewModel.setSystemPrompt(it) },
                placeholder = { Text("定义 AI 的角色和行为...", fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // 隐私分组
            SectionTitle("隐私")
            Surface(
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
        }
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
