package com.example.aichat.data.repository

import com.example.aichat.data.remote.EmbeddingsApiService
import com.example.aichat.data.remote.dto.EmbeddingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt as mathSqrt

/* ============================================================================
 * 本地向量库 + RAG 检索
 *
 *   · 简化实现：
 *      1) 用 ConcurrentHashMap 内存存储 (id -> VectorDoc)
 *      2) 若提供 apiKey + baseUrl，则远程调用嵌入模型；否则使用"伪嵌入"作为占位
 *      3) 相似度用余弦距离，top-K 返回最相关的若干片段
 *   · 典型用法：
 *      - store(text) 存入文档片段
 *      - retrieve(query, topK) 检索相关片段
 *      - asContextPrompt(query) 把最相关片段拼接到一个字符串供 LLM 用
 * ========================================================================== */

data class VectorDoc(
    val id: String,
    val text: String,
    val vector: FloatArray,
    val createdAt: Long = System.currentTimeMillis()
) {
    // 自定义 equals/hashCode（FloatArray 按内容比较）
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorDoc) return false
        return id == other.id && text == other.text && vector.contentEquals(other.vector)
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}

data class SearchHit(
    val doc: VectorDoc,
    val score: Float  // 余弦相似度 ∈ [-1, 1]，越大越相似
)

@Singleton
class EmbeddingsRepository @Inject constructor(
    private val apiService: EmbeddingsApiService
) {

    // —— 内存向量存储：生产环境可替换为 Room + 二进制 BLOB 或 FAISS
    private val store = ConcurrentHashMap<String, VectorDoc>()

    // —— 默认嵌入维度（用于占位伪嵌入）
    private val fallbackDim = 64

    /* ---------- 对外：写入 ---------- */

    /** 把一段文本存入本地向量库。返回 docId */
    suspend fun store(text: String, baseUrl: String = "", model: String = "", apiKey: String = ""): String =
        withContext(Dispatchers.Default) {
            val vector = embedOrFallback(text, baseUrl, model, apiKey)
            val id = "doc_${System.currentTimeMillis()}_${text.hashCode().and(0xffff)}"
            store[id] = VectorDoc(id, text, vector)
            return@withContext id
        }

    /** 批量写入 */
    suspend fun storeAll(texts: List<String>, baseUrl: String = "", model: String = "", apiKey: String = ""): List<String> {
        return texts.map { store(it, baseUrl, model, apiKey) }
    }

    /* ---------- 对外：检索 ---------- */

    /** top-K 余弦相似度检索 */
    suspend fun retrieve(
        query: String,
        topK: Int = 5,
        baseUrl: String = "",
        model: String = "",
        apiKey: String = ""
    ): List<SearchHit> = withContext(Dispatchers.Default) {
        if (store.isEmpty()) return@withContext emptyList()

        val qv = embedOrFallback(query, baseUrl, model, apiKey)

        return@withContext store.values
            .map { doc -> SearchHit(doc, cosine(qv, doc.vector)) }
            .sortedByDescending { it.score }
            .take(topK)
    }

    /** 直接拼出一个给 LLM 的上下文字符串 */
    suspend fun asContextPrompt(
        query: String,
        topK: Int = 3,
        baseUrl: String = "",
        model: String = "",
        apiKey: String = ""
    ): String {
        val hits = retrieve(query, topK, baseUrl, model, apiKey)
        if (hits.isEmpty()) return ""
        return buildString {
            append("以下是可能相关的知识库片段：\n\n")
            hits.forEachIndexed { i, h ->
                append("[${i + 1}] (相关度 ${"%.2f".format(h.score)})\n")
                append(h.doc.text.take(800))
                append("\n\n")
            }
        }
    }

    /* ---------- 对外：管理 ---------- */

    fun count(): Int = store.size
    fun clear() = store.clear()
    fun remove(docId: String) = store.remove(docId)

    /* ---------- 内部：嵌入（远程 API 不可用时走伪嵌入）---------- */

    private suspend fun embedOrFallback(
        text: String,
        baseUrl: String,
        model: String,
        apiKey: String
    ): FloatArray {
        if (apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()) {
            runCatching { embedRemote(text, baseUrl, model, apiKey) }
                .onSuccess { return it }
        }
        // 伪嵌入：对文本字符做简单哈希投影，产生稳定的稠密向量
        return pseudoEmbed(text)
    }

    private suspend fun embedRemote(
        text: String,
        baseUrl: String,
        model: String,
        apiKey: String
    ): FloatArray = withContext(Dispatchers.IO) {
        val url = if (baseUrl.endsWith("/embeddings")) baseUrl else "$baseUrl/embeddings"
        val auth = "Bearer $apiKey"

        val response = apiService.embeddings(
            url,
            auth,
            EmbeddingRequest(model = model, input = listOf(text))
        ).execute()

        if (!response.isSuccessful) error("embeddings failed: ${response.code()}")
        val body = response.body() ?: error("empty response")
        body.data.firstOrNull()?.embedding?.toFloatArray()
            ?: error("no embedding returned")
    }

    /* ---------- 内部：伪嵌入（纯本地、稳定、可复现）---------- */

    private fun pseudoEmbed(text: String): FloatArray {
        val dim = fallbackDim
        val vec = FloatArray(dim)
        // 把文本字节做滚动哈希，投影到 dim 维 + 简单归一化
        for (i in text.indices) {
            val c = text[i].code
            for (d in 0 until dim) {
                val seed = (d * 2654435761L + c * 16777619L + i * 7L).toInt()
                vec[d] += (((seed xor (seed ushr 16)) and 0xffff) - 0x8000).toFloat() * 0.001f
            }
        }
        return normalize(vec)
    }

    /* ---------- 数学工具 ---------- */

    /** 快速余弦相似度（Float 原生运算，避免 Double 转换） */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a[i]
            val bv = b[i]
            dot += av * bv
            na += av * av
            nb += bv * bv
        }
        if (na == 0f || nb == 0f) return 0f
        return dot / (mathSqrt(na) * mathSqrt(nb))
    }

    private fun normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val norm = mathSqrt(s)
        if (norm < 1e-9f) return v.apply { fill(0f); this[0] = 1f }
        for (i in v.indices) v[i] /= norm
        return v
    }
}
