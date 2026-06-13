package com.example.aichat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val TEMPERATURE = stringPreferencesKey("temperature")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL_NAME = stringPreferencesKey("model_name")
        val SELECTED_MODEL_IDS = stringPreferencesKey("selected_model_ids") // 已选模型 id 列表，以 | 分隔
    }

    suspend fun setTheme(theme: String) { context.dataStore.edit { it[Keys.THEME] = theme } }
    fun getTheme(): Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "light" }

    suspend fun setLanguage(lang: String) { context.dataStore.edit { it[Keys.LANGUAGE] = lang } }
    fun getLanguage(): Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "zh" }

    suspend fun setFontSize(size: String) { context.dataStore.edit { it[Keys.FONT_SIZE] = size } }
    fun getFontSize(): Flow<String> = context.dataStore.data.map { it[Keys.FONT_SIZE] ?: "medium" }

    suspend fun setDefaultModel(model: String) { context.dataStore.edit { it[Keys.DEFAULT_MODEL] = model } }
    fun getDefaultModel(): Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_MODEL] ?: "deepseek-chat" }

    suspend fun setTemperature(temp: String) { context.dataStore.edit { it[Keys.TEMPERATURE] = temp } }
    fun getTemperature(): Flow<String> = context.dataStore.data.map { it[Keys.TEMPERATURE] ?: "1.0" }

    suspend fun setSystemPrompt(prompt: String) { context.dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt } }
    fun getSystemPrompt(): Flow<String> = context.dataStore.data.map { it[Keys.SYSTEM_PROMPT] ?: "" }

    suspend fun setBaseUrl(url: String) { context.dataStore.edit { it[Keys.BASE_URL] = url } }
    fun getBaseUrl(): Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: "https://api.deepseek.com" }

    suspend fun setModelName(name: String) { context.dataStore.edit { it[Keys.MODEL_NAME] = name } }
    fun getModelName(): Flow<String> = context.dataStore.data.map { it[Keys.MODEL_NAME] ?: "deepseek-chat" }

    // 已选模型列表：持久化为以 | 分隔的字符串，便于 JSON-free 存储
    suspend fun setSelectedModelIds(ids: List<String>) {
        context.dataStore.edit { it[Keys.SELECTED_MODEL_IDS] = ids.joinToString("|") }
    }
    fun getSelectedModelIds(): Flow<List<String>> = context.dataStore.data.map {
        val raw = it[Keys.SELECTED_MODEL_IDS]
        if (raw.isNullOrBlank()) emptyList() else raw.split("|").filter(String::isNotBlank)
    }
}
