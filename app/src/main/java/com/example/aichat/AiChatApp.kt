package com.example.aichat

import android.app.Application
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 启动时输出一行版本/环境信息 —— 便于 adb logcat 快速定位测试包
        Log.d("AiChat", "onCreate: appId=$packageName, ver=$versionName(${versionCode}), sdk=${Build.VERSION.SDK_INT}, device=${Build.MODEL}")
    }

    private val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0"

    private val versionCode: Int
        get() = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }.getOrNull() ?: 1
}
