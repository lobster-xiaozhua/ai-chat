package com.example.aichat.ui.chat

import android.content.Context
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

    // —— Think 模式（深度推理：更慢、更全面；通过 system prompt + temperature 传递）
    private val _thinkMode = MutableStateFlow(false)
    val thinkMode: StateFlow<Boolean> = _thinkMode.asStateFlow()

    // —— Search 模式（联网/检索增强：通过 system prompt 告知模型可以查询上下文）
    private val _searchMode = MutableStateFlow(false)
    val searchMode: StateFlow<Boolean> = _searchMode.asStateFlow()

    // 待发送图片附件（content:// URI 字符串列表）
    // 使用 SnapshotStateList：Compose 中每个 Chip 的增删不会导致其他 Chip 重组
    private val _pendingImageUrls = androidx.compose.runtime.mutableStateListOf<String>()
    val pendingImageUrls: androidx.compose.runtime.snapshots.SnapshotStateList<String> = _pendingImageUrls

    // 待发送文档（uri 字符串列表 + uri→文件名映射）
    private val _pendingDocumentUrls = androidx.compose.runtime.mutableStateListOf<String>()
    val pendingDocumentUrls: androidx.compose.runtime.snapshots.SnapshotStateList<String> = _pendingDocumentUrls
    private val _pendingDocumentNames = androidx.compose.runtime.mutableStateMapOf<String, String>()
    val pendingDocumentNames: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String> = _pendingDocumentNames

    // 用户已选模型列表（用于快速切换）
    private val _selectedModelIds = MutableStateFlow(emptyList<String>())
    val selectedModelIds: StateFlow<List<String>> = _selectedModelIds.asStateFlow()

    private val _baseUrl = MutableStateFlow("https://api.deepseek.com")
    private val _temperatureStr = MutableStateFlow("1.0")
    private val _systemPrompt = MutableStateFlow("")

    private var generationJob: Job? = null
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()
    private var historyForApi = mutableListOf<Pair<String, String>>()

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

    /** 添加一组图片 URI（来自 Photo Picker）*/
    fun addImageUrls(urls: List<String>) {
        for (url in urls) {
            if (url !in _pendingImageUrls) _pendingImageUrls.add(url)
        }
    }

    /** 从待发送列表移除一张图片 */
    fun removeImageUrl(url: String) {
        _pendingImageUrls.remove(url)
    }

    /** 清空待发送图片列表 */
    fun clearPendingImages() {
        _pendingImageUrls.clear()
    }

    /* ---------- 对外操作：文档附件 ---------- */

    /**
     * 添加一批文档（通常来自 OpenMultipleDocuments contract 的返回）。
     * 入参是 (uri 字符串 → 显示用文件名) 的列表。
     */
    fun addDocumentUrls(urisWithNames: List<Pair<String, String>>) {
        for ((uri, name) in urisWithNames) {
            if (uri !in _pendingDocumentUrls) {
                _pendingDocumentUrls.add(uri)
                _pendingDocumentNames[uri] = name.ifEmpty { "未命名文档" }
            }
        }
    }

    /** 从待发送列表移除一个文档 */
    fun removeDocumentUrl(uri: String) {
        _pendingDocumentUrls.remove(uri)
        _pendingDocumentNames.remove(uri)
    }

    /** 清空待发送文档列表 */
    fun clearDocuments() {
        _pendingDocumentUrls.clear()
        _pendingDocumentNames.clear()
    }

    /* ---------- 对外操作：会话与消息 ---------- */

    fun setConversation(conversationId: String) {
        if (_currentConversationId.value == conversationId && _messages.value.isNotEmpty()) return
        _currentConversationId.value = conversationId
        generationJob?.cancel()
        _isGenerating.value = false
        _streamingAssistant.value = null
        clearPendingImages()
        clearDocuments()

        viewModelScope.launch {
            val history = chatRepository.getMessagesAsList(conversationId)
            _messages.value = history
            historyForApi = history
                .filter { it.role == "user" || it.role == "assistant" }
                .map { it.role to it.content }
                .toMutableList()
        }
    }

    /**
     * 发送消息 —— 支持纯文本、纯图片、文本+图片、文本+文档、文本+图片+文档
     *   · 文本/图片立即写入消息列表（消息气泡可见）
     *   · 文档内容异步提取 → 嵌入用户消息文本前 —— AI 按上下文处理
     *   · content:// → data://（base64）转换，供多模态 API 使用
     *   · 流式响应期间逐 token 更新界面
     *   · 完成后 assistant 消息写回持久层
     */
    fun sendMessage(text: String) {
        // snapshot 当前图片 + 文档列表
        val images = _pendingImageUrls.toList()
        val documents = _pendingDocumentUrls.toList()
        val docNames = _pendingDocumentNames.toMap()
        val convId = _currentConversationId.value ?: return
        if (text.isBlank() && images.isEmpty() && documents.isEmpty()) return

        // 确认进入生成流程后清空附件（失败时在 catch 中恢复）
        _pendingImageUrls.clear()
        _pendingDocumentUrls.clear()
        _pendingDocumentNames.clear()

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

                // —— 图片：content:// 异步 → base64 data URI（多模态 API 需要）
                val dataUriList = if (images.isNotEmpty()) images.toDataUriList(context, maxKb = 512) else emptyList()

                // —— 文档：异步提取每个文档的文本 → 拼到用户消息文本前
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

                // 如果有文档，更新 historyForApi 的最后一条 user 消息
                if (docParts.isNotEmpty()) {
                    val lastIdx = historyForApi.indexOfLast { it.first == "user" }
                    if (lastIdx >= 0) {
                        val newHistory = historyForApi.toMutableList()
                        newHistory[lastIdx] = "user" to combinedUserText
                        historyForApi = newHistory
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
                historyForApi.removeLastOrNull()
                restorePendingAttachments(images, documents, docNames)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止 → 保留部分内容，不恢复附件（已发送的消息是有效的）
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
                historyForApi.removeLastOrNull()
                restorePendingAttachments(images, documents, docNames)
            } finally {
                _streamingAssistant.value = null
                _isGenerating.value = false
            }
        }
    }

    /** 发送失败时恢复附件，让用户可以重试 */
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
        // 仅取消协程，让 CancellationException + finally 统一处理状态
        generationJob?.cancel()
    }

    fun clearError() { _error.value = null }

    fun setModel(model: String) {
        _currentModel.value = model
        viewModelScope.launch { settingsRepository.setDefaultModel(model) }
    }
}
