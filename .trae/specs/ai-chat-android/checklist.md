# AI Chat Android App - 验证清单

> 每个任务完成后须逐项勾选通过，方可标记任务为完成。

---

## 阶段总览

- [ ] **Phase 0 — Setup**（Task 1）: 项目可编译启动
- [ ] **Phase 1 — Navigation**（Task 2）: 四屏骨架 + 基础主题
- [ ] **Phase 2 — Data**（Task 3）: Room + DataStore + 加密存储
- [ ] **Phase 3 — Network**（Task 4）: Retrofit + SSE + 错误处理
- [ ] **Phase 4 — Chat Core**（Task 5）: 流式对话 + Markdown
- [ ] **Phase 5 — Conversations**（Task 6）: 会话列表 + 侧边栏
- [ ] **Phase 6 — Settings**（Task 7）: 设置页 + 个性化
- [ ] **Phase 7 — Polish**（Task 8）: 测试 + ProGuard + 性能
- [ ] **Phase 8 — Enhanced**（Task 9, optional）: 语音/导出/图片

---

## Task 1 — Setup 检查项

- [ ] 1.1 `./gradlew assembleDebug` 构建成功，0 编译错误
- [ ] 1.2 `./gradlew assembleRelease` 构建成功（签名可跳过，但 ProGuard 不报错）
- [ ] 1.3 应用可启动并显示 Material 3 基础主题
- [ ] 1.4 `settings.gradle.kts` 中 `pluginManagement` + `dependencyResolutionManagement` 配置正确
- [ ] 1.5 `build.gradle.kts`（根）声明了 Kotlin/AGP/Hilt 插件
- [ ] 1.6 app `build.gradle.kts` 中 `compileSdk = 35`、`minSdk = 29`、`targetSdk = 35`
- [ ] 1.7 `AndroidManifest.xml` 声明了 `android.permission.INTERNET` 和 `MainActivity`
- [ ] 1.8 `@HiltAndroidApp` Application class 存在且在 Manifest 中引用
- [ ] 1.9 Theme.kt 中 `DarkTheme`/`LightTheme` 两种 token 完整，主色 #6750A4
- [ ] 1.10 `proguard-rules.pro` 有基础规则

## Task 2 — Navigation 检查项

- [ ] 2.1 四个屏幕（Chat/Settings/CustomModel/Account）可通过 Navigation Compose 互相跳转
- [ ] 2.2 每个屏幕有独立的 Composable 入口，不依赖未实现的 Repository
- [ ] 2.3 骨架 UI 不崩溃（空按钮、空列表）
- [ ] 2.4 `ChatMode` enum 定义完整
- [ ] 2.5 `Conversation` / `Message` 数据类定义完整（后续补 Room 注解）
- [ ] 2.6 导航图无循环依赖
- [ ] 2.7 主题切换（手动在代码中改值预览）能正确切换深/浅色

## Task 3 — Data 检查项

- [ ] 3.1 `AppDatabase.kt` 中 `@Database` 注解包含 Conversation + Message，version = 1
- [ ] 3.2 `ConversationDao` 提供 Flow 返回的 `getAllConversations()`、search、CRUD
- [ ] 3.3 `MessageDao` 提供 `PagingSource<Int, Message>` 返回的 `getMessages()`
- [ ] 3.4 Message 实体包含 `conversationId` 外键 + 级联删除 + 索引
- [ ] 3.5 `SettingsDataStore` 封装了 theme/language/fontSize/apiKey/defaultModel/temperature/systemPrompt/baseUrl 的读写
- [ ] 3.6 `SecureKeyStore` 正确使用 `EncryptedSharedPreferences`（非普通 SharedPreferences）
- [ ] 3.7 `ChatRepository` / `SettingsRepository` 方法签名清晰，纯 suspend 或 Flow，不阻塞主线程
- [ ] 3.8 `DatabaseModule.kt` 中 `@Provides @Singleton` 全部配置
- [ ] 3.9 Room schema 导出目录已在 `build.gradle.kts` 中配置（`kapt { arguments { arg("room.schemaLocation", ...) } }` 或 ksp 对应配置）
- [ ] 3.10 手动 DAO 单元测试通过：insert → query 能读到

## Task 4 — Network 检查项

