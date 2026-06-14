package com.example.aichat

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.aichat.util.clearCameraTempFiles
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AiChatApp : Application() {

    // 应用级别的 CoroutineScope — 生命周期与 app 同步，用于非 UI 后台任务
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 启动时输出一行版本/环境信息 —— 便于 adb logcat 快速定位测试包
        Log.d("AiChat", "onCreate: appId=$packageName, ver=$versionName(${versionCode}), sdk=${Build.VERSION.SDK_INT}, device=${Build.MODEL}")
        // 清理上次残留的拍照临时文件 —— 不阻塞 UI，失败也静默忽略
        appScope.launch { clearCameraTempFiles(this@AiChatApp) }
    }

    private val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0"

    private val versionCode: Int
        get() = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }.getOrNull() ?: 1
}
