package com.example.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Message
import com.example.aichat.data.repository.AiRepository
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

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentModel = MutableStateFlow("deepseek-chat")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private var generationJob: Job? = null
    private var currentConversationId: String = ""
    private var historyForApi = mutableListOf<Pair<String, String>>()

    fun setConversation(conversationId: String) {
        currentConversationId = conversationId
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val userMsg = Message(
            conversationId = currentConversationId,
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        val currentList = _messages.value.toMutableList()
        currentList.add(userMsg)
        _messages.value = currentList

        _isGenerating.value = true
        _error.value = null

        generationJob = viewModelScope.launch {
            val baseUrl = "https://api.deepseek.com"
            val apiKey = settingsRepository.getApiKey()
            val temperature = settingsRepository.getTemperature().toDoubleOrNull() ?: 1.0
            val systemPrompt = settingsRepository.getSystemPrompt()

            historyForApi.add(Pair("user", text))

            var assistantContent = ""
            val assistantMsg = Message(
                conversationId = currentConversationId,
                role = "assistant",
                content = "",
                timestamp = System.currentTimeMillis()
            )
            val listWithAssistant = _messages.value.toMutableList()
            listWithAssistant.add(assistantMsg)
            _messages.value = listWithAssistant

            try {
                aiRepository.streamChat(
                    messages = historyForApi,
                    systemPrompt = systemPrompt,
                    baseUrl = baseUrl,
                    model = _currentModel.value,
                    apiKey = apiKey,
                    temperature = temperature
                ).collect { token ->
                    assistantContent += token
                    val updated = _messages.value.toMutableList()
                    val idx = updated.indexOfLast { it.role == "assistant" && it.content.isEmpty() || it.id == assistantMsg.id }
                    if (idx >= 0) {
                        updated[idx] = updated[idx].copy(content = assistantContent)
                    } else {
                        updated[updated.size - 1] = updated[updated.size - 1].copy(content = assistantContent)
                    }
                    _messages.value = updated
                }

                historyForApi.add(Pair("assistant", assistantContent))

            } catch (e: Exception) {
                _error.value = "消息生成失败: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) { _currentModel.value = model }
}
