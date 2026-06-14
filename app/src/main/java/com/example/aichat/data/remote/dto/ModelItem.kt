package com.example.aichat.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * /v1/models 接口返回的单个模型信息。
 *   { "id": "nvidia/nemotron-nano-12b-v2-vl", "object": "model", "owned_by": "nvidia", ... }
 *
 * 我们只关心 id（= modelId）和 owned_by（= 提供商）。
 * 用于在模型选择界面分组 + 搜索。
 */
@Serializable
data class ModelItem(
    val id: String,
    val owned_by: String? = null,
    val object_field: String? = null,
)

@Serializable
data class ModelsResponse(
    val data: List<ModelItem> = emptyList(),
    val `object`: String? = null,
)
