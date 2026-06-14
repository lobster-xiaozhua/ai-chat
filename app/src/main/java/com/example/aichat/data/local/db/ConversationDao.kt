package com.example.aichat.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aichat.data.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<Conversation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
