# AI Chat Android App - 实现计划（分解与优先级任务列表）

> **关键原则**: 每阶段产出一个可运行的子系统，减少文件之间的互相等待。分层按"脚手架→数据→网络→核心UI→辅助UI→设置→打磨"顺序推进。

---

## [x] Task 1: 项目脚手架与依赖注入（Phase 0 — Setup） ✅
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 创建 `settings.gradle.kts`（含 `dependencyResolutionManagement`、`pluginManagement`）
  - 创建根 `build.gradle.kts`（Kotlin 2.0、AGP 8.5、Hilt 插件）
  - 创建 app 模块 `build.gradle.kts`（Compose BOM 2024.06、Room 2.6.1、Hilt 2.51、Retrofit 2.11、OkHttp 4.12、Kotlinx Serialization、DataStore、Paging 3、Compose Testing）
  - 创建 `AndroidManifest.xml`（`android.permission.INTERNET`，`MainActivity` 作为 launcher）
  - 创建 `MainActivity.kt`（`@AndroidEntryPoint`，`setContent { AiChatTheme { NavHost(...) } }`）
  - 创建 `AiChatApp.kt`（`@HiltAndroidApp` application class）
  - 创建 `Theme.kt` / `Color.kt` / `Type.kt` / `Shape.kt`（Material 3 基础主题，支持深色/浅色模式切换）
  - 创建 `proguard-rules.pro`（保留序列化模型、Retrofit 接口、Kotlinx Serialization）
  - 创建 `gradle.properties`（`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`、`android.useAndroidX=true`、`kotlin.code.style=official`）
- **Acceptance Criteria Addressed**: AC-4 的基础（主题系统可切换）、NFR-5 的分层基础
- **Test Requirements**:
  - `programmatic` TR-1.1: `./gradlew assembleDebug` 成功构建，无编译错误
  - `programmatic` TR-1.2: 应用可启动并显示空白屏
  - `human-judgment` TR-1.3: `Theme.kt` 中深色/浅色 token 映射完整，无未定义颜色引用
- **Notes**: 主题颜色严格按设计文档：Primary #6750A4，Background #FFFFFF/#121212，Surface #F8F8FA/#1E1E1E

---

## [x] Task 2: 导航框架与三屏骨架（Phase 1 — Navigation） ✅
- **Priority**: P0
- **Depends On**: Task 1
- **Description**:
  - 创建 `data/model/ChatMode.kt`（`enum class ChatMode { DEEP_THINK, WEB_SEARCH, QUICK }`）
  - 创建 `data/model/Conversation.kt`（`@Entity` Conversation 实体，含 `id/title/systemPrompt/createdAt/updatedAt`）
  - 创建 `data/model/Message.kt`（`@Entity` Message 实体，含 `id/conversationId/role/content/timestamp`）
  - 创建 `ui/navigation/Routes.kt`（sealed class Routes: `Chat`, `Settings`, `CustomModel`, `Account`）
  - 创建 `ui/navigation/NavGraph.kt`（`@Composable NavHost(...)`，四个路由入口）
  - 创建 `ui/chat/ChatScreen.kt`（骨架：顶部标题栏 + 空 LazyColumn + 输入框 + 发送按钮）
  - 创建 `ui/settings/SettingsScreen.kt`（骨架：列表项占位，主题/语言/字号/模型/隐私分组）
  - 创建 `ui/settings/CustomModelScreen.kt`（骨架：三个输入框 + 保存按钮）
  - 创建 `ui/settings/AccountScreen.kt`（骨架：头像 + 列表项 + 退出登录）
  - 创建空的 `ChatViewModel.kt` / `SettingsViewModel.kt`（`@HiltViewModel`，`MutableStateFlow` 占位状态）
- **Acceptance Criteria Addressed**: AC-4 的导航骨架、AC-5 的 UI 布局基础
- **Test Requirements**:
  - `programmatic` TR-2.1: `./gradlew assembleDebug` 通过
  - `human-judgment` TR-2.2: 四个屏幕可通过导航切换，无崩溃
  - `human-judgment` TR-2.3: 配色与设计文档一致，主色 #6750A4