- [ ] 4.1 所有 DTO 使用 `@Serializable`，字段与 OpenAI Chat Completions 规范一致
- [ ] 4.2 `OpenAiApiService` 的 `streamChatCompletion` 使用 `@Streaming`，返回 `ResponseBody`
- [ ] 4.3 `EventSourceParser.parse()` 能正确解析典型 SSE 流：逐行处理 `data: {...}`，遇到 `data: [DONE]` 后关闭 Flow
- [ ] 4.4 `EventSourceParser` 正确忽略空行、注释行（以 `:` 开头）
- [ ] 4.5 协程取消时 `awaitClose { source.close() }` 被调用，不泄漏资源
- [ ] 4.6 `AiRepository.streamChat()` 中 HTTP 非 2xx 抛 `IOException` 带可读消息
- [ ] 4.7 `NetworkModule` 中 OkHttp 有 HttpLoggingInterceptor，Debug 为 BODY、Release 为 NONE
- [ ] 4.8 `Json { ignoreUnknownKeys = true }` 配置正确
- [ ] 4.9 单元测试：给 `EventSourceParser` 喂模拟流，输出的 token 序列与预期一致
- [ ] 4.10 单元测试：HTTP 401 时抛异常含"API Key"相关信息

## Task 5 — Chat Core 检查项

