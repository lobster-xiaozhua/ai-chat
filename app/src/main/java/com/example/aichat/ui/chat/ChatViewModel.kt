package com.example.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Message
import com.example.aichat.data.repository.AiRepository
import com.example.aichat.data.repository.ApiException
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ======================================================================
 * 聊天界面的状态与逻辑中枢
 * ======================================================================
 * 暴露的 StateFlow：
 *   messages            → 已完成的历史消息
 *   streamingAssistant  → 当前正在生成的 assistant 文本（逐 token 更新）
 *   isGenerating        → 是否正在生成
 *   error               → 错误信息（UI 层用 Snackbar 展示）
 *   currentModel        → 当前选择的模型名
 *   selectedModelIds    → 在 ModelPicker 中勾选的常用模型列表
 *   jsonMode            → 是否启用 JSON 输出模式（切换开关）
 *   pendingImageUrls    → 用户刚选择的图片 URL（多模态输入用，发送后清空）
 * ====================================================================== */

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /* ---------- 状态 ---------- */
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _streamingAssistant = MutableStateFlow<String?>(null)
    val streamingAssistant: StateFlow<String?> = _streamingAssistant.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentModel = MutableStateFlow("deepseek-chat")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _jsonMode = MutableStateFlow(false)
    val jsonMode: StateFlow<Boolean> = _jsonMode.asStateFlow()

    // 待发送的图片附件（多模态输入），sendMessage 后自动清空
    private val _pendingImageUrls = MutableStateFlow<List<String>>(emptyList())
    val pendingImageUrls: StateFlow<List<String>> = _pendingImageUrls.asStateFlow()

    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    private val _temperatureStr = MutableStateFlow("1.0")
    private val _systemPrompt = MutableStateFlow("")

    private var generationJob: Job? = null
    private var currentConversationId: String = ""
    private var historyForApi = mutableListOf<Pair<String, String>>()

    /* ---------- 初始化：从 DataStore 异步加载设置 ---------- */
    init {
        viewModelScope.launch {
            settingsRepository.getBaseUrl().collect { if (it.isNotBlank()) _baseUrl.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getTemperature().collect { if (it.isNotBlank()) _temperatureStr.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getSystemPrompt().collect { _systemPrompt.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getDefaultModel().collect { if (it.isNotBlank() && !_isGenerating.value) _currentModel.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getSelectedModelIds().collect { _selectedModelIds.value = it }
        }
    }

    /* ---------- 对外操作 ---------- */

    fun setJsonMode(on: Boolean) { _jsonMode.value = on }
    fun toggleJsonMode() { _jsonMode.value = !_jsonMode.value }

    fun addImageUrl(url: String) {
        _pendingImageUrls.value = _pendingImageUrls.value + url
    }

    fun removeImageUrl(url: String) {
        _pendingImageUrls.value = _pendingImageUrls.value - url
    }

    fun clearPendingImages() {
        _pendingImageUrls.value = emptyList()
    }

    fun setConversation(conversationId: String) {
        if (currentConversationId == conversationId && _messages.value.isNotEmpty()) return
        currentConversationId = conversationId
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null
        _pendingImageUrls.value = emptyList()

        viewModelScope.launch {
            val history = chatRepository.getMessagesAsList(conversationId)
            _messages.value = history
            historyForApi = history
                .filter { it.role == "user" || it.role == "assistant" }
                .map { it.role to it.content }
                .toMutableList()
        }
    }

    /**
     * 发送消息：
     *   1. 用户消息（文本 + 可选图片）立即写入列表
     *   2. 调用 AiRepository 进入流式生成
     *   3. 完成后把 assistant 回复写回列表
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val images = _pendingImageUrls.value // 快照，发送后清空
        _pendingImageUrls.value = emptyList()

        val now = System.currentTimeMillis()
        val userMsg = Message(
            conversationId = currentConversationId,
            role = "user",
            content = text,
            timestamp = now
        )

        _messages.value = _messages.value + userMsg
        historyForApi.add("user" to text)
        viewModelScope.launch { runCatching { chatRepository.insertMessage(userMsg) } }

        _isGenerating.value = true
        _error.value = null
        _streamingAssistant.value = ""

        generationJob = viewModelScope.launch {
            val builder = StringBuilder(2048)
            var lastEmitNs = 0L
            val throttleNs = 50_000_000L
            var tokensSinceEmit = 0

            try {
                val apiKey = settingsRepository.getApiKey()
                if (apiKey.isBlank()) throw ApiException.Unauthorized("请先在设置中配置 API Key")

                val temperature = _temperatureStr.value.toDoubleOrNull() ?: 1.0

                aiRepository.streamChat(
                    messages = historyForApi,
                    systemPrompt = _systemPrompt.value,
                    baseUrl = _baseUrl.value,
                    model = _currentModel.value,
                    apiKey = apiKey,
                    temperature = temperature,
                    jsonMode = _jsonMode.value,
                    userImageUrls = images
                ).collect { token ->
                    builder.append(token)
                    tokensSinceEmit++
                    val now = System.nanoTime()
                    if (now - lastEmitNs > throttleNs || tokensSinceEmit >= 3) {
                        _streamingAssistant.value = builder.toString()
                        lastEmitNs = now
                        tokensSinceEmit = 0
                    }
                }

                val finalContent = builder.toString()
                if (finalContent.isNotEmpty()) {
                    _streamingAssistant.value = finalContent
                    val finalMsg = Message(
                        conversationId = currentConversationId,
                        role = "assistant",
                        content = finalContent,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + finalMsg
                    historyForApi.add("assistant" to finalContent)
                    viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
                }

            } catch (e: ApiException) {
                _error.value = e.message ?: "发生错误"
                historyForApi.removeLastOrNull()
            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = builder.toString()
                if (partial.isNotEmpty()) {
                    _streamingAssistant.value = partial
                    val finalMsg = Message(
                        conversationId = currentConversationId,
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
            } finally {
                _streamingAssistant.value = null
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) {
        _currentModel.value = model
        viewModelScope.launch { settingsRepository.setDefaultModel(model) }
    }
}