- **Notes**: Conversation/Message 实体先不写 Room 注解，Task 3 中补齐，避免本 Task 依赖 Room 编译器

---

## [x] Task 3: 本地数据层（Phase 2 — Data） ✅
- **Priority**: P0
- **Depends On**: Task 2
- **Description**:
  - 补齐 `Conversation.kt` 的 Room 注解：`@Entity(indices = [Index(value = ["updatedAt"])])`
  - 补齐 `Message.kt` 的 Room 注解：`@Entity(foreignKeys = [ForeignKey(entity = Conversation::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["conversationId"])])`
  - 创建 `data/local/db/ConversationDao.kt`（`@Dao`，含 `getAllConversations(): Flow<List<Conversation>>`、`searchConversations(query): Flow`、`insert/update/delete`）
  - 创建 `data/local/db/MessageDao.kt`（`getMessages(id): PagingSource<Int, Message>`、`insert/delete`）
  - 创建 `data/local/db/AppDatabase.kt`（`@Database(entities = [Conversation::class, Message::class], version = 1) abstract class AppDatabase : RoomDatabase()`）
  - 创建 `data/local/preferences/SettingsDataStore.kt`（`DataStore<Preferences>`，封装 `themeMode/language/fontSize/apiKey/defaultModel/temperature/systemPrompt/baseUrl` 的 read/write）
  - 创建 `data/security/SecureKeyStore.kt`（EncryptedSharedPreferences 封装，用于 API Key 读写）
  - 创建 `data/repository/SettingsRepository.kt`（聚合 DataStore + SecureKeyStore，暴露 Flow 与 suspend 读写方法）
  - 创建 `data/repository/ChatRepository.kt`（依赖 DAO，暴露 `getConversationsFlow / getMessagesPaging / insertMessage / createConversation / deleteConversation / updateConversationTitle`）
  - 创建 `di/DatabaseModule.kt`（`@Provides @Singleton` 提供 Room Database、DAO、DataStore、SettingsRepository、ChatRepository、SecureKeyStore）
- **Acceptance Criteria Addressed**: AC-3（会话持久化）、AC-6（Key 加密）、AC-8（Paging）
- **Test Requirements**:
  - `programmatic` TR-3.1: Room DAO 编译通过，Room Schema 生成（`room.schemaLocation` 已配置）
  - `programmatic` TR-3.2: 手动单元测试——`insertConversation → getAllConversations` 能读到刚插入的条目
  - `human-judgment` TR-3.3: `SecureKeyStore` 在真机上可写入 + 读取同一字符串（无 KeyStore 异常）
- **Notes**: `message.timestamp` 用毫秒级 Long；`conversation.id` 用 UUID.randomUUID().toString()

---

## [x] Task 4: 网络层与 SSE 解析（Phase 3 — Network） ✅
- **Priority**: P0
- **Depends On**: Task 3
- **Description**:
  - 创建 `data/remote/dto/ChatCompletionRequest.kt`（`@Serializable`，含 `model/messages/temperature/max_tokens/stream=true`）
  - 创建 `data/remote/dto/ChatCompletionChunk.kt`（`@Serializable`，含 `choices: List<Choice>`）
  - 创建 `data/remote/dto/Choice.kt` / `Delta.kt` / `RequestMessage.kt`
  - 创建 `data/remote/OpenAiApiService.kt`（`interface`，`@POST("v1/chat/completions") @Streaming fun streamChatCompletion(@Header("Authorization") auth, @Body body): ResponseBody`）
  - 创建 `data/remote/sse/EventSourceParser.kt`（`fun parse(source: BufferedSource): Flow<String> = callbackFlow { ... }`，处理 `data: {...}` 行与 `data: [DONE]`，忽略空行与注释行）
  - 创建 `data/repository/AiRepository.kt`（`streamChat(messages, systemPrompt): Flow<String>`，组合 apiKey/model/temperature 从 SettingsRepository 读取，构造请求，解析 SSE 流，逐 token emit）
  - 在 `AiRepository.streamChat` 中增加：HTTP 状态码非 2xx 抛 `IOException`；网络异常包裹为用户可读错误消息；`try/catch` 不吞噬 `CancellationException`
  - 创建 `di/NetworkModule.kt`（`@Provides @Singleton` 提供 `Json { ignoreUnknownKeys = true }`、`OkHttpClient`（带 HttpLoggingInterceptor，Release 级别 NONE）、`Retrofit.Builder()`、`OpenAiApiService`）
