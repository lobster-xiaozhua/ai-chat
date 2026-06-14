# AI Chat Android App - 产品需求文档

## Overview
- **Summary**: 一款极简、克制、高效的个人 AI 聊天助手。支持多模型/多会话/流式响应，数据完全存储在本地。技术栈：Kotlin + Jetpack Compose + MVVM + Hilt + Room + Retrofit + SSE。
- **Purpose**: 为技术用户提供一个可配置 API Key 的轻量级 AI 对话客户端，替代复杂臃肿的同类应用
- **Target Users**: 开发者、技术写作人员、AI 重度使用者，关注隐私与效率

## Goals
- 实现高质量高性能的流式 AI 聊天体验（打字机效果、可中断）
- 会话/消息的本地持久化（Room + DataStore）
- 多模型配置与快速切换（支持自定义 OpenAI 兼容 API）
- 个性化设置（深色模式/语言/字号/系统提示词/Temperature）
- 完整 Markdown 渲染（标题/列表/代码块高亮/表格/链接/粗斜体）
- 符合 Google Play 生成式 AI 应用合规要求

## Non-Goals (Out of Scope)
- **不做**账号系统/云同步（仅本地存储）- MVP 版本
- **不做**语音输入 / TTS 朗读（P2，可选增强）
- **不做**对话导出为 Markdown 文件（P2）
- **不做**图片生成功能（P2）
- **不做**实时协作/多人会话
- **不做** iOS / Web 版本
- **不做**自建大模型推理

## Background & Context
- 当前目录仅含 `README.md` 和 `.gitignore`，无实际代码
- 已有完整设计文档（`ai_chat_ android_development_documentation.txt`）作为实现参考
- 使用 Gradle KTS + AGP 8.x + JDK 17
- 目标平台：Android (minSdk 29, targetSdk 35)
- 设计哲学：DeepSeek 风格——极简、克制、去装饰化，聚焦内容
- 分层架构：UI → ViewModel → Repository → Local/Remote Sources

## Functional Requirements
- **FR-1**: 用户可发送文本消息，接收流式 AI 回复（打字机效果，带闪烁光标）
- **FR-2**: 生成过程中可点击停止按钮中断，保留已生成内容
- **FR-3**: 长按消息气泡弹出操作菜单（复制/重新生成/删除）
- **FR-4**: 会话管理——新建/切换/重命名/删除/搜索历史对话
- **FR-5**: 空状态展示横向提示词卡片，点击自动填入并发送
- **FR-6**: 模型选择——底部弹窗列表切换，支持自定义模型配置
- **FR-7**: 模式切换——深度思考/联网搜索/快速模式（仅 UI 状态，实际依赖模型支持）
- **FR-8**: 系统提示词自定义（全局 + 会话级覆盖）
- **FR-9**: 设置页——深色模式开关/语言切换/字号三档调节/Temperature 滑块/自定义模型配置入口
- **FR-10**: 账号管理页——头像/邮箱/修改密码/绑定手机/退出登录（MVP 仅占位 UI，无后端）
- **FR-11**: API Key 加密本地存储（EncryptedSharedPreferences）
- **FR-12**: 自定义模型配置页——API 地址/模型名称/API Key 三项输入
- **FR-13**: 消息 Markdown 渲染，流式过程中增量渲染无闪烁
- **FR-14**: 对话数据完全存储在本地 Room 数据库，设置项使用 DataStore
- **FR-15**: 内容举报入口与隐私声明链接（合规要求）
- **FR-16**: 清除所有对话功能（二次确认）

## Non-Functional Requirements
- **NFR-1 性能**: 消息列表滚动 ≥50fps，流式追加 token 时主线程占用 ≤10ms/帧
- **NFR-2 安全**: API Key 使用 Android KeyStore 加密存储，HTTPS 传输，Release 构建移除 Authorization 头日志
- **NFR-3 可靠性**: 网络异常/API 限流有友好提示和错误状态；停止生成能真正中断网络连接
- **NFR-4 可测试性**: Repository 和 ViewModel 具备单元测试条件，核心流程有测试用例
- **NFR-5 可维护性**: 分层清晰，模块间低耦合，核心聊天模块独立便于更换 AI 后端
- **NFR-6 合规性**: 符合 Google Play 生成式 AI 应用政策——内容举报入口、隐私政策、禁止生成受限内容声明
- **NFR-7 多语言**: 支持中文/英文切换，字符串资源已国际化
- **NFR-8 响应式**: 适配手机/平板/横屏，Material 3 Compact/Medium/Expanded 断点

