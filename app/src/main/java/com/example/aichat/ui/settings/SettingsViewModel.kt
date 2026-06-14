package com.example.aichat.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsVM"
    }

    private val _theme = MutableStateFlow("light")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _language = MutableStateFlow("zh")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _fontSize = MutableStateFlow("medium")
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()

    private val _defaultModel = MutableStateFlow("deepseek-chat")
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    private val _temperature = MutableStateFlow("1.0")
    val temperature: StateFlow<String> = _temperature.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    init {
        viewModelScope.launch {
            launch { settingsRepository.getTheme().collect { _theme.value = it } }
            launch { settingsRepository.getLanguage().collect { _language.value = it } }
            launch { settingsRepository.getFontSize().collect { _fontSize.value = it } }
            launch { settingsRepository.getDefaultModel().collect { _defaultModel.value = it } }
            launch { settingsRepository.getTemperature().collect { _temperature.value = it } }
            launch { settingsRepository.getSystemPrompt().collect { _systemPrompt.value = it } }
            launch { settingsRepository.getBaseUrl().collect { _baseUrl.value = it } }
            launch { settingsRepository.getSelectedModelIds().collect { _selectedModelIds.value = it } }
            launch { _apiKey.value = settingsRepository.getApiKey() }
        }
    }

    fun setTheme(value: String) = viewModelScope.launch { settingsRepository.setTheme(value) }
    fun setLanguage(value: String) = viewModelScope.launch { settingsRepository.setLanguage(value) }
    fun setFontSize(value: String) = viewModelScope.launch { settingsRepository.setFontSize(value) }
    fun setDefaultModel(value: String) = viewModelScope.launch { settingsRepository.setDefaultModel(value) }
    fun setTemperature(value: String) = viewModelScope.launch { settingsRepository.setTemperature(value) }
    fun setSystemPrompt(value: String) = viewModelScope.launch { settingsRepository.setSystemPrompt(value) }
    fun setBaseUrl(value: String) = viewModelScope.launch { settingsRepository.setBaseUrl(value) }
    fun setApiKey(value: String) = viewModelScope.launch { settingsRepository.setApiKey(value) }
    fun setSelectedModelIds(ids: List<String>) = viewModelScope.launch { settingsRepository.setSelectedModelIds(ids) }
    fun getApiKeyNow(): String = settingsRepository.getApiKey()

    fun clearAllConversations() = viewModelScope.launch {
        chatRepository.deleteAllConversations()
        Log.d(TAG, "All conversations cleared")
    }

    suspend fun getConversationCount(): Int {
        val count = chatRepository.getConversationCount()
        Log.d(TAG, "Conversation count: $count")
        return count
    }

    suspend fun getMessageCount(): Int {
        val count = chatRepository.getMessageCount()
        Log.d(TAG, "Message count: $count")
        return count
    }
}