- **Acceptance Criteria Addressed**: AC-1（流式）、AC-2（停止）、AC-9（错误提示）
- **Test Requirements**:
  - `programmatic` TR-4.1: `EventSourceParser.parse()` 给定模拟 SSE 字符串流，能 emit 正确的 token 序列，并在 `[DONE]` 后 close
  - `programmatic` TR-4.2: `EventSourceParser` 遇到空行/注释行（以 `:` 开头）能正确忽略
  - `programmatic` TR-4.3: `AiRepository` 在 Retrofit mock 返回 401 时，能抛出带"API Key 无效"信息的异常
  - `human-judgment` TR-4.4: 使用 curl 手动测试目标 API，SSE 行格式确认与解析器兼容
- **Notes**: 简化动态 Base URL 实现——Retrofit 使用 `SettingsRepository.getBaseUrl()` 读取的 baseUrl；配置变更后提示用户重启应用

---

## [/] Task 5: 聊天界面核心实现（Phase 4 — Chat）
- **Priority**: P0
- **Depends On**: Task 4
- **Description**:
  - 扩展 `ChatViewModel.kt`：
    - `private val _messages = MutableStateFlow<List<Message>>(emptyList())` + `val messages`（从 Room Paging 读取时转为普通列表用于流式 UI；历史消息用 Paging，当前会话用内存 List）
    - `private val _isGenerating = MutableStateFlow(false)`、`val isGenerating`
    - `private val _currentMode = MutableStateFlow(ChatMode.DEEP_THINK)`、`val currentMode`
    - `private val _error = MutableStateFlow<String?>(null)`、`val error`
    - `private var generationJob: Job? = null`
    - `fun sendMessage(text: String)`：若生成中或 text 为空则忽略；插入 user Message → 调用 `aiRepository.streamChat()` → `collect { token ->` 追加 assistant Message → 流式完成后刷新 Room }`；在整个过程中维护 `_isGenerating`
    - `fun stopGeneration()`：`generationJob?.cancel()`，`_isGenerating = false`
    - `fun regenerateLast()`：删除最后一条 assistant，重新调用发送逻辑
    - `fun deleteMessage(id)` / `fun copyMessage(id)` / `fun setMode(mode)`
  - 创建 `ui/chat/MessageBubble.kt`（Composable，区分用户/AI 两种气泡样式，按设计文档的 `#6750A4` / `#F2F2F5` 背景，圆角 20dp/左下角 6dp/右下角 6dp）
  - 创建 `ui/chat/MarkdownRenderer.kt`（MVP 级：简单正则解析粗体 `**x**`、斜体 `*x*`、代码 `` `x` ``、代码块 ``` ``` ``` ```、链接 `[x](y)`、换行，返回 `AnnotatedString`；流式过程中增量构建，避免全量 AST 重建）
  - 重写 `ChatScreen.kt` 完整实现：
    - 顶部透明栏：汉堡菜单按钮 + 动态标题（从 conversation.title 读取，空对话显示"新对话"）
    - 中部对话区：`LazyColumn`，items 用 `key = message.id` + `contentType` 区分角色；空状态显示"✨ 开启一段新对话" + 横向提示词卡片（`@Composable PromptCard(text, onClick)`）
    - 底部工具栏：三个横向按钮（深度思考/联网搜索/模型选择），`ChatMode` 选中态用主色强调
    - 输入区：圆角输入框 `BasicTextField` + 圆形发送/停止按钮（40dp，根据 `isGenerating` 切换图标与紫色/红色）
    - 长按消息气泡 → 弹出 `DropdownMenu`（复制/重新生成/删除）
    - 流式追加时，AI 消息末尾显示闪烁光标 `▌`（用 `LaunchedEffect` 每 500ms 切换一个空格/光标字符，仅在 isGenerating 时）
  - 在 `ChatScreen` 中接入错误状态显示（`SnackbarHost`，当 `error != null` 时弹出）
