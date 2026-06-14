package com.example.aichat.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release API 响应体（仅保留更新检查所需字段）。
 * GET https://api.github.com/repos/{owner}/{repo}/releases/latest
 */
@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
    val size: Long = 0
)
