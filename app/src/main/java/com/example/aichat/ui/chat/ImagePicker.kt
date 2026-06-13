package com.example.aichat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/* ============================================================================
 * ImagePicker —— 封装 Android 官方 Photo Picker，兼容 Android 11+
 *
 * 使用方法：
 *   val picker = rememberImagePicker(onPicked = { uris ->
 *       // uris: List<String> —— content:// URI 的字符串形式
 *   })
 *   Button(onClick = { picker.pickImages(maxItems = 3) }) { ... }
 *
 * 底层：ActivityResultContracts.PickMultipleVisualMedia（Android 13+ 原生）
 *      · ActivityResultContracts.GetContent  （Android 11/12 兼容路径）
 * ========================================================================== */

interface ImagePickerLauncher {
    fun pickImages(maxItems: Int = 3)
    fun pickSingleImage()
}

@Composable
fun rememberImagePicker(onPicked: (List<String>) -> Unit): ImagePickerLauncher {

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

    return object : ImagePickerLauncher {
        override fun pickImages(maxItems: Int) {
            // 先尝试 PickMultipleVisualMedia；若系统不支持，launcher 内部会降级
            try {
                multiPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            } catch (_: Exception) {
                // 极端降级：GetContent
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
    }
}