- **Acceptance Criteria Addressed**: AC-1/AC-2/AC-3(部分)/AC-7/AC-13(Markdown)/AC-9
- **Test Requirements**:
  - `programmatic` TR-5.1: `ChatViewModel.sendMessage("hello")` → `messages.test { ... }` 验证状态序列：首项包含 user 消息，后续追加 assistant token
  - `programmatic` TR-5.2: `stopGeneration()` 调用后 `isGenerating` 为 false，且协程真正取消（Flow.collect 中断）
  - `human-judgment` TR-5.3: 真机流式体验无明显卡顿，token 追加流畅
  - `human-judgment` TR-5.4: 长按菜单能复制文本到剪贴板
  - `human-judgment` TR-5.5: Markdown 粗体/代码块/链接样式正确渲染
- **Notes**: 流式 Markdown MVP 不做复杂语法树——通过 `AnnotatedString.Builder` 增量追加 + remember 缓存解析位置，避免每次 token 全量重解析

---

## [ ] Task 6: 会话列表与侧边栏（Phase 5 — Conversations）
- **Priority**: P1
- **Depends On**: Task 5
- **Description**:
  - 创建 `ConversationListViewModel.kt`（`@HiltViewModel`，订阅 `chatRepository.conversationsFlow`，暴露 `search(query)`、`deleteConversation(id)`、`renameConversation(id, newTitle)`、`createNewConversation(): String`）
  - 在 `ChatScreen.kt` 顶部栏的汉堡按钮点击后：右侧遮罩半透明黑色 + 左侧滑入 `Drawer`（宽度 clamp 260-320dp）
  - 侧边栏内容：
    - 顶部"历史对话"标题
    - 搜索框（圆角，键入实时过滤）
    - 列表：每个条目单行标题 + ellipsis；当前活跃项左侧 3dp 紫色竖条；点击加载会话 + 关闭侧边栏；长按条目弹出重命名/删除菜单
    - 底部区域：圆形头像 + "个人中心"文字 + 更多按钮 `⋮`；点击整个区域进入账号管理页
  - 新对话逻辑：点击"新对话"或侧边栏顶部按钮 → `createNewConversation()` → navigation 切换回聊天页 → 顶栏标题"新对话"
  - 自动生成标题逻辑：`ChatRepository.sendMessageAndUpdateConversation()` 中，当 conversation.title == "新对话" 且刚插入第一条 user message 时，截取用户消息前 10 个字符作为新 title
  - 实时同步：ConversationListViewModel 的 `conversationsFlow.distinctUntilChanged()`，聊天页新消息写入 Room 后自动更新
- **Acceptance Criteria Addressed**: AC-3/AC-4
- **Test Requirements**:
  - `programmatic` TR-6.1: `createNewConversation()` 后 `getAllConversations()` 列表长度 +1
  - `programmatic` TR-6.2: 首次发送消息后，conversation.title 从"新对话"变为用户消息前 10 字
  - `human-judgment` TR-6.3: 侧边栏滑动流畅，搜索实时过滤有效
- **Notes**: 侧边栏 Navigation Drawer 使用 Material 3 的 `ModalNavigationDrawer`；无需自己实现动画

---

