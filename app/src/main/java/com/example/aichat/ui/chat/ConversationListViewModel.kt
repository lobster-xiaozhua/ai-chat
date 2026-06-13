package com.example.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        loadConversations()
    }

    private fun loadConversations() {
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

    fun searchConversations(query: String) {
        viewModelScope.launch {
            chatRepository.searchConversations(query).collect {
                _conversations.value = it
            }
        }
    }
}
