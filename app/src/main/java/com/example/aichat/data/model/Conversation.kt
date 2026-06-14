package com.example.aichat.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话实体 — 1.0 稳定版字段冻结。
 *
 * 架构注释（未来迁移参考）：
 *   · 版本 1 — 初始字段: id / title / systemPrompt / createdAt / updatedAt
 *   · 版本 1 — 补充冗余查询字段: lastMessage / messageCount
 *     （避免会话列表页对整个 messages 表做 COUNT 子查询）
 *
 * 需要迁移时在 AppDatabase 的 migrations 列表追加 Migration 对象。
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"]), Index(value = ["isPinned"])]
)
data class Conversation(
    @PrimaryKey val id: String,
    var title: String,
    val systemPrompt: String = "",
    val createdAt: Long,
    var updatedAt: Long,
    /** 最近一条消息的文本摘要（最长 120 chars），会话列表页直接显示，避免 join 查询 */
    var lastMessage: String? = null,
    /** 会话内消息总数（由 MessageDao 触发的观察者维护），避免 COUNT(*) 全表扫描 */
    var messageCount: Int = 0,
    /** 是否置顶 */
    var isPinned: Boolean = false
)
