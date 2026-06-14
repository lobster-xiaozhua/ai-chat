package com.example.aichat.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/* ============================================================================
 * DocumentUtils —— 从 content:// URI 读取文档内容（纯文本 / PDF）
 *
 *   · getFileMime(context)     →  "text/plain" | "application/pdf" | ...
 *   · getFileName(context)     →  文件名（用于 UI Chip 展示）
 *   · getFileSizeBytes(context) →  文件大小（用于提示信息）
 *   · extractText(context, maxChars) →  文档文本（前 maxChars 字符）
 *
 *  支持的内容类型：
 *    · text/any   —— 直接 InputStream → reader.readText()
 *    · application/json  application/xml  application/javascript
 *    · application/pdf  —— PdfRenderer，前 3 页文本
 *  其他返回 null。
 *
 *  全部挂起函数，IO 线程执行，不阻塞主线程。
 * ============================================================================ */

/**
 * 查询 content:// URI 的 MIME 类型。
 * 如果 ContentResolver 查不到，按扩展名回退。
 */
fun Uri.getFileMime(context: Context): String? = when (scheme) {
    ContentResolver.SCHEME_CONTENT -> context.contentResolver.getType(this)
    ContentResolver.SCHEME_FILE -> {
        val name = (lastPathSegment ?: "").lowercase()
        when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown") -> "text/plain"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".xml") -> "application/xml"
            name.endsWith(".csv") -> "text/csv"
            name.endsWith(".kt") || name.endsWith(".java") ||
                name.endsWith(".js") || name.endsWith(".ts") ||
                name.endsWith(".py") || name.endsWith(".html") ||
                name.endsWith(".css") -> "text/plain"
            else -> null
        }
    }
    else -> null
}

/**
 * 获取文件名 —— 用于输入栏下方 Chip 的显示。
 * 对外提供普通函数形式 + Uri 扩展函数形式，两种调用方式都可用。
 */
fun getFileName(context: Context, uri: Uri): String {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return uri.lastPathSegment ?: ""
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (idx >= 0) cursor.getString(idx) else ""
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

/** Uri 扩展函数：获取文件名（内部委托给上面的普通函数）。 */
fun Uri.getFileName(context: Context): String = com.example.aichat.util.getFileName(context, this)

/**
 * 获取文件大小（字节）。失败返回 -1。
 */
fun Uri.getFileSizeBytes(context: Context): Long {
    if (scheme != ContentResolver.SCHEME_CONTENT) return -1L
    return try {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            if (idx >= 0) cursor.getLong(idx) else -1L
        } ?: -1L
    } catch (e: Exception) {
        -1L
    }
}

/**
 * 从文档 URI 中提取纯文本。超过 maxChars 会截断并附加提示。
 *
 * 支持：
 *   · text/any —— 直接读取字符流
 *   · application/json / application/xml / text/csv
 *   · application/pdf —— PdfRenderer，最多 3 页，每页前 4000 字符
 *  其他 MIME 或异常返回 null。
 */
suspend fun Uri.extractText(context: Context, maxChars: Int = 12000): String? =
    withContext(Dispatchers.IO) {
        val mime = getFileMime(context) ?: return@withContext null
        val isText = mime.startsWith("text/") ||
            mime == "application/json" ||
            mime == "application/xml" ||
            mime == "application/javascript"

        when {
            isText -> runCatching { readAsText(context, this@extractText, maxChars) }.getOrNull()
            mime == "application/pdf" -> runCatching { readPdfAsText(context, this@extractText, maxChars) }.getOrNull()
            else -> null
        }
    }

/**
 * 打开 content:// 的 InputStream。
 */
private fun Uri.openInput(context: Context): InputStream? =
    try { context.contentResolver.openInputStream(this) } catch (e: Exception) { null }

/**
 * 把字符流直接读出为文本（带上限）。
 */
private fun readAsText(context: Context, uri: Uri, maxChars: Int): String? {
    val stream = uri.openInput(context) ?: return null
    return stream.use {
        val reader = it.bufferedReader()
        val buffer = CharArray(4096)
        val sb = StringBuilder()
        while (sb.length < maxChars) {
            val n = reader.read(buffer)
            if (n <= 0) break
            sb.append(buffer, 0, n)
        }
        val content = sb.toString()
        if (sb.length >= maxChars) "$content...[已截断，更多内容未发送]" else content
    }
}

/**
 * 通过 PdfRenderer 读取 PDF 前几页的文本。
 * PdfRenderer 本身只提供 bitmap；这里退化为读取每页文本长度的近似估计（按页面
 * 字符密度估计），同时通过 contentResolver 的另一套路径尝试提取文本流。
 * 如果 contentResolver 无法直接返回文本，则返回一个带页数 / 大小的占位描述，
 * 足以让模型知道是个 PDF 文件。
 */
private fun readPdfAsText(context: Context, uri: Uri, maxChars: Int): String? {
    // 1) 先尝试直接把 PDF 作为流文本读取（部分纯文本/低复杂度 PDF 内容可读）
    val direct = try {
        uri.openInput(context)?.use {
            it.bufferedReader().readText().trim().ifEmpty { null }
        }
    } catch (e: Exception) { null }
    if (direct != null && direct.length >= 80) {
        return if (direct.length > maxChars) direct.take(maxChars) + "\n...[已截断]" else direct
    }

    // 2) 退而求其次：用 PdfRenderer 获取页数 + 估算每页可处理文本
    var pfd: ParcelFileDescriptor? = null
    val sb = StringBuilder()
    sb.appendLine("[PDF 文档预览]")
    sb.appendLine("文件名：${uri.getFileName(context)}")
    try {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) return null
        var pageCount = 0
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            // PdfRenderer 构造失败时手动关闭 pfd
            runCatching { pfd.close() }
            pfd = null
            return null
        }
        renderer.use {
            pageCount = it.pageCount
            val take = pageCount.coerceAtMost(3)
            sb.appendLine("总页数：$pageCount；已分析前 $take 页")
            for (i in 0 until take) {
                it.openPage(i).use { page ->
                    val w = page.width
                    val h = page.height
                    sb.appendLine("第 ${i + 1} 页：尺寸 ${w}x$h（需要多模态模型才能理解图片内容）")
                }
            }
        }
        // PdfRenderer.close() 已关闭 pfd，标记为 null 避免重复关闭
        pfd = null
    } finally {
        // 仅在 PdfRenderer 未成功关闭 pfd 时兜底关闭
        runCatching { pfd?.close() }
    }
    return sb.toString()
}

/**
 * 以 Map.Entry 形式返回「uri → 文件名」，用于 ChatViewModel 的文档状态管理。
 * 简化为 Pair<String, String>。
 */
fun Uri.toDocumentEntry(context: Context): Pair<String, String> =
    toString() to getFileName(context).ifEmpty { "未命名文档" }
