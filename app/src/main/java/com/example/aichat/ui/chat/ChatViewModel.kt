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
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

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
        // 后台收集设置值到 StateFlow，供 sendMessage 同步访问
        viewModelScope.launch {
            settingsRepository.getBaseUrl().collect {
                if (it.isNotBlank()) _baseUrl.value = it
            }
        }
        viewModelScope.launch {
            settingsRepository.getTemperature().collect {
                if (it.isNotBlank()) _temperatureStr.value = it
            }
        }
        viewModelScope.launch {
            settingsRepository.getSystemPrompt().collect {
                _systemPrompt.value = it
            }
        }
        viewModelScope.launch {
            settingsRepository.getDefaultModel().collect {
                if (it.isNotBlank() && !_isGenerating.value) _currentModel.value = it
            }
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

        viewModelScope.launch {
            val history = chatRepository.getMessagesAsList(conversationId)
            _messages.value = history.toMutableList()
            historyForApi = history
                .filter { it.role == "user" || it.role == "assistant" }
                .map { it.role to it.content }
                .toMutableList()
        }
    }

    /**
     * 发送消息。逻辑：
     *   1. 构造用户消息，追加到列表，写入数据库
     *   2. 调用 AI 流式接口，逐 token 更新列表中最后一条 assistant 消息
     *   3. 结束后：写入 assistant 消息，更新会话标题
     *   4. 异常：根据 ApiException 类型给出对用户友好的提示
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val userMsg = Message(
            conversationId = currentConversationId,
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )

        // —— 先把用户消息显示出来并写入 DB ——
        val currentList = _messages.value.toMutableList()
        currentList.add(userMsg)
        _messages.value = currentList
        historyForApi.add("user" to text)

        viewModelScope.launch {
            runCatching { chatRepository.insertMessage(userMsg) }
        }

        _isGenerating.value = true
        _error.value = null

        // assistant 占位消息：列表最后一条之后插入一条空内容项
        val assistantMsgPlaceholder = Message(
            conversationId = currentConversationId,
            role = "assistant",
            content = "",
            timestamp = System.currentTimeMillis()
        )
        val listWithAssistant = _messages.value.toMutableList()
        listWithAssistant.add(assistantMsgPlaceholder)
        _messages.value = listWithAssistant

        val assistantIndex = _messages.value.size - 1
        var assistantContent = ""

        generationJob = viewModelScope.launch {
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
                    assistantContent += token
                    // 只替换 assistant 那条：不可变列表语义，其它 item 保持不变
                    val updated = java.util.ArrayList(_messages.value)
                    updated[assistantIndex] = updated[assistantIndex].copy(content = assistantContent)
                    _messages.value = updated
                }

                // —— 流结束：补写 assistant 消息到数据库，更新会话标题 ——
                if (assistantContent.isNotEmpty()) {
                    val finalMsg = Message(
                        conversationId = currentConversationId,
                        role = "assistant",
                        content = assistantContent,
                        timestamp = System.currentTimeMillis()
                    )
                    // 更新 UI 列表中的最后内容（确保 timestamp 等字段一致）
                    val finalList = java.util.ArrayList(_messages.value)
                    finalList[assistantIndex] = finalMsg
                    _messages.value = finalList
                    historyForApi.add("assistant" to assistantContent)

                    viewModelScope.launch {
                        runCatching { chatRepository.insertMessage(finalMsg) }
                    }
                } else {
                    // 空回复：移除占位
                    val finalList = _messages.value.toMutableList()
                    finalList.removeAt(assistantIndex)
                    _messages.value = finalList
                    historyForApi.removeLastOrNull()
                }

            } catch (e: ApiException) {
                _error.value = e.message ?: "发生错误"
                rollbackAssistant(assistantIndex)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止生成：不显示错误，但保留已生成的内容
                // 同样写入 DB，让用户能看到已生成的部分
                if (assistantContent.isNotEmpty()) {
                    val finalMsg = Message(
                        conversationId = currentConversationId,
                        role = "assistant",
                        content = assistantContent,
                        timestamp = System.currentTimeMillis()
                    )
                    val finalList = java.util.ArrayList(_messages.value)
                    finalList[assistantIndex] = finalMsg
                    _messages.value = finalList
                    historyForApi.add("assistant" to assistantContent)
                    viewModelScope.launch {
                        runCatching { chatRepository.insertMessage(finalMsg) }
                    }
                } else {
                    rollbackAssistant(assistantIndex)
                }
                throw e
            } catch (e: Exception) {
                _error.value = "消息生成失败：${e.message}"
                rollbackAssistant(assistantIndex)
            } finally {
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
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) { _currentModel.value = model }

    /**
     * 回滚：移除 assistant 占位消息（出错 / 取消且无内容时）。
     */
    private fun rollbackAssistant(index: Int) {
        val current = _messages.value
        if (index in current.indices) {
            val rolled = current.toMutableList()
            rolled.removeAt(index)
            _messages.value = rolled
        }
        // 同时把之前加到 historyForApi 的那一条 user 回滚，避免下次请求上下文错乱
        if (historyForApi.lastOrNull()?.first == "user") {
            historyForApi.removeLast()
        }
    }
}