## [ ] Task 7: 设置与个性化（Phase 6 — Settings）
- **Priority**: P1
- **Depends On**: Task 6
- **Description**:
  - 扩展 `SettingsViewModel.kt`：
    - 暴露 `themeFlow / languageFlow / fontSizeFlow / defaultModelFlow / temperatureFlow / systemPromptFlow`（均来自 `SettingsRepository`）
    - `fun setTheme(theme)` / `fun setLanguage(lang)` / `fun setFontSize(size)` / `fun setDefaultModel(model)` / `fun setTemperature(value)` / `fun setSystemPrompt(text)` / `fun setCustomModel(baseUrl, modelName, apiKey)` / `fun clearAllConversations()`
  - 重写 `SettingsScreen.kt`：
    - 分组卡片式布局（账号/通用设置/模型配置/隐私）
    - 通用设置：深色模式 `Switch` + 语言下拉（中文/English）+ 字号下拉（小/中/大）+ 下方实时预览行 "预览文字效果 Preview"
    - 模型配置：默认模型下拉（DeepSeek-V3、DeepSeek-Coder、GPT-4o Mini、自定义）+ Temperature `Slider`（0.0-2.0, step 0.2）右侧显示当前数值 + "自定义大模型配置 ›" 入口
    - 隐私："清除所有对话"红色文字（点击弹出二次确认对话框）+ "内容举报 ›" 跳转到外部浏览器 URL
    - 系统提示词：多行 `TextField`，提供预设模板按钮（翻译助手/代码专家/写作助手）
  - 重写 `CustomModelScreen.kt`：顶部返回按钮 + 标题"自定义模型配置"；三个输入框（API 地址、模型名称、API Key，密文显示）；底部全宽紫色保存按钮；保存成功后 toast 提示并返回设置页
  - 重写 `AccountScreen.kt`：顶部返回按钮 + 标题"账号管理"；居中大头像（64dp 圆形占位）+ 邮箱（占位文本）；列表项"修改头像/修改密码/绑定手机"（均为占位，点击 toast"敬请期待"）；设置入口（返回或跳转到 Settings）；底部"退出登录"红色文字
  - 语言切换实现：`AppCompatDelegate.setApplicationLocales(LocaleList(Locale(lang)))` + Compose `MutableState<Long>` 的 `CompositionLocal` 触发强制重组（最简单方案）
  - 字号切换实现：CompositionLocal 暴露 `val LocalFontScale = compositionLocalOf { 1.0f }`，`Theme.kt` 中 Typography 的 fontSize 乘以该系数
  - 创建 `res/values/strings.xml`（中文默认）与 `res/values-en/strings.xml`（英文翻译），覆盖所有 UI 文本
- **Acceptance Criteria Addressed**: AC-4/AC-5/AC-6/AC-8/AC-10/FR-16
- **Test Requirements**:
  - `programmatic` TR-7.1: `setTheme(DARK)` 后 `themeFlow` 下一项为 DARK
  - `programmatic` TR-7.2: `clearAllConversations()` 后 DAO 查询返回空列表
  - `human-judgment` TR-7.3: 深色模式切换后颜色反转正确，无硬编码白色
  - `human-judgment` TR-7.4: 中英文切换生效，所有主要按钮/标题已国际化
  - `human-judgment` TR-7.5: Temperature 滑块拖动时数值实时更新
- **Notes**: "内容举报" URL 暂时使用 `https://example.com/privacy-report`，后续替换为真实 GitHub Pages URL

---

## [ ] Task 8: 测试、ProGuard 与打磨（Phase 7 — Polish）
- **Priority**: P1
- **Depends On**: Task 7
- **Description**:
  - 单元测试：
    - `ChatViewModelTest.kt`（使用 `TestDispatcher` + `Turbine`，覆盖 TR-5.1/TR-5.2 场景 + 网络错误 + 空消息忽略）
    - `EventSourceParserTest.kt`（纯 Kotlin，mock BufferedSource）
    - `SettingsRepositoryTest.kt`（mock DataStore，验证读写一致性）
  - 增加 `Paging 3` 集成：`MessageDao.getMessages()` 返回 `PagingSource`，`ChatScreen` 的历史消息用 `LazyPagingItems` 渲染（当前会话的流式消息仍走内存 List 以保证即时响应；切换对话时从 Paging 加载历史）
  - 增加 `Baseline Profile` 配置（可选但推荐，加速冷启动）
  - 完善 `proguard-rules.pro`：保留 `com.example.aichat.data.remote.dto.**`、`kotlinx.serialization.**`、`com.example.aichat.data.remote.OpenAiApiService`；移除 OkHttp 日志拦截器的 Authorization 头打印
  - `MainActivity` 增加 `WindowInsets.ime` 适配：键盘弹出时工具栏不被顶起消失
  - 增加 `Scaffold` + `WindowInsets.safeDrawing` 处理刘海屏/指示条
  - 增加全应用的 `minWidth/height` 与横屏/平板布局检查
  - README.md 补充构建说明与隐私政策链接
