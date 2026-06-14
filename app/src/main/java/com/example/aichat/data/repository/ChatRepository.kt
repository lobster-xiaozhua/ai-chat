package com.example.aichat.data.repository

import com.example.aichat.data.local.db.ConversationDao
import com.example.aichat.data.local.db.MessageDao
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.model.Message
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    fun getConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<Conversation>> =
        conversationDao.searchConversations(query)

    suspend fun createConversation(conversation: Conversation) {
        conversationDao.insert(conversation)
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDao.update(conversation)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDao.delete(conversation)
    }

    suspend fun deleteAllConversations() {
        conversationDao.deleteAll()
    }

    fun getMessages(conversationId: String): androidx.paging.PagingSource<Int, Message> =
        messageDao.getMessages(conversationId)

    fun getMessagesFlow(conversationId: String): kotlinx.coroutines.flow.Flow<List<Message>> =
        messageDao.getMessagesFlow(conversationId)

    suspend fun getMessagesAsList(conversationId: String): List<Message> =
        messageDao.getMessagesAsList(conversationId)

    suspend fun insertMessage(message: Message): Long {
        return messageDao.insert(message)
    }

    suspend fun deleteMessage(message: Message) {
        messageDao.delete(message)
    }

    suspend fun sendMessageAndUpdateConversation(
        conversation: Conversation,
        userMessage: String,
        assistantMessage: String
    ) {
        // 插入用户消息
        insertMessage(
            Message(
                conversationId = conversation.id,
                role = "user",
                content = userMessage,
                timestamp = System.currentTimeMillis()
            )
        )

        // 插入助手消息
        insertMessage(
            Message(
                conversationId = conversation.id,
                role = "assistant",
                content = assistantMessage,
                timestamp = System.currentTimeMillis()
            )
        )

        // 更新会话的 updatedAt 和标题（首次消息时）
        val updated = conversation.copy(
            title = if (conversation.title == "新对话") {
                userMessage.take(10)
            } else {
                conversation.title
            },
            updatedAt = System.currentTimeMillis()
        )
        updateConversation(updated)
    }
}
