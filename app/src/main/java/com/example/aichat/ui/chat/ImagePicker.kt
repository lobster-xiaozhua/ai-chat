package com.example.aichat.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.aichat.util.createImageFileUri

/* ============================================================================
 * ImagePicker —— 封装 Android 官方 Photo Picker + 系统相机
 *
 *   · pickImages(maxItems)  →  从相册选图
 *   · pickSingleImage()     →  单张图
 *   · pickFromCamera()      →  打开系统相机拍照
 *
 *  所有选择结果都通过 onPicked 回调返回 content:// URI 字符串列表。
 * ========================================================================== */

interface ImagePickerLauncher {
    fun pickImages(maxItems: Int = 3)
    fun pickSingleImage()
    fun pickFromCamera()
}

@Composable
fun rememberImagePicker(onPicked: (List<String>) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current

    // —— 多选：PickMultipleVisualMedia（Android 13+ 原生）
    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPicked(uris.map { it.toString() })
            }
        }
    )

    // —— 单选：PickVisualMedia（部分机型更稳定）
    val singlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { onPicked(listOf(it.toString())) }
        }
    )

    // —— 兼容层：GetContent（老系统上 PhotoPicker 不可用时的 fallback）
    val legacyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPicked(uris.map { it.toString() })
            }
        }
    )

    // —— 拍照：TakePicture，返回 Boolean 表示是否成功；成功后使用预先创建的 URI
    val cameraOutput = remember { mutableMapOf<String, Uri>() }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        // 找到最后一个加入的 URI 作为拍照结果
        val last = cameraOutput.keys.lastOrNull() ?: return@rememberLauncherForActivityResult
        val uri = cameraOutput.remove(last) ?: return@rememberLauncherForActivityResult
        if (success) {
            // 拍照成功：授予后续读取权限（重启 app 也可读）
            tryGrantPersistableRead(context, uri)
            onPicked(listOf(uri.toString()))
        }
    }

    return object : ImagePickerLauncher {
        override fun pickImages(maxItems: Int) {
            try {
                multiPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            } catch (_: Exception) {
                legacyPicker.launch("image/*")
            }
        }

        override fun pickSingleImage() {
            try {
                singlePicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            } catch (_: Exception) {
                legacyPicker.launch("image/*")
            }
        }

        override fun pickFromCamera() {
            // 在 cacheDir/camera/ 下创建新文件，包成 FileProvider URI，
            // 写入 launch map 以便 onResult 时取回。
            try {
                val outputUri = createImageFileUri(context)
                val key = "shot_${System.currentTimeMillis()}"
                cameraOutput[key] = outputUri
                // TakePicture 需要 FLAG_GRANT_WRITE_URI_PERMISSION 授权给相机应用
                // （这里 contract 内部会处理；若失败，走异常回退用 GetContent 选图）
                cameraLauncher.launch(outputUri)
            } catch (e: Exception) {
                // 若设备上无可用相机应用，降级为从相册选单张
                try { singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { legacyPicker.launch("image/*") }
            }
        }
    }
}

/**
 * 对 content:// URI 申请持久读取权限 —— 使得 app 重启后仍能从该 URI 读取内容。
 * 若系统不支持（部分 ROM），降级忽略，不阻塞用户发送。
 */
private fun tryGrantPersistableRead(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
        // 不支持持久权限 → 忽略；当前 session 内可读即可
    }
}

/* ============================================================================
 * DocumentPicker —— 文档选择（OpenMultipleDocuments）
 *
 *   · pickDocuments()     →  多选文档（text / pdf / json …）
 *   · pickSingleDocument()  →  单选文档
 *
 *  返回：List<Pair<String, String>> = (uri 字符串 → 显示用文件名)
 * ========================================================================== */

interface DocumentPickerLauncher {
    fun pickDocuments()
    fun pickSingleDocument()
}

@Composable
fun rememberDocumentPicker(onPicked: (List<Pair<String, String>>) -> Unit): DocumentPickerLauncher {
    val context = LocalContext.current

    val multiDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { tryGrantPersistableRead(context, it) }
            onPicked(uris.map { it.toString() to com.example.aichat.util.getFileName(context, it) })
        }
    }

    val singleDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            tryGrantPersistableRead(context, it)
            onPicked(listOf(it.toString() to com.example.aichat.util.getFileName(context, it)))
        }
    }

    return object : DocumentPickerLauncher {
        override fun pickDocuments() {
            try {
                // 声明支持的 MIME 类型（text/* + application/pdf + application/json）
                multiDocLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/pdf",
                        "application/json",
                        "application/xml",
                        "application/javascript"
                    )
                )
            } catch (_: Exception) {
                // 部分旧 ROM 不支持 OpenMultipleDocuments → 退化为单选
                singleDocLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/pdf",
                        "application/json"
                    )
                )
            }
        }

        override fun pickSingleDocument() {
            singleDocLauncher.launch(arrayOf("text/*", "application/pdf", "application/json"))
        }
    }
}
