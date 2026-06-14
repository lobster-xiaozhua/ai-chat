package com.example.aichat.data.repository

import com.example.aichat.data.local.preferences.SettingsDataStore
import com.example.aichat.data.security.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val secureKeyStore: SecureKeyStore
) {

    // Theme
    suspend fun setTheme(theme: String) = dataStore.setTheme(theme)
    fun getTheme(): Flow<String> = dataStore.getTheme()

    // Language
    suspend fun setLanguage(lang: String) = dataStore.setLanguage(lang)
    fun getLanguage(): Flow<String> = dataStore.getLanguage()

    // Font Size
    suspend fun setFontSize(size: String) = dataStore.setFontSize(size)
    fun getFontSize(): Flow<String> = dataStore.getFontSize()

    // Default Model
    suspend fun setDefaultModel(model: String) = dataStore.setDefaultModel(model)
    fun getDefaultModel(): Flow<String> = dataStore.getDefaultModel()

    // Temperature
    suspend fun setTemperature(temp: String) = dataStore.setTemperature(temp)
    fun getTemperature(): Flow<String> = dataStore.getTemperature()

    // System Prompt
    suspend fun setSystemPrompt(prompt: String) = dataStore.setSystemPrompt(prompt)
    fun getSystemPrompt(): Flow<String> = dataStore.getSystemPrompt()

    // Base URL
    suspend fun setBaseUrl(url: String) = dataStore.setBaseUrl(url)
    fun getBaseUrl(): Flow<String> = dataStore.getBaseUrl()

    // Model Name
    suspend fun setModelName(name: String) = dataStore.setModelName(name)
    fun getModelName(): Flow<String> = dataStore.getModelName()

    // Selected Models（用户从提供商列表里勾选的模型）
    suspend fun setSelectedModelIds(ids: List<String>) = dataStore.setSelectedModelIds(ids)
    fun getSelectedModelIds(): Flow<List<String>> = dataStore.getSelectedModelIds()

    // API Key (加密存储)
    // 注意：SecureKeyStore 基于 SharedPreferences，setApiKey 使用 apply() 异步写入，
    // 无法通过 Flow 观察变更；如需实时感知需改用 DataStore 或 EventBus。
    suspend fun setApiKey(key: String) { secureKeyStore.saveApiKey(key) }
    fun getApiKey(): String = secureKeyStore.getApiKey()
    fun clearApiKey() = secureKeyStore.clearApiKey()
}
