package com.example.aichat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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

    // 用户从提供商模型列表里勾选的模型 id 集合（有序）
    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getTheme().collect { _theme.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getLanguage().collect { _language.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getFontSize().collect { _fontSize.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getDefaultModel().collect { _defaultModel.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getTemperature().collect { _temperature.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getSystemPrompt().collect { _systemPrompt.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getBaseUrl().collect { _baseUrl.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getSelectedModelIds().collect { _selectedModelIds.value = it }
        }
        _apiKey.value = settingsRepository.getApiKey()
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
    // 为非挂起调用提供同步快照（ModelsRepository / UI 需要时）
    fun getApiKeyNow(): String = settingsRepository.getApiKey()
}
