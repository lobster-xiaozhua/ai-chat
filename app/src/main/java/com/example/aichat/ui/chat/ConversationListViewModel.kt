package com.example.aichat.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ConvListVM"
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.getConversations().collect { list ->
                // 搜索模式下由 searchJob 更新，避免覆盖搜索结果
                if (searchJob?.isActive != true) {
                    _conversations.value = list
                }
            }
        }
    }

    suspend fun createNewConversation(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatRepository.createConversation(
            Conversation(
                id = id,
                title = "新对话",
                systemPrompt = "",
                createdAt = now,
                updatedAt = now
            )
        )
        Log.d(TAG, "Created new conversation: $id")
        return id
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            val conv = _conversations.value.firstOrNull { it.id == id } ?: return@launch
            chatRepository.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            Log.d(TAG, "Renamed conversation $id to '$newTitle'")
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val conv = _conversations.value.firstOrNull { it.id == id } ?: return@launch
            chatRepository.deleteConversation(conv)
            Log.d(TAG, "Deleted conversation $id")
        }
    }

    fun togglePin(id: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            chatRepository.updateIsPinned(id, !currentlyPinned)
            Log.d(TAG, "Toggled pin for $id: pinned=${!currentlyPinned}")
        }
    }

    /**
     * 搜索会话列表；空 query 时退回到全量列表。
     */
    fun searchConversations(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            // 立即刷新全量数据
            viewModelScope.launch {
                _conversations.value = chatRepository.getConversations().first()
            }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _conversations.value = chatRepository.searchConversations(query).first()
        }
    }

    suspend fun exportConversationAsMarkdown(id: String): String {
        Log.d(TAG, "Exporting conversation $id as Markdown")
        return chatRepository.exportConversationAsMarkdown(id)
    }
}
