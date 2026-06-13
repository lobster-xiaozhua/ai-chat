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

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // —————————— UI state ——————————
    // 仅包含"已完成"的历史消息 —— 不随流式 token 变化
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 正在流式生成的 assistant 内容 —— 只由一条气泡读取，每 token 只更新这一个 String
    private val _streamingAssistant = MutableStateFlow<String?>(null)
    val streamingAssistant: StateFlow<String?> = _streamingAssistant.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentModel = MutableStateFlow("deepseek-chat")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    // —————————— 缓存的设置值（从 DataStore 异步收集，提供同步访问）——————————
    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    private val _temperatureStr = MutableStateFlow("1.0")
    private val _systemPrompt = MutableStateFlow("")

    private var generationJob: Job? = null
    private var currentConversationId: String = ""
    private var historyForApi = mutableListOf<Pair<String, String>>()

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
    }

    /**
     * 切换到指定会话：从数据库加载历史消息并重建 API 上下文。
     */
    fun setConversation(conversationId: String) {
        if (currentConversationId == conversationId && _messages.value.isNotEmpty()) return
        currentConversationId = conversationId
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null

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
     * 发送消息。关键路径：
     *   1. 用户消息立即进列表 + 写 DB
     *   2. 设置 _streamingAssistant = "" 触发占位气泡
     *   3. 流式接口逐 token 写 StringBuilder，每到达一个 token 就把整个字符串 toString() 一次
     *      emit 给 _streamingAssistant（String 不可变，Compose 只重组一个气泡）
     *   4. 完成后：一次性将 assistant 消息加入 _messages，写 DB，并清空 _streamingAssistant
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val now = System.currentTimeMillis()
        val userMsg = Message(
            conversationId = currentConversationId,
            role = "user",
            content = text,
            timestamp = now
        )

        // 1. 用户消息进列表（只做一次，创建一个新 List 引用）—— 以后 token 到达不再改它
        _messages.value = _messages.value + userMsg
        historyForApi.add("user" to text)
        viewModelScope.launch { runCatching { chatRepository.insertMessage(userMsg) } }

        // 2. 进入生成状态，流式内容开始为空（UI 会渲染一个空气泡 / 光标）
        _isGenerating.value = true
        _error.value = null
        _streamingAssistant.value = ""

        generationJob = viewModelScope.launch {
            val builder = StringBuilder(2048)  // 预分配，避免反复扩容

            try {
                val apiKey = settingsRepository.getApiKey()
                if (apiKey.isBlank()) {
                    throw ApiException.Unauthorized("请先在设置中配置 API Key")
                }
                val temperature = _temperatureStr.value.toDoubleOrNull() ?: 1.0

                aiRepository.streamChat(
                    messages = historyForApi,
                    systemPrompt = _systemPrompt.value,
                    baseUrl = _baseUrl.value,
                    model = _currentModel.value,
                    apiKey = apiKey,
                    temperature = temperature
                ).collect { token ->
                    builder.append(token)
                    // 只改这一个 String，Compose 只重组流式气泡的 Text
                    _streamingAssistant.value = builder.toString()
                }

                // 3. 完成 —— 写 DB + 加入历史消息列表（一次性，之后所有 token 都不再触发 LazyColumn diff）
                val finalContent = builder.toString()
                if (finalContent.isNotEmpty()) {
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
                historyForApi.removeLastOrNull()  // 回滚刚才的 user
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止生成：若已有内容则保留为一条完整消息
                val partial = builder.toString()
                if (partial.isNotEmpty()) {
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

    /**
     * 停止生成：取消协程 → AiRepository 会联动取消 HTTP Call。
     */
    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) { _currentModel.value = model }
}
