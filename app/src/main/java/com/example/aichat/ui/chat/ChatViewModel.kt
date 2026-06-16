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
import kotlinx.coroutines.flow.first
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

    private val _pendingImageUrls = MutableStateFlow<List<String>>(emptyList())
    val pendingImageUrls: StateFlow<List<String>> = _pendingImageUrls.asStateFlow()

    private val _pendingDocumentUrls = MutableStateFlow<List<String>>(emptyList())
    val pendingDocumentUrls: StateFlow<List<String>> = _pendingDocumentUrls.asStateFlow()
    private val _pendingDocumentNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingDocumentNames: StateFlow<Map<String, String>> = _pendingDocumentNames.asStateFlow()

    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    private val _temperatureStr = MutableStateFlow("1.0")
    private val _systemPrompt = MutableStateFlow("")

    private var generationJob: Job? = null
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private fun List<Message>.toApiHistory(): List<Pair<String, String>> =
        filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }

    // 草稿管理：conversationId → draft text（ConcurrentHashMap 保证线程安全）
    private val drafts = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        _pendingImageUrls.value = (_pendingImageUrls.value + urls).distinct()
    }

    fun removeImageUrl(url: String) {
        _pendingImageUrls.value = _pendingImageUrls.value.filter { it != url }
    }
    fun clearPendingImages() {
        _pendingImageUrls.value = emptyList()
    }

    /* ---------- 对外操作：文档附件 ---------- */
    fun addDocumentUrls(urisWithNames: List<Pair<String, String>>) {
        val currentUrls = _pendingDocumentUrls.value
        val currentNames = _pendingDocumentNames.value.toMutableMap()
        val newUrls = currentUrls.toMutableList()
        for ((uri, name) in urisWithNames) {
            if (uri !in currentUrls) {
                newUrls.add(uri)
                currentNames[uri] = name.ifEmpty { "未命名文档" }
            }
        }
        _pendingDocumentUrls.value = newUrls
        _pendingDocumentNames.value = currentNames
    }

    fun removeDocumentUrl(uri: String) {
        _pendingDocumentUrls.value = _pendingDocumentUrls.value.filter { it != uri }
        val names = _pendingDocumentNames.value.toMutableMap()
        names.remove(uri)
        _pendingDocumentNames.value = names
    }

    fun clearDocuments() {
        _pendingDocumentUrls.value = emptyList()
        _pendingDocumentNames.value = emptyMap()
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
            Log.d(TAG, "Loaded conversation $conversationId with ${history.size} messages")
        }
    }

    fun sendMessage(text: String) {
        val images = _pendingImageUrls.value
        val documents = _pendingDocumentUrls.value
        val docNames = _pendingDocumentNames.value
        val convId = _currentConversationId.value ?: return
        if (text.isBlank() && images.isEmpty() && documents.isEmpty()) return

        _pendingImageUrls.value = emptyList()
        _pendingDocumentUrls.value = emptyList()
        _pendingDocumentNames.value = emptyMap()
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
        viewModelScope.launch {
            val insertedId = chatRepository.insertMessage(userMsg)
            if (insertedId > 0) {
                _messages.value = _messages.value.map {
                    if (it.timestamp == now && it.role == "user" && it.id == 0L) it.copy(id = insertedId) else it
                }
            }
        }

        val userMsgCount = _messages.value.count { it.role == "user" }
        if (userMsgCount == 1) {
            viewModelScope.launch {
                val title = text.take(20).replace("\n", " ")
                updateConversationTitle(convId, title)
            }
        }

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

                val apiHistory = _messages.value.toApiHistory().toMutableList()
                if (docParts.isNotEmpty()) {
                    val lastUserIdx = apiHistory.indexOfLast { it.first == "user" }
                    if (lastUserIdx >= 0) {
                        apiHistory[lastUserIdx] = "user" to combinedUserText
                    }
                }

                aiRepository.streamChat(
                    messages = apiHistory.toList(),
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
                    viewModelScope.launch {
                        val insertedId = chatRepository.insertMessage(finalMsg)
                        if (insertedId > 0) {
                            _messages.value = _messages.value.map {
                                if (it.timestamp == finalMsg.timestamp && it.role == "assistant" && it.id == 0L) it.copy(id = insertedId) else it
                            }
                        }
                    }
                }
            } catch (e: com.example.aichat.data.repository.ApiException) {
                _error.value = e.message ?: "发生错误"
                _lastGenerationFailed.value = true
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
                    viewModelScope.launch { runCatching { chatRepository.insertMessage(finalMsg) } }
                } else {
                    restorePendingAttachments(images, documents, docNames)
                }
                throw e
            } catch (e: Exception) {
                _error.value = "消息生成失败：${e.message}"
                _lastGenerationFailed.value = true
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
        _pendingImageUrls.value = (_pendingImageUrls.value + images).distinct()
        val currentUrls = _pendingDocumentUrls.value
        val currentNames = _pendingDocumentNames.value.toMutableMap()
        val newUrls = currentUrls.toMutableList()
        for (uri in documents) {
            if (uri !in currentUrls) {
                newUrls.add(uri)
                docNames[uri]?.let { currentNames[uri] = it }
            }
        }
        _pendingDocumentUrls.value = newUrls
        _pendingDocumentNames.value = currentNames
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
        val currentMessages = _messages.value
        val target = currentMessages.firstOrNull { it.id == messageId }
        if (target == null) {
            Log.w(TAG, "deleteMessage: message $messageId not found")
            return
        }
        _messages.value = currentMessages.filter { it.id != messageId }
        viewModelScope.launch {
            runCatching { chatRepository.deleteMessageById(messageId) }
                .onFailure { Log.w(TAG, "Failed to delete message $messageId: ${it.message}") }
                .onSuccess { Log.d(TAG, "Deleted message $messageId") }
        }
    }

    /** 重新生成最后一条助手消息 */
    fun regenerateLastAssistant() {
        val currentMessages = _messages.value
        val lastAssistant = currentMessages.lastOrNull { it.role == "assistant" } ?: run {
            Log.w(TAG, "regenerateLastAssistant: no assistant message found")
            return
        }
        val lastUserMsg = currentMessages.lastOrNull { it.role == "user" }

        _messages.value = currentMessages.filter { it.id != lastAssistant.id && it.id != lastUserMsg?.id }

        viewModelScope.launch {
            runCatching { chatRepository.deleteMessageById(lastAssistant.id) }
            lastUserMsg?.let { runCatching { chatRepository.deleteMessageById(it.id) } }
        }

        if (lastUserMsg != null) {
            Log.d(TAG, "Regenerating from user message: '${lastUserMsg.content.take(50)}...'")
            sendMessage(lastUserMsg.content)
        } else {
            Log.w(TAG, "regenerateLastAssistant: no user message to regenerate from")
            _error.value = "没有可重新生成的用户消息"
        }
    }

    fun regenerateMessage(messageId: Long) {
        val currentMessages = _messages.value
        val targetAssistant = currentMessages.firstOrNull { it.id == messageId } ?: run {
            Log.w(TAG, "regenerateMessage: message $messageId not found")
            return
        }
        if (targetAssistant.role != "assistant") return

        val assistantIdx = currentMessages.indexOf(targetAssistant)
        val userMsg = currentMessages.take(assistantIdx).lastOrNull { it.role == "user" }

        _messages.value = currentMessages.filter { it.id != targetAssistant.id && it.id != userMsg?.id }

        viewModelScope.launch {
            runCatching { chatRepository.deleteMessageById(targetAssistant.id) }
            userMsg?.let { runCatching { chatRepository.deleteMessageById(it.id) } }
        }

        if (userMsg != null) {
            Log.d(TAG, "Regenerating message $messageId from user: '${userMsg.content.take(50)}...'")
            sendMessage(userMsg.content)
        } else {
            Log.w(TAG, "regenerateMessage: no user message found before $messageId")
            _error.value = "没有可重新生成的用户消息"
        }
    }

    /* ---------- 数据统计 ---------- */

    suspend fun getConversationCount(): Int = chatRepository.getConversationCount()
    suspend fun getMessageCount(): Int = chatRepository.getMessageCount()

    private suspend fun updateConversationTitle(convId: String, title: String) {
        try {
            val conv = chatRepository.getConversations().first().firstOrNull { it.id == convId }
            if (conv != null && conv.title == "新对话") {
                chatRepository.updateConversation(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
                Log.d(TAG, "Updated conversation title: $title")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update conversation title: ${e.message}")
        }
    }
}
