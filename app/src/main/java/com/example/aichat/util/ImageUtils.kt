package com.example.aichat.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/* ============================================================================
 * ImageUtils —— content:// URI 读取 + MIME 检测 + base64 编码 + 拍照临时输出
 *
 *   · contentUriToDataUri(uri)  →  "data:image/jpeg;base64,/9j/4AAQ..."
 *   · contentUriToInputStream(uri)  →  InputStream (调用方负责关闭)
 *   · getImageMime(uri)  →  "image/jpeg" | "image/png" | "image/webp" ...
 *   · getImageFileName(uri)  →  显示用文件名（可能为空）
 *   · isImageUri(uri)  →  是否为图片 MIME
 *   · compressToJpegBytes(uri, maxSize)  →  压缩到指定尺寸内（降低 API 传输成本）
 *   · createImageFileUri(context)  →  为 TakePicture contract 生成拍照输出 URI
 * ============================================================================ */

/**
 * 把 content:// URI 转成 data URI（base64 内联），可以直接塞进 JSON 发给多模态 API。
 *
 *   data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAS...
 *
 * @param maxKb 若 > 0，按尺寸等比压缩直到体积 < maxKb KB（粗略，避免超大图）。
 * @return 完整的 data URI 字符串；若读取失败返回 null。
 */
suspend fun Uri.contentUriToDataUri(
    context: Context,
    maxKb: Int = 1024
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val mime = getImageMime(context) ?: "image/jpeg"
        val bytes = compressToJpegBytes(context, maxKb) ?: return@runCatching null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:$mime;base64,$encoded"
    }.getOrNull()
}

/**
 * 把 content:// URI 打开成 InputStream；调用方负责关闭。
 */
fun Uri.contentUriToInputStream(context: Context): InputStream? {
    return try {
        context.contentResolver.openInputStream(this)
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * 根据 content:// URI 查询 MIME 类型。
 * 若 ContentResolver 查询不到，则根据文件扩展名回退。
 */
fun Uri.getImageMime(context: Context): String? {
    return when (scheme) {
        ContentResolver.SCHEME_CONTENT -> context.contentResolver.getType(this)
        ContentResolver.SCHEME_FILE -> {
            val name = lastPathSegment?.lowercase() ?: return null
            when {
                name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
                name.endsWith(".png") -> "image/png"
                name.endsWith(".webp") -> "image/webp"
                name.endsWith(".gif") -> "image/gif"
                name.endsWith(".bmp") -> "image/bmp"
                else -> null
            }
        }
        else -> null
    }
}

/**
 * 是否为图片类型的 URI。
 */
fun Uri.isImageUri(context: Context): Boolean {
    val mime = getImageMime(context) ?: return false
    return mime.startsWith("image/")
}

/**
 * 从 content:// URI 获取文件名（仅用于 UI 展示）。
 */
fun Uri.getImageFileName(context: Context): String {
    if (scheme != ContentResolver.SCHEME_CONTENT) {
        return lastPathSegment ?: ""
    }
    return try {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else ""
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * 按 JPEG 压缩输出字节数组；可指定最大 KB 数（粗略等比缩放）。
 *
 * 压缩策略：
 *   1) 先 decodeStream 读原图 → 若原图 < maxKb，直接原样输出（保留 JPEG）
 *   2) 否则循环按比例缩放 + quality 80%，直到满足条件
 */
private suspend fun Uri.compressToJpegBytes(context: Context, maxKb: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        // 先用 inJustDecodeBounds 获取原图尺寸，计算采样率避免 OOM
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(this@compressToJpegBytes)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val targetSize = 2048
        val sampleSize = calculateInSampleSize(bounds, targetSize, targetSize)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        val input = contentUriToInputStream(context) ?: return@withContext null
        val original = input.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return@withContext null

        // 先尝试 quality=80 的直接 JPEG 压缩
        var bytes = original.encodeToJpeg(80)
        if (maxKb <= 0 || bytes.size / 1024 < maxKb) {
            original.recycle()
            return@withContext bytes
        }

        // 过大则等比缩放 —— 每次乘以 0.75，直到满足目标
        var scale = 1.0f
        val maxBytes = maxKb * 1024
        var attempt = 0
        while (bytes.size > maxBytes && attempt < 5) {
            scale *= 0.75f
            val w = (original.width * scale).toInt().coerceAtLeast(64)
            val h = (original.height * scale).toInt().coerceAtLeast(64)
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)
            bytes = scaled.encodeToJpeg(80)
            scaled.recycle()
            attempt++
        }
        original.recycle()
        bytes
    }

/**
 * Bitmap → JPEG 字节数组（带 try/catch 安全封装）。
 */
private fun Bitmap.encodeToJpeg(quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    return try {
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        stream.toByteArray()
    } catch (e: Exception) {
        stream.toByteArray()
    } finally {
        runCatching { stream.close() }
    }
}

/**
 * 批量把 content:// URI 列表转换为 data URI 列表。
 * 忽略失败项。
 */
suspend fun List<String>.toDataUriList(context: Context, maxKb: Int = 1024): List<String> {
    val result = mutableListOf<String>()
    for (raw in this) {
        val uri = try { Uri.parse(raw) } catch (_: Exception) { null } ?: continue
        uri.contentUriToDataUri(context, maxKb)?.let { result.add(it) }
    }
    return result
}

/**
 * 在应用私有 cache 目录下创建一个新的 JPEG 临时文件，并通过 FileProvider 返回
 * content:// URI。用于给 TakePicture contract 作为拍照输出目标。
 *
 *   · 目录：cacheDir/camera/IMG_{timestamp}.jpg
 *   · authority："${context.packageName}.fileprovider"（与 AndroidManifest.xml 中 provider
 *     的 android:authorities 字段保持一致；引用由构建工具替换 applicationId）
 *   · 权限：调用方必须通过 Intent.addFlags(FLAG_GRANT_WRITE_URI_PERMISSION) 授权给相机
 *
 * 返回的 File 对象建议保存在 ViewModel 中，发送成功后由 clearCameraTempFiles 回收。
 */
fun createImageFileUri(context: Context): Pair<Uri, File> {
    val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
    val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return uri to file
}

/**
 * 清空 cacheDir/camera 下所有临时拍照文件。
 *   · 应用启动时调用一次 — 兜底清除上次没清理的文件
 *   · 拍照成功并入库后调用一次 — 回收刚才的临时文件
 * 操作发生在 IO 线程，不会阻塞主线程。
 */
suspend fun clearCameraTempFiles(context: Context) = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "camera")
    if (!dir.exists() || !dir.isDirectory) return@withContext
    runCatching {
        dir.listFiles()?.forEach { it.delete() }
    }
}
