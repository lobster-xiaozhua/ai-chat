package com.example.aichat.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.model.Message
import com.example.aichat.data.repository.AiRepository
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.SettingsRepository
import com.example.aichat.util.extractText
import com.example.aichat.util.toDocumentEntry
import com.example.aichat.util.toDataUriList
import com.example.aichat.util.getFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    /* ---------- 状态 ---------- */
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _streamingAssistant = MutableStateFlow<String?>(null)
    val streamingAssistant: StateFlow<String?> = _streamingAssistant.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentModel = MutableStateFlow("deepseek-chat")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _jsonMode = MutableStateFlow(false)
    val jsonMode: StateFlow<Boolean> = _jsonMode.asStateFlow()

    private val _thinkMode = MutableStateFlow(false)
    val thinkMode: StateFlow<Boolean> = _thinkMode.asStateFlow()

    private val _searchMode = MutableStateFlow(false)
    val searchMode: StateFlow<Boolean> = _searchMode.asStateFlow()

    private val _pendingImageUrls = androidx.compose.runtime.mutableStateListOf<String>()
    val pendingImageUrls: androidx.compose.runtime.snapshots.SnapshotStateList<String> = _pendingImageUrls

    private val _pendingDocumentUrls = androidx.compose.runtime.mutableStateListOf<String>()
    val pendingDocumentUrls: androidx.compose.runtime.snapshots.SnapshotStateList<String> = _pendingDocumentUrls
    private val _pendingDocumentNames = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val pendingDocumentNames: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> = _pendingDocumentNames

    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    private val _temperatureStr = MutableStateFlow("1.0")
    private val _systemPrompt = MutableStateFlow("")

    private var generationJob: Job? = null
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()
    private var historyForApi = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    // 草稿管理：conversationId → draft text
    private val drafts = mutableMapOf<String, String>()

    // 生成失败标记（用于显示重新生成按钮）
    private val _lastGenerationFailed = MutableStateFlow(false)
    val lastGenerationFailed: StateFlow<Boolean> = _lastGenerationFailed.asStateFlow()

    /* ---------- 初始化：从 DataStore 异步加载设置 ---------- */
    init {
        viewModelScope.launch {
            launch { settingsRepository.getBaseUrl().collect { if (it.isNotBlank()) _baseUrl.value = it } }
            launch { settingsRepository.getTemperature().collect { if (it.isNotBlank()) _temperatureStr.value = it } }
            launch { settingsRepository.getSystemPrompt().collect { _systemPrompt.value = it } }
            launch { settingsRepository.getDefaultModel().collect { if (it.isNotBlank() && !_isGenerating.value) _currentModel.value = it } }
            launch { settingsRepository.getSelectedModelIds().collect { _selectedModelIds.value = it } }
        }
    }

    /* ---------- 对外操作：JSON 模式 ---------- */
    fun setJsonMode(on: Boolean) { _jsonMode.value = on }
    fun toggleJsonMode() { _jsonMode.value = !_jsonMode.value }

    /* ---------- 对外操作：Think / Search 模式 ---------- */
    fun toggleThink() { _thinkMode.value = !_thinkMode.value }
    fun toggleSearch() { _searchMode.value = !_searchMode.value }

    /* ---------- 对外操作：图片附件 ---------- */
    fun addImageUrls(urls: List<String>) {
        for (url in urls) {
            if (url !in _pendingImageUrls) _pendingImageUrls.add(url)
        }
    }

    fun removeImageUrl(url: String) { _pendingImageUrls.remove(url) }
    fun clearPendingImages() { _pendingImageUrls.clear() }

    /* ---------- 对外操作：文档附件 ---------- */
    fun addDocumentUrls(urisWithNames: List<Pair<String, String>>) {
        for ((uri, name) in urisWithNames) {
            if (uri !in _pendingDocumentUrls) {
                _pendingDocumentUrls.add(uri)
                _pendingDocumentNames[uri] = name.ifEmpty { "未命名文档" }
            }
        }
    }

    fun removeDocumentUrl(uri: String) {
        _pendingDocumentUrls.remove(uri)
        _pendingDocumentNames.remove(uri)
    }

    fun clearDocuments() {
        _pendingDocumentUrls.clear()
        _pendingDocumentNames.clear()
    }

    /* ---------- 草稿管理 ---------- */
    fun saveDraft(conversationId: String, text: String) {
        if (text.isBlank()) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = text
        }
    }

    fun restoreDraft(conversationId: String): String {
        return drafts.remove(conversationId) ?: ""
    }

    fun clearDraft(conversationId: String) {
        drafts.remove(conversationId)
    }

    /* ---------- 对外操作：会话与消息 ---------- */

    fun setConversation(conversationId: String) {
        // 保存当前会话草稿
        _currentConversationId.value?.let { currentId ->
            // 草稿由 ChatScreen 层在切换前保存
        }

        if (_currentConversationId.value == conversationId && _messages.value.isNotEmpty()) return
        generationJob?.cancel()
        generationJob = null
        _isGenerating.value = false
        _streamingAssistant.value = null
        _error.value = null
        _lastGenerationFailed.value = false
        clearPendingImages()
        clearDocuments()

        viewModelScope.launch {
            val history = chatRepository.getMessagesAsList(conversationId)
            _messages.value = history
            _currentConversationId.value = conversationId
            historyForApi = java.util.Collections.synchronizedList(
                history
                    .filter { it.role == "user" || it.role == "assistant" }
                    .map { it.role to it.content }
                    .toMutableList()
            )
            Log.d(TAG, "Loaded conversation $conversationId with ${history.size} messages")
        }
    }

    fun sendMessage(text: String) {
        val images = _pendingImageUrls.toList()
        val documents = _pendingDocumentUrls.toList()
        val docNames = _pendingDocumentNames.toMap()
        val convId = _currentConversationId.value ?: return
        if (text.isBlank() && images.isEmpty() && documents.isEmpty()) return

        _pendingImageUrls.clear()
        _pendingDocumentUrls.clear()
        _pendingDocumentNames.clear()
        // 清除草稿
        clearDraft(convId)
        _lastGenerationFailed.value = false

        val now = System.currentTimeMillis()
        val initialTextForHistory = text
        val userMsg = Message(
            conversationId = convId,
            role = "user",
            content = initialTextForHistory,
            imageUrls = Message.encodeImageUrls(images),
            timestamp = now
        )

        _messages.value = _messages.value + userMsg
        historyForApi.add("user" to initialTextForHistory)
        viewModelScope.launch { runCatching { chatRepository.insertMessage(userMsg) } }

        _isGenerating.value = true
        _error.value = null
        _streamingAssistant.value = ""

        generationJob = viewModelScope.launch {
            val builder = StringBuilder(2048)
            var lastEmitNs = 0L
            val throttleNs = 50_000_000L
            var tokensSinceEmit = 0

            try {
                val apiKey = settingsRepository.getApiKey()
                if (apiKey.isBlank()) throw com.example.aichat.data.repository.ApiException.Unauthorized("请先在设置中配置 API Key")

                val temperature = _temperatureStr.value.toDoubleOrNull() ?: 1.0
                val dataUriList = if (images.isNotEmpty()) images.toDataUriList(context, maxKb = 512) else emptyList()

                val docParts = mutableListOf<String>()
                for (uriString in documents) {
                    val uri = try { android.net.Uri.parse(uriString) } catch (_: Exception) { null } ?: continue
                    val extracted = uri.extractText(context)
                    val displayName = docNames[uriString] ?: uri.getFileName(context)
                    if (extracted != null) {
                        docParts.add("[文档：$displayName]\n---\n$extracted\n---\n")
                    } else {
                        docParts.add("[文档：$displayName（内容无法自动读取，请用户提供文本）]\n")
                    }
                }
                val combinedUserText = if (docParts.isNotEmpty()) {
                    (docParts.joinToString("") + "\n" + text).trim()
                } else text

                if (docParts.isNotEmpty()) {
                    val lastIdx = historyForApi.indexOfLast { it.first == "user" }
                    if (lastIdx >= 0) {
                        val newHistory = historyForApi.toMutableList()
                        newHistory[lastIdx] = "user" to combinedUserText
                        historyForApi = java.util.Collections.synchronizedList(newHistory)
                    }
                }

                aiRepository.streamChat(
                    messages = historyForApi,
                    systemPrompt = _systemPrompt.value,
                    baseUrl = _baseUrl.value,
                    model = _currentModel.value,
                    apiKey = apiKey,
                    temperature = temperature,
                    jsonMode = _jsonMode.value,
                    userImageUrls = dataUriList,
                    thinkMode = _thinkMode.value,
                    searchMode = _searchMode.value
                ).collect { token ->
                    builder.append(token)
                    tokensSinceEmit++
                    val now2 = System.nanoTime()
                    if (now2 - lastEmitNs > throttleNs || tokensSinceEmit >= 3) {
                        _streamingAssistant.value = builder.toString()
                        lastEmitNs = now2
                        tokensSinceEmit = 0
                    }
                }

                val finalContent = builder.toString()
                if (finalContent.isNotEmpty()) {
                    _streamingAssistant.value = finalContent
                    val finalMsg = Message(
                        conversationId = convId,
                        role = "assistant",
                        content = finalContent,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + finalMsg
                    historyForApi.add("assistant" to finalContent)
                    viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
                }
            } catch (e: com.example.aichat.data.repository.ApiException) {
                _error.value = e.message ?: "发生错误"
                _lastGenerationFailed.value = true
                historyForApi.removeLastOrNull()
                restorePendingAttachments(images, documents, docNames)
                Log.w(TAG, "API error: ${e.message}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                val partial = builder.toString()
                if (partial.isNotEmpty()) {
                    val finalMsg = Message(
                        conversationId = convId,
                        role = "assistant",
                        content = partial,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + finalMsg
                    historyForApi.add("assistant" to partial)
                    viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
                } else {
                    historyForApi.removeLastOrNull()
                    restorePendingAttachments(images, documents, docNames)
                }
                throw e
            } catch (e: Exception) {
                _error.value = "消息生成失败：${e.message}"
                _lastGenerationFailed.value = true
                historyForApi.removeLastOrNull()
                restorePendingAttachments(images, documents, docNames)
                Log.w(TAG, "Generation failed: ${e.message}")
            } finally {
                _streamingAssistant.value = null
                _isGenerating.value = false
            }
        }
    }

    private fun restorePendingAttachments(
        images: List<String>,
        documents: List<String>,
        docNames: Map<String, String>
    ) {
        for (url in images) {
            if (url !in _pendingImageUrls) _pendingImageUrls.add(url)
        }
        for (uri in documents) {
            if (uri !in _pendingDocumentUrls) {
                _pendingDocumentUrls.add(uri)
                docNames[uri]?.let { _pendingDocumentNames[uri] = it }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) {
        _currentModel.value = model
        viewModelScope.launch { settingsRepository.setDefaultModel(model) }
    }

    /* ---------- 消息长按操作 ---------- */

    /** 删除指定消息 */
    fun deleteMessage(messageId: Long) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: run {
            Log.w(TAG, "deleteMessage: message $messageId not found")
            return
        }
        // 先计算 historyForApi 中的索引（在移除前）
        val historyIdx = _messages.value
            .filter { it.role == "user" || it.role == "assistant" }
            .indexOfFirst { it.id == messageId }

        _messages.value = _messages.value.filter { it.id != messageId }

        if (historyIdx >= 0 && historyIdx < historyForApi.size) {
            historyForApi = java.util.Collections.synchronizedList(
                historyForApi.toMutableList().also { it.removeAt(historyIdx) }
            )
        }
        viewModelScope.launch {
            runCatching { chatRepository.deleteMessageById(messageId) }
                .onFailure { Log.w(TAG, "Failed to delete message $messageId: ${it.message}") }
                .onSuccess { Log.d(TAG, "Deleted message $messageId") }
        }
    }

    /** 重新生成最后一条助手消息 */
    fun regenerateLastAssistant() {
        val lastAssistant = _messages.value.lastOrNull { it.role == "assistant" } ?: run {
            Log.w(TAG, "regenerateLastAssistant: no assistant message found")
            return
        }
        // 移除最后的 assistant 消息
        _messages.value = _messages.value.filter { it.id != lastAssistant.id }
        historyForApi = java.util.Collections.synchronizedList(
            historyForApi.toMutableList().also { list ->
                val lastIdx = list.indexOfLast { it.first == "assistant" && it.second == lastAssistant.content }
                if (lastIdx >= 0) list.removeAt(lastIdx)
            }
        )
        viewModelScope.launch {
            runCatching { chatRepository.deleteMessageById(lastAssistant.id) }
        }

        // 找到最后一条用户消息重新发送
        val lastUserMsg = _messages.value.lastOrNull { it.role == "user" }
        if (lastUserMsg != null) {
            Log.d(TAG, "Regenerating from user message: '${lastUserMsg.content.take(50)}...'")
            // 从 historyForApi 移除最后的 user 消息，sendMessage 会重新添加
            historyForApi = java.util.Collections.synchronizedList(
                historyForApi.toMutableList().also { list ->
                    val lastIdx = list.indexOfLast { it.first == "user" && it.second == lastUserMsg.content }
                    if (lastIdx >= 0) list.removeAt(lastIdx)
                }
            )
            _messages.value = _messages.value.filter { it.id != lastUserMsg.id }
            sendMessage(lastUserMsg.content)
        } else {
            Log.w(TAG, "regenerateLastAssistant: no user message to regenerate from")
            _error.value = "没有可重新生成的用户消息"
        }
    }

    /* ---------- 数据统计 ---------- */

    suspend fun getConversationCount(): Int = chatRepository.getConversationCount()
    suspend fun getMessageCount(): Int = chatRepository.getMessageCount()
}
