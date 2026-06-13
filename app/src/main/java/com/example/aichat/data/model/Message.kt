package com.example.aichat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    /**
     * 图片附件 —— JSON 数组字符串，形如：
     *   ["content://media/external/images/media/123","content://..."]
     *
     * 设计考虑：
     *   · 仅用户消息可能有图片（assistant 不产生图片附件）
     *   · content:// URI 可被系统相册长期持有（takePersistableUriPermission 已处理）
     *   · 没有附件时为 null / 空字符串
     */
    val imageUrls: String? = null,
    val timestamp: Long
) {
    /** 判断是否有图片附件。*/
    fun hasImages(): Boolean = !imageUrls.isNullOrBlank() && imageUrls != "[]"

    /** 解析图片 URL 列表。*/
    fun imageUrlList(): List<String> {
        if (imageUrls.isNullOrBlank()) return emptyList()
        val raw = imageUrls.trim()
        if (!raw.startsWith("[") || !raw.endsWith("]")) return emptyList()
        val inner = raw.substring(1, raw.length - 1).trim()
        if (inner.isBlank()) return emptyList()
        return inner.split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
    }

    companion object {
        /** 把图片 URL 列表编码为 Message.imageUrls 字段。*/
        fun encodeImageUrls(urls: List<String>): String? {
            if (urls.isEmpty()) return null
            return urls.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ","
            ) { "\"$it\"" }
        }
    }
}
