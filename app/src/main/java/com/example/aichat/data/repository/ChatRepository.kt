package com.example.aichat.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.aichat.data.local.db.AppDatabase
import com.example.aichat.data.local.db.ConversationDao
import com.example.aichat.data.local.db.MessageDao
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val db: AppDatabase
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

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

    suspend fun deleteAllConversations() = db.withTransaction {
        messageDao.deleteByAll()
        conversationDao.deleteAll()
    }

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

    suspend fun deleteMessageById(id: Long) {
        Log.d(TAG, "Deleting message id=$id")
        messageDao.deleteById(id)
    }

    suspend fun updateIsPinned(id: String, pinned: Boolean) {
        Log.d(TAG, "Updating isPinned for conv=$id to $pinned")
        conversationDao.updateIsPinned(id, pinned)
    }

    suspend fun getConversationCount(): Int = conversationDao.getConversationCount()

    suspend fun getMessageCount(): Int = messageDao.getMessageCount()

    /**
     * 导出会话为 Markdown 格式
     */
    suspend fun exportConversationAsMarkdown(conversationId: String): String {
        val conv: Conversation = try {
            conversationDao.getAllConversations().first().firstOrNull { it.id == conversationId }
        } catch (_: Exception) {
            null
        } ?: run {
            Log.w(TAG, "Conversation $conversationId not found for export")
            return ""
        }
        val messages = messageDao.getMessagesAsList(conversationId)
        val sb = StringBuilder()
        sb.appendLine("# ${conv.title}")
        sb.appendLine()
        sb.appendLine("> 导出时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(conv.updatedAt))}")
        sb.appendLine()
        for (msg in messages) {
            val label = if (msg.role == "user") "🧑 用户" else "🤖 助手"
            sb.appendLine("### $label")
            sb.appendLine()
            sb.appendLine(msg.content)
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        Log.d(TAG, "Exported conversation ${conv.id} as Markdown (${sb.length} chars)")
        return sb.toString()
    }

    suspend fun sendMessageAndUpdateConversation(
        conversation: Conversation,
        userMessage: String,
        assistantMessage: String
    ) = db.withTransaction {
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
