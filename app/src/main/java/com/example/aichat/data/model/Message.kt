package com.example.aichat.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体 — 1.0 稳定版字段冻结。
 *
 * 字段约束：
 *   · conversationId 指向 conversations.id 的外键，CASCADE 删除
 *   · role 只接受 "user" / "assistant" / "system"，由发送阶段保证，Room 不做 enum 约束
 *   · imageUrls 是可选字段，存储逗号分隔的 content:// URI 列表字符串
 *   · timestamp 用于排序，建议使用 System.currentTimeMillis()
 *
 * 迁移注释（未来版本参考）：
 *   · 版本 1 — 初始: id / conversationId / role / content / imageUrls / timestamp
 *   · 若后续追加 role = "tool"，需要新增 toolCallId / toolName 字段
 */
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
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    /**
     * 图片附件 — JSON 数组字符串，形如 ["content://media/.../123","content://..."]
     * 空字符串 / null 都表示"没有图片附件"。
     * 注：这里不使用 Kotlinx Serialization，避免 Room 对 @Serializable 的编译推断干扰。
     */
    @ColumnInfo(name = "imageUrls", typeAffinity = ColumnInfo.TEXT)
    val imageUrls: String? = null,
    val timestamp: Long
) {
    /** 判断是否有图片附件。 */
    fun hasImages(): Boolean = !imageUrls.isNullOrBlank() && imageUrls != "[]"

    /** 解析图片 URL 列表 — 只接受严格的 ["url1","url2"] 格式。 */
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
        /** 把图片 URL 列表编码为 Message.imageUrls 字段 — 始终输出 JSON Array 字符串。 */
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
