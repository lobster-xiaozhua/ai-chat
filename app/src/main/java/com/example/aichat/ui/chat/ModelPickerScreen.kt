package com.example.aichat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.repository.GroupedModels
import com.example.aichat.data.repository.ModelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全屏幕模型选择器。
 *
 * 交互：
 *   1. 进入后异步拉取 /v1/models，展示为 {provider: [models]} 分组
 *   2. 用户勾选 → 加入「已选模型」集合
 *   3. 点击某模型同时「设为默认」→ 保存为 defaultModel
 *   4. 返回键返回主界面
 *   5. 顶部刷新按钮强制重新拉
 *   6. 搜索框按关键字模糊过滤（搜索框 + 模型名 / 提供商）
 */

@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    private val modelsRepository: ModelsRepository,
    private val settingsRepository: com.example.aichat.data.repository.SettingsRepository,
) : ViewModel() {

    private val _groups = MutableStateFlow<List<GroupedModels>>(emptyList())
    val groups: StateFlow<List<GroupedModels>> = _groups.asStateFlow()

    private val _selectedIds = MutableStateFlow(emptyList<String>())
    val selectedIds: StateFlow<List<String>> = _selectedIds.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSelectedModelIds().collect { _selectedIds.value = it }
        }
        refresh(false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _loading.value = true
            // getBaseUrl() 返回 Flow（DataStore 无限流），必须用 first() 取首个值
            // 原写法 flow.collect { return@collect } 会永远挂起（collect 是终端操作符）
            val baseUrl = settingsRepository.getBaseUrl().first()
            val apiKey = settingsRepository.getApiKey()
            val result = modelsRepository.getGroupedModels(baseUrl, apiKey, force)
            _groups.value = result.getOrElse { emptyList() }
            _loading.value = false
        }
    }

    fun toggleSelected(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableList().also {
            if (it.remove(id)) Unit else it.add(id)
        }
    }

    fun saveAndSetDefault(id: String) {
        viewModelScope.launch {
            val current = _selectedIds.value.toMutableList()
            if (id !in current) current.add(id)
            settingsRepository.setSelectedModelIds(current)
            settingsRepository.setDefaultModel(id)
        }
    }

    fun saveOnly() {
        viewModelScope.launch { settingsRepository.setSelectedModelIds(_selectedIds.value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(onBack: () -> Unit = {}) {
    val vm: ModelPickerViewModel = hiltViewModel()
    val groups by vm.groups.collectAsState()
    val selectedIds by vm.selectedIds.collectAsState()
    val loading by vm.loading.collectAsState()

    var keyword by remember { mutableStateOf("") }

    // 过滤：搜索关键字（小写）在模型 id 或 provider 中出现
    val kw = keyword.trim().lowercase()
    val filtered = remember(groups, kw) {
        if (kw.isBlank()) groups
        else groups.mapNotNull { group ->
            val models = group.models.filter {
                it.id.lowercase().contains(kw) || group.provider.lowercase().contains(kw)
            }
            if (models.isEmpty()) null else GroupedModels(group.provider, models)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择模型", style = MaterialTheme.typography.titleMedium)
                        Text("已选 ${selectedIds.size} 个",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { vm.saveOnly(); onBack() }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)) {
            // 搜索框
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("搜索模型名或提供商", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            if (loading && filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载模型中...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    filtered.forEach { group ->
                        item(key = "header-${group.provider}") {
                            Text(
                                text = group.provider,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        items(group.models, key = { it.id }) { model ->
                            val selected = model.id in selectedIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selected,
                                    onCheckedChange = { vm.toggleSelected(model.id) })
                                Text(text = model.id, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Surface(
                                    onClick = { vm.saveAndSetDefault(model.id); onBack() },
                                    color = Color.Transparent,
                                    modifier = Modifier.padding(start = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("设为默认",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty() && !loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Text("没有匹配的模型", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}
