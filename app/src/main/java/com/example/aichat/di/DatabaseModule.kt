package com.example.aichat.di

import android.content.Context
import androidx.room.Room
import com.example.aichat.data.local.db.AppDatabase
import com.example.aichat.data.local.db.ConversationDao
import com.example.aichat.data.local.db.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "aichat_db"
        )
        // TODO: 实现正式的 Room Migration 策略，避免 schema 升级时静默删除用户数据
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
}
