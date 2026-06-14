package com.example.aichat.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aichat.data.model.Conversation
import com.example.aichat.data.model.Message

@Database(
    entities = [Conversation::class, Message::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
