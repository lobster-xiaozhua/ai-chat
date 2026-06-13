package com.example.aichat.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"])]
)
data class Conversation(
    @PrimaryKey val id: String,
    var title: String,
    val systemPrompt: String = "",
    val createdAt: Long,
    var updatedAt: Long
)
