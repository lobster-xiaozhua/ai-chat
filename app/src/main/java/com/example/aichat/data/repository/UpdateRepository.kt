package com.example.aichat.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.aichat.BuildConfig
import com.example.aichat.data.remote.dto.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新仓库 —— 通过 GitHub Releases API 检查新版本并下载 APK。
 *
 * 流程：
 *   1. checkForUpdate() → 请求 GitHub API 获取最新 Release
 *   2. 比较版本号（语义化版本），判断是否需要更新
 *   3. downloadApk() → 下载 APK 到外部缓存目录
 *   4. installApk() → 触发系统安装器
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val context: Context
) {
    companion object {
        private const val TAG = "UpdateRepository"
        // GitHub 仓库地址（与 git remote 对应）
        private const val OWNER = "lobster-xiaozhua"
        private const val REPO = "ai-chat"
        private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    }

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    /**
     * 检查 GitHub 最新 Release，返回 Release 信息或 null（无更新/网络错误）。
     */
    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API 请求失败: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val release = json.decodeFromString<GitHubRelease>(body)

            // 比较版本号
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v")
            val latestVersion = release.tag_name.removePrefix("v")

            if (compareVersions(latestVersion, currentVersion) > 0) {
                Log.d(TAG, "发现新版本: $latestVersion (当前: $currentVersion)")
                release
            } else {
                Log.d(TAG, "已是最新版本: $currentVersion")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败: ${e.message}")
            null
        }
    }

    /**
     * 下载 APK 文件。返回下载后的 File 或 null。
     */
    suspend fun downloadApk(release: GitHubRelease): File? = withContext(Dispatchers.IO) {
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
        if (asset == null) {
            Log.w(TAG, "Release 中未找到 APK 文件")
            return@withContext null
        }

        _isDownloading.value = true
        _downloadProgress.value = 0f

        try {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkDir = File(cacheDir, "updates").also { it.mkdirs() }
            val apkFile = File(apkDir, asset.name)
            // 如果已下载过同版本 APK 且文件完整，直接返回
            if (apkFile.exists() && apkFile.length() == asset.size && isApkValid(apkFile)) {
                Log.d(TAG, "APK 已缓存: ${apkFile.absolutePath}")
                _downloadProgress.value = 1f
                return@withContext apkFile
            }

            val request = Request.Builder().url(asset.downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "APK 下载失败: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength().coerceAtLeast(1)
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        _downloadProgress.value = downloadedBytes.toFloat() / totalBytes
                    }
                }
            }

            Log.d(TAG, "APK 下载完成: ${apkFile.absolutePath} (${downloadedBytes} bytes)")
            apkFile
        } catch (e: Exception) {
            Log.w(TAG, "APK 下载异常: ${e.message}")
            null
        } finally {
            _isDownloading.value = false
        }
    }

    /**
     * 触发系统安装器安装 APK。
     */
    fun installApk(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "已触发 APK 安装: ${apkFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "触发安装失败: ${e.message}")
        }
    }

    /**
     * 验证文件是否为有效 APK（检查 ZIP 魔数 PK\x03\x04）。
     */
    private fun isApkValid(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 语义化版本比较：返回 >0 表示 v1 > v2，<0 表示 v1 < v2，0 表示相等。
     * 支持 "1.2.3" / "1.2.3-beta" 等格式。
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
