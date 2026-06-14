package com.example.aichat.data.security

import android.content.Context
import dev.spght.encryptedprefs.EncryptedSharedPreferences
import dev.spght.encryptedprefs.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // 延迟初始化：避免 MasterKey/EncryptedSharedPreferences 在构造时阻塞主线程
    // 首次访问 getApiKey/saveApiKey 时才执行 Keystore 初始化（~200ms）
    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(key: String) {
        sharedPreferences.edit().putString("api_key", key).apply()
    }

    fun getApiKey(): String {
        return sharedPreferences.getString("api_key", "") ?: ""
    }

    fun clearApiKey() {
        sharedPreferences.edit().remove("api_key").apply()
    }
}