- [ ] 5.1 `ChatViewModel.sendMessage()` 正确：空消息不触发；生成中再发被忽略；user message 先插入 Room 或内存状态，再 collect 流式 token
- [ ] 5.2 `ChatViewModel.isGenerating` 状态流正确：发送前 true，完成/中断后 false
- [ ] 5.3 `stopGeneration()` 能同时取消 `generationJob` + OkHttp `Call`
- [ ] 5.4 `ChatScreen` 的 `LazyColumn` 使用稳定 key（`message.id`）+ contentType
- [ ] 5.5 用户气泡（#6750A4 / 白色文字 / 右下角 6dp 圆角）与 AI 气泡（浅灰/深灰 / 左下角 6dp 圆角）样式符合设计文档
- [ ] 5.6 流式回复时 AI 消息末尾显示闪烁光标 ▌，非流式时不显示
- [ ] 5.7 发送按钮：输入为空时灰色禁用，有内容时紫色圆形（40dp），生成中变为红色停止按钮
- [ ] 5.8 长按气泡弹出菜单：复制/重新生成/删除，每项功能实测有效
- [ ] 5.9 `MarkdownRenderer` 至少能渲染：`**粗体**`、`*斜体*`、`` `行内代码` ``、``` ```代码块``` ``、`[链接](url)`、标题/列表
- [ ] 5.10 空状态显示"✨ 开启一段新对话" + 横向提示词卡片，点击卡片能自动填入输入框并发送
- [ ] 5.11 底部工具栏三个按钮（深度思考/联网搜索/模型选择）样式正确，点击能切换状态
- [ ] 5.12 网络错误时 `Snackbar` 弹出可读提示，不崩溃
- [ ] 5.13 ViewModel 单元测试通过：send → 状态流验证；stop → 状态重置

## Task 6 — Conversations 检查项

- [ ] 6.1 侧边栏宽度 clamp(260dp, 75vw, 320dp)，遮罩半透明黑色
- [ ] 6.2 点击汉堡菜单 → 侧边栏滑入；点击遮罩或对话条目 → 侧边栏关闭
- [ ] 6.3 搜索框输入时实时过滤会话列表（基于 title 模糊匹配）
- [ ] 6.4 当前活跃会话左侧有 3dp 紫色竖条
- [ ] 6.5 新建对话 → 顶栏标题"新对话"，发送第一条 user message 后自动变为前 10 字
- [ ] 6.6 删除会话有二次确认（长按条目菜单）
- [ ] 6.7 ConversationListViewModel 中 `conversationsFlow.distinctUntilChanged()` 生效，数据变更时 UI 同步
- [ ] 6.8 点击侧边栏底部 → 跳转到账号管理页

## Task 7 — Settings 检查项

- [ ] 7.1 深色模式 `Switch`：开启后全应用反色，重启应用仍记住
- [ ] 7.2 语言下拉：中文 / English 可切换，字符串资源即时更新
- [ ] 7.3 字号下拉：小/中/大 三档，下方实时预览文字 "预览文字效果 Preview" 随之变化
- [ ] 7.4 默认模型下拉：包含 DeepSeek-V3、DeepSeek-Coder、GPT-4o Mini、自定义
- [ ] 7.5 Temperature Slider：0.0–2.0，步长 0.2，右侧实时显示当前数值
- [ ] 7.6 自定义模型配置页：三个输入框（API 地址、模型名称、API Key 密文显示）+ 底部紫色全宽保存按钮；保存成功后 toast + 返回设置页
- [ ] 7.7 系统提示词：多行 TextField + 预设模板按钮（翻译助手/代码专家/写作助手），点击模板填入默认文本
- [ ] 7.8 清除所有对话：红色文字，点击弹出二次确认（"确认要删除所有对话吗？此操作不可恢复"），确认后全部删除
- [ ] 7.9 内容举报：点击跳转到外部浏览器 URL（`Intent.ACTION_VIEW`）
- [ ] 7.10 账号管理页：64dp 圆形头像占位 + 邮箱占位 + "修改头像/修改密码/绑定手机" 点击 toast"敬请期待" + "退出登录"红色文字
- [ ] 7.11 中英文 `strings.xml` 覆盖全部主要 UI 文本
- [ ] 7.12 SettingsRepository 的 Flow 与 suspend 函数与 ViewModel 正确连接

## Task 8 — Polish 检查项

- [ ] 8.1 `./gradlew testDebugUnitTest` 全部通过（至少 ChatViewModel、EventSourceParser、SettingsRepository 三个测试文件各 ≥1 个用例）
- [ ] 8.2 `./gradlew assembleRelease` 构建成功，ProGuard 无警告导致的运行崩溃
- [ ] 8.3 Release 构建的 APK 中，OkHttp 日志拦截器级别为 NONE，不打印 Authorization 头
- [ ] 8.4 历史消息加载使用 Paging 3，当前会话的流式消息仍走内存 List 保证响应性
- [ ] 8.5 键盘弹出时输入区 + 工具栏保持在键盘上方，不被遮挡（WindowInsets.ime 适配）
- [ ] 8.6 `Scaffold` + `WindowInsets.safeDrawing` 处理刘海屏/指示条/状态栏
- [ ] 8.7 横屏/平板布局合理：侧边栏宽度不超 400dp，消息气泡最大宽度 clamp 合理
- [ ] 8.8 README.md 中增加了构建说明、隐私政策 URL、合规声明
- [ ] 8.9 真机滚动测试：1000+ 条消息 ≥50fps（可通过 Android Studio Profiler 验证或目测流畅度）
- [ ] 8.10 无 `TODO` 未清理的遗留硬编码（除了隐私政策 URL 允许临时占位）

## Task 9 — Enhanced 检查项（可选）

- [ ] 9.1 语音输入：点击麦克风图标 → 语音识别 → 文本自动填入输入框（中文识别率 ≥80%）
- [ ] 9.2 TTS 朗读：长按 AI 消息 → "朗读" 菜单 → TextToSpeech 读出内容
- [ ] 9.3 对话导出：侧边栏或设置中增加"导出为 Markdown" → 生成 .md 文件 → 系统分享面板
- [ ] 9.4 图片生成：输入 `/image 描述` → 独立调用 DALL-E 兼容 API → 返回图片显示

---

## 通用质量检查（贯穿所有任务）

- [ ] G-1 代码风格统一：Kotlin，遵循官方编码规范；4 空格缩进；`val` 优于 `var`
- [ ] G-2 分层清晰：UI 不直接依赖 DAO/Retrofit，必须通过 ViewModel → Repository
- [ ] G-3 无内存泄漏：ViewModel 不持有 Context 引用，协程使用 `viewModelScope` 自动取消
- [ ] G-4 无硬编码字符串：所有 UI 文本来自 `R.string.*`，中英文两套
- [ ] G-5 无魔法数字：颜色、尺寸、间距常量集中在 Theme.kt 或资源文件中
- [ ] G-6 所有 Composable 使用 `Modifier` 参数，便于重用
- [ ] G-7 `@Inject`/`@Provides` 依赖注入完整，无手动 `new` 关键对象
- [ ] G-8 所有 suspend/Flow 在 `Dispatchers.IO` 执行，UI 收集在 `Dispatchers.Main.immediate`（Lifecycle 自动处理）
- [ ] G-9 无直接在主线程执行磁盘/网络 IO
- [ ] G-10 `build.gradle.kts` 版本号与设计文档一致（Kotlin 2.0、Compose BOM 2024.06、Room 2.6.1、Hilt 2.51、Retrofit 2.11）
