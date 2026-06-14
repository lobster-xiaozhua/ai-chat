package com.example.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    // 搜索模式下 collect 的 job，切回全量列表前 cancel，避免覆盖全量数据
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.getConversations().collect { list ->
                _conversations.value = list
            }
        }
    }

    fun createNewConversation(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            chatRepository.createConversation(
                Conversation(
                    id = id,
                    title = "新对话",
                    systemPrompt = "",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return id
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            val conv = _conversations.value.firstOrNull { it.id == id } ?: return@launch
            chatRepository.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val conv = _conversations.value.firstOrNull { it.id == id } ?: return@launch
            chatRepository.deleteConversation(conv)
        }
    }

    /**
     * 搜索会话列表；空 query 时退回到全量列表。
     * 使用独立 job 管理搜索协程，确保切回全量列表时不会被搜索结果覆盖。
     */
    fun searchConversations(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            chatRepository.searchConversations(query).collect {
                _conversations.value = it
            }
        }
    }
}
