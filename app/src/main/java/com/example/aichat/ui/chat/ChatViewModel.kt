package com.example.aichat.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Message
import com.example.aichat.data.repository.AiRepository
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.SettingsRepository
import com.example.aichat.util.toDataUriList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
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

    // —— Think 模式（深度推理：更慢、更全面；通过 system prompt + temperature 传递）
    private val _thinkMode = MutableStateFlow(false)
    val thinkMode: StateFlow<Boolean> = _thinkMode.asStateFlow()

    // —— Search 模式（联网/检索增强：通过 system prompt 告知模型可以查询上下文）
    private val _searchMode = MutableStateFlow(false)
    val searchMode: StateFlow<Boolean> = _searchMode.asStateFlow()

    // 待发送图片附件（content:// URI 字符串列表）
    private val _pendingImageUrls = MutableStateFlow<List<String>>(emptyList())
    val pendingImageUrls: StateFlow<List<String>> = _pendingImageUrls.asStateFlow()

    // 用户已选模型列表（用于快速切换）
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

    /* ---------- 对外操作：JSON 模式 ---------- */
    fun setJsonMode(on: Boolean) { _jsonMode.value = on }
    fun toggleJsonMode() { _jsonMode.value = !_jsonMode.value }

    /* ---------- 对外操作：Think / Search 模式 ---------- */
    fun toggleThink() { _thinkMode.value = !_thinkMode.value }
    fun toggleSearch() { _searchMode.value = !_searchMode.value }

    /* ---------- 对外操作：图片附件 ---------- */

    /** 添加一组图片 URI（来自 Photo Picker）*/
    fun addImageUrls(urls: List<String>) {
        val current = _pendingImageUrls.value
        val combined = current + urls.filterNot { it in current }
        _pendingImageUrls.value = combined
    }

    /** 从待发送列表移除一张图片 */
    fun removeImageUrl(url: String) {
        _pendingImageUrls.value = _pendingImageUrls.value - url
    }

    /** 清空待发送图片列表 */
    fun clearPendingImages() {
        _pendingImageUrls.value = emptyList()
    }

    /* ---------- 对外操作：会话与消息 ---------- */

    fun setConversation(conversationId: String) {
        if (currentConversationId == conversationId && _messages.value.isNotEmpty()) return
        currentConversationId = conversationId
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null
        clearPendingImages()

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
     * 发送消息 —— 支持纯文本、纯图片、文本+图片三种组合。
     *   · 文本/图片立即写入消息列表（消息气泡可见）
     *   · 异步 content:// → data://（base64）转换，供多模态 API 使用
     *   · 流式响应期间逐 token 更新界面
     *   · 完成后 assistant 消息写回持久层
     */
    fun sendMessage(text: String) {
        // snapshot 当前图片列表，发送后清空
        val images = _pendingImageUrls.value
        if (text.isBlank() && images.isEmpty()) return
        _pendingImageUrls.value = emptyList()

        val now = System.currentTimeMillis()
        val userMsg = Message(
            conversationId = currentConversationId,
            role = "user",
            content = text,
            imageUrls = Message.encodeImageUrls(images),
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
                if (apiKey.isBlank()) throw com.example.aichat.data.repository.ApiException.Unauthorized("请先在设置中配置 API Key")

                val temperature = _temperatureStr.value.toDoubleOrNull() ?: 1.0

                // 把 content:// URI 异步转换为 base64 data URI（多模态 API 需要）
                val dataUriList = if (images.isNotEmpty()) images.toDataUriList(context, maxKb = 512) else emptyList()

                aiRepository.streamChat(
                    messages = historyForApi,
                    systemPrompt = _systemPrompt.value,
                    baseUrl = _baseUrl.value,
                    model = _currentModel.value,
                    apiKey = apiKey,
                    temperature = temperature,
                    jsonMode = _jsonMode.value,
                    userImageUrls = dataUriList,
                    thinkMode = _thinkMode.value,
                    searchMode = _searchMode.value
                ).collect { token ->
                    builder.append(token)
                    tokensSinceEmit++
                    val now2 = System.nanoTime()
                    if (now2 - lastEmitNs > throttleNs || tokensSinceEmit >= 3) {
                        _streamingAssistant.value = builder.toString()
                        lastEmitNs = now2
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
            } catch (e: com.example.aichat.data.repository.ApiException) {
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
