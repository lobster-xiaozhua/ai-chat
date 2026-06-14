package com.example.aichat.data.repository

import com.example.aichat.data.remote.ModelsApiService
import com.example.aichat.data.remote.dto.ModelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型列表仓库。
 *
 * 职责：
 *   1. 根据用户配置的 baseUrl + apiKey 调用 /v1/models，拿到所有可用模型；
 *   2. 按 owned_by 分组，便于 UI 显示；
 *   3. 失败时返回一组合理的硬编码备选模型，防止 UI 空窗。
 *
 * 约定：modelId 就是 API 返回的 "id" 字段，直接用它来调用 /chat/completions。
 */
@Singleton
class ModelsRepository @Inject constructor(
    private val apiService: ModelsApiService
) {

    // 内存缓存（避免重复网络请求）。以 baseUrl 为 key。ConcurrentHashMap 保证线程安全。
    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<GroupedModels>>()

    /**
     * 获取模型列表（带缓存）。
     *   @param refresh true 时强制拉最新，忽略缓存。
     *   @return 按提供商分组的模型列表。
     */
    suspend fun getGroupedModels(
        baseUrl: String,
        apiKey: String,
        refresh: Boolean = false
    ): Result<List<GroupedModels>> = withContext(Dispatchers.IO) {
        try {
            val key = baseUrl.trimEnd('/')
            if (!refresh && cache.containsKey(key)) {
                return@withContext Result.success(cache[key]!!)
            }

            val url = buildModelsUrl(baseUrl)
            val auth = "Bearer $apiKey"

            val response = apiService.getModels(url = url, auth = auth)

            // 去掉 object != "model" 的条目（有些后端会返回非模型）
            val items = response.data.filter {
                it.objectType?.equals("model", true) ?: true
            }

            val grouped = items
                .groupBy { (it.owned_by ?: "other").lowercase() }
                .map { (provider, list) ->
                    GroupedModels(
                        provider = provider.friendlyProvider(),
                        models = list.map { Model(it.id, it.owned_by) }
                            .sortedBy { it.id }
                    )
                }
                .sortedBy { it.provider }
                .ifEmpty { fallback(baseUrl) }

            cache[key] = grouped
            Result.success(grouped)
        } catch (t: Throwable) {
            // 失败就用 fallback，不让 UI 空；记录日志便于排查
            android.util.Log.w("ModelsRepository", "获取模型列表失败，使用 fallback", t)
            Result.success(fallback(baseUrl))
        }
    }

    /**
     * 将 baseUrl 变成 /v1/models URL。
     * 处理方式：先 trimEnd('/')，然后检查是否以 "/v1" 结尾。
     *   "https://integrate.api.nvidia.com/v1"    → ".../v1/models"
     *   "https://api.deepseek.com"                 → ".../v1/models"
     *
     * 为了最大兼容性，始终尝试 "{trimmed}/models"，其中 trimmed 以 "/v1" 结尾。
     */
    private fun buildModelsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1")) {
            "$trimmed/models"
        } else {
            "$trimmed/v1/models"
        }
    }

    /**
     * 在 API 调用失败 / 无模型时提供一组合理的默认值。
     * 根据 baseUrl 粗略推断提供商。
     */
    private fun fallback(baseUrl: String): List<GroupedModels> {
        val url = baseUrl.lowercase()
        return when {
            "nvidia" in url -> listOf(
                GroupedModels("NVIDIA", listOf(
                    "nvidia/nemotron-nano-12b-v2-vl",
                    "nvidia/nemotron-4-340b-instruct",
                    "meta/llama-3.1-8b-instruct",
                    "meta/llama-3.2-11b-vision-instruct",
                    "mistralai/mistral-7b-instruct-v0.3",
                    "qwen/qwen2.5-72b-instruct",
                ).map { Model(it, it.substringBefore("/")) })
            )
            "deepseek" in url -> listOf(
                GroupedModels("DeepSeek", listOf(
                    Model("deepseek-chat", "deepseek"),
                    Model("deepseek-coder", "deepseek"),
                ))
            )
            else -> listOf(
                GroupedModels("其他", listOf(
                    Model("default", "unknown"),
                ))
            )
        }
    }

    /** owned_by 的简单映射 —— 让用户看到的是中文/友好名，而不是 raw string */
    private fun String.friendlyProvider(): String = when {
        this == "nvidia" -> "NVIDIA"
        this.startsWith("meta") -> "Meta"
        this.startsWith("deepseek") -> "DeepSeek"
        this.startsWith("mistralai") -> "Mistral"
        this.startsWith("qwen") -> "Qwen"
        this.startsWith("openai") -> "OpenAI"
        else -> this.replaceFirstChar { it.uppercase() }
    }
}

/** UI 使用的数据类 */
data class Model(
    val id: String,
    val provider: String? = null,
)

data class GroupedModels(
    val provider: String,
    val models: List<Model>,
)