## Constraints
- **技术**: Kotlin 2.0 + Jetpack Compose BOM 2024.06 + Hilt 2.51 + Room 2.6.1 + Retrofit 2.11
- **业务**: 必须提供隐私政策 URL（GitHub Pages 托管），声明数据仅存储本地
- **依赖**: 需要用户自备 OpenAI 兼容 API Key，应用不提供内置 Key
- **发布**: targetSdk ≥34，需 AAB 打包，内容分级问卷

## Assumptions
- 用户已有 API Key 或愿意注册获取
- 用户设备运行 Android 10+（minSdk 29）
- 流式 API 遵循 OpenAI Chat Completions SSE 规范（`data: {...}` / `data: [DONE]`）
- 自定义模型配置变更后需重启应用生效（简化实现）

## Acceptance Criteria

### AC-1: 发送消息与流式回复
- **Given**: 用户已配置 API Key 且处于有效会话
- **When**: 用户输入文本并点击发送按钮或按回车
- **Then**: 用户气泡出现在列表中，AI 气泡随之出现并逐 token 追加内容，末尾有闪烁光标 ▌；发送按钮变为红色停止按钮；生成完毕后光标消失，按钮恢复
- **Verification**: `human-judgment`（手动测试交互）+ `programmatic`（ViewModel 状态流测试）

### AC-2: 停止生成
- **Given**: AI 正在流式生成回复
- **When**: 用户点击红色停止按钮
- **Then**: 流式响应立即中断，已生成内容保留，生成状态重置为 false，按钮恢复紫色
- **Verification**: `human-judgment`（测试能否真正中断）

### AC-3: 会话管理
- **Given**: 用户已有若干历史对话
- **When**: 用户从侧边栏选择/搜索/重命名/删除会话
- **Then**: 对应的 UI 更新，数据持久化到 Room，标题根据首条用户消息自动生成
- **Verification**: `programmatic`（DAO 测试 + Flow 状态验证）

### AC-4: 主题与语言切换
- **Given**: 用户在设置页操作开关
- **When**: 切换深色模式/语言/字号
- **Then**: 整个应用立即应用新主题，无需重启；字符串资源切换为对应语言；字号三档同步调整
- **Verification**: `human-judgment`（视觉确认）

### AC-5: 模型切换与自定义配置
- **Given**: 用户已在设置页配置自定义模型
- **When**: 在聊天页底部工具栏点击"模型选择"
- **Then**: 底部弹窗列出所有已配置模型，选中后按钮文本更新，下次对话使用新模型
- **Verification**: `human-judgment`（UI 流）+ `programmatic`（配置持久化验证）

### AC-6: API Key 安全存储
- **Given**: 用户首次启动应用
- **When**: 在自定义模型配置页输入 API Key
- **Then**: Key 使用 EncryptedSharedPreferences 加密存储，应用重启后可读取；不会出现在 Release 构建的日志中
- **Verification**: `programmatic`（加密写入/读取一致性测试）

### AC-7: 消息长按操作菜单
- **Given**: 任意消息气泡存在于对话中
- **When**: 用户长按气泡
- **Then**: 弹出上下文菜单（复制/重新生成/删除），选择对应操作后 UI 与持久化数据同步更新
- **Verification**: `human-judgment`

### AC-8: 大消息列表性能
- **Given**: 会话内有 1000+ 条消息
- **When**: 用户上下滚动列表
- **Then**: 滚动帧率保持 ≥50fps，无明显卡顿（Paging 分页 + LazyColumn key 稳定性）
- **Verification**: `programmatic`（PagingSource 测试）+ `human-judgment`（真机滚动测试）

### AC-9: 网络错误友好提示
- **Given**: 网络不可用或 API 返回非 200
- **When**: 用户发送消息
- **Then**: 显示可理解的中文错误提示（如"网络连接失败"、"API Key 无效"），不崩溃
- **Verification**: `human-judgment`（手动断网测试）

### AC-10: 合规入口
- **Given**: 设置页已打开
- **When**: 用户浏览
- **Then**: 能看到"内容举报"入口链接和隐私政策说明
- **Verification**: `human-judgment`

## Open Questions
- [ ] 账号管理页 MVP 是否直接使用假数据（无后端）？→ 暂定是，仅占位 UI
- [ ] 系统提示词是全局生效还是每个会话独立？→ 暂定全局 + 会话级覆盖
- [ ] 流式 Markdown 是否需要代码块语法高亮？→ MVP 做简单正则匹配着色，后续可集成成熟库
- [ ] P2 增强功能（语音/TTS/导出）是否需要在本次规划中预留接口？→ 预留接口但不实现
- [ ] 默认模型列表应包含哪些预设？→ DeepSeek-V3、DeepSeek-Coder、通用对话模型（GPT-4o Mini）
