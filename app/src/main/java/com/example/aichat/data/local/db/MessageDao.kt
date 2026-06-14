package com.example.aichat.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.paging.PagingSource
import com.example.aichat.data.model.Message

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): PagingSource<Int, Message>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesFlow(conversationId: String): kotlinx.coroutines.flow.Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesAsList(conversationId: String): List<Message>

    @Insert
    suspend fun insert(message: Message): Long

    @Delete
    suspend fun delete(message: Message)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteByAll()
}