- **Acceptance Criteria Addressed**: NFR-1(性能)/NFR-2(安全)/NFR-4(可测试性)/AC-8
- **Test Requirements**:
  - `programmatic` TR-8.1: `./gradlew testDebugUnitTest` 全部通过
  - `programmatic` TR-8.2: `./gradlew assembleRelease` 成功
  - `human-judgment` TR-8.3: 键盘弹出时工具栏固定在输入框上方，不随键盘位移
  - `human-judgment` TR-8.4: 横屏/平板布局合理，对话卡片宽度限制在合理范围
- **Notes**: MVP 阶段不强制 Baseline Profile，可作为 P2 优化项；先保证 Release 构建无崩溃

---

## [ ] Task 9: 增强功能（可选，P2 — Enhanced）
- **Priority**: P2
- **Depends On**: Task 8
- **Description**:
  - 语音输入：`SpeechRecognizer` + `RecognitionListener`，输入框左侧增加麦克风图标按钮
  - TTS 朗读：`TextToSpeech`，长按 AI 消息时增加"朗读"菜单项
  - 对话导出：将会话转为 Markdown，通过 `Intent.ACTION_SEND` 系统分享
  - 图片生成：独立的 `/image` 指令识别，调用 DALL-E 兼容 API（独立 Repository）
  - 云端同步占位：预留接口（Gitee/GitHub 私有仓库备份），不实现
- **Acceptance Criteria Addressed**: NFR-5（可扩展性）
- **Test Requirements**:
  - `human-judgment` TR-9.1: 语音输入能正确识别中文短句子
  - `human-judgment` TR-9.2: 导出的 Markdown 文件结构正确，可被普通阅读器打开
- **Notes**: 这一阶段完全可选，仅在 MVP 稳定后考虑

---

## 依赖有向图（DAG）

```
Task 1 (Setup) ──► Task 2 (Navigation) ──► Task 3 (Data) ──► Task 4 (Network)
                                                         │
                                                         └──► Task 5 (Chat Core) ──► Task 6 (Conversations)
                                                                                         │
                                                                                         └──► Task 7 (Settings) ──► Task 8 (Polish) ──► Task 9 (Enhanced, optional)
```

## 关键技术难点提示

| 难点 | 所在 Task | 简化策略 |
|------|-----------|---------|
| SSE 协程安全关闭 | Task 4 | `callbackFlow` + `awaitClose { source.close() }` + 不吞 `CancellationException` |
| 流式 Markdown 无闪烁 | Task 5 | MVP 仅 AnnotatedString Builder 增量 + remember 缓存；完整 AST 后续 |
| 停止生成真正中断 | Task 5 | `generationJob.cancel()` + OkHttp `Call.cancel()` 双保险 |
| 动态 Base URL | Task 4 | 简化为"配置变更后提示重启"，避免拦截器中的 runBlocking |
| 会话列表实时同步 | Task 6 | Room Flow + distinctUntilChanged + stateIn(viewModelScope, WhileSubscribed) |
| 语言切换 Compose 刷新 | Task 7 | AppCompatDelegate.setApplicationLocales + `key(timeTick)` 强制重组 |
| 键盘弹出工具栏位置 | Task 8 | `WindowInsets.ime` + Scaffold |
