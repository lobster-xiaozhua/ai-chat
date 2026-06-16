package com.example.aichat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.example.aichat.ui.icons.ExtendedIcons
import com.example.aichat.ui.icons.Extended

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aichat.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModelScreen(onBack: () -> Unit = {}) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val currentBaseUrl by viewModel.baseUrl.collectAsState()
    val currentModel by viewModel.defaultModel.collectAsState()
    val currentKey by viewModel.apiKey.collectAsState()

    var baseUrl by remember { mutableStateOf(currentBaseUrl) }
    var modelName by remember { mutableStateOf(currentModel) }
    var apiKey by remember { mutableStateOf(currentKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun validate(): Boolean {
        val url = baseUrl.trim().lowercase(Locale.ROOT)
        errorMsg = when {
            url.isEmpty() -> "请填写 API 地址"
            !(url.startsWith("http://") || url.startsWith("https://")) ->
                "API 地址必须以 http:// 或 https:// 开头"
            modelName.isBlank() -> "请填写模型名称"
            apiKey.isBlank() -> "请填写 API Key"
            else -> null
        }
        return errorMsg == null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("自定义模型配置", style = MaterialTheme.typography.titleMedium) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("https://api.deepseek.com", color = Color.Gray) },
                isError = errorMsg != null
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("deepseek-chat", color = Color.Gray) }
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Extended.Visibility else Icons.Extended.VisibilityOff,
                            contentDescription = if (showApiKey) "隐藏" else "显示",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            errorMsg?.let {
                Text(
                    text = it,
                    color = Color(0xFFD32F2F),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // —— 快速配置：一键填入推荐的 baseUrl + modelName
            Text(
                "快速配置",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetButton(
                    text = "DeepSeek",
                    onClick = {
                        baseUrl = "https://api.deepseek.com"
                        modelName = "deepseek-chat"
                    }
                )
                PresetButton(
                    text = "NVIDIA 云端",
                    onClick = {
                        baseUrl = "https://integrate.api.nvidia.com/v1"
                        modelName = "nvidia/nemotron-nano-12b-v2-vl"
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = {
                    if (!validate()) return@Surface
                    viewModel.setBaseUrl(baseUrl.trim())
                    viewModel.setDefaultModel(modelName.trim())
                    viewModel.setApiKey(apiKey.trim())
                    // 延迟一帧返回，确保 DataStore 写入完成
                    coroutineScope.launch {
                        delay(100)
                        onBack()
                    }
                },
                shape = RoundedCornerShape(22.dp),
                color = Primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "保存配置",
                    color = Color.White,
                    modifier = Modifier.padding(14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RowScope.PresetButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 12.dp),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
