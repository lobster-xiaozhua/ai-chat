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
        // 清理上次残留的拍照临时文件 —— 不阻塞 UI，失败记录日志
        appScope.launch {
            runCatching { clearCameraTempFiles(this@AiChatApp) }
                .onFailure { Log.w("AiChat", "清理拍照临时文件失败", it) }
        }
    }

    private val versionName: String
        get() = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.onFailure { Log.w("AiChatApp", "获取版本信息失败", it) }.getOrNull() ?: "1.0"

    private val versionCode: Long
        get() = runCatching {
            // minSdk=29 ≥ API 28(P)，longVersionCode 始终可用
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        }.onFailure { Log.w("AiChatApp", "获取版本信息失败", it) }.getOrNull() ?: 1L
}
