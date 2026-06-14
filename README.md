# ai-chat
一款极简、克制的个人 AI 聊天客户端（Kotlin + Jetpack Compose）。你需要自备一个兼容 OpenAI Chat Completions 协议的 API Key。

## 一、云打包（GitHub Actions 免费）

提交代码到 `main` / `master` 分支或打 `v*` 标签，即会自动触发 GitHub Actions 打包 APK / AAB 并上传到 Actions 产物与 Releases。

### 工作流

| 工作流 | 触发条件 | 产物 |
|--------|----------|------|
| [android-debug.yml](.github/workflows/android-debug.yml) | push / PR 到 `main` | `app-debug.apk` |
| [android-release.yml](.github/workflows/android-release.yml) | push 到 `main` / 打 `v*` tag | `app-release.apk` + `app-release.aab`，并发布到 GitHub Releases |

### Release 签名：在仓库 Settings 添加 Secrets

```
KEYSTORE_BASE64   # cat release.keystore | base64 (全部内容)
KEYSTORE_PASSWORD # 密钥库密码
KEY_ALIAS         # 密钥别名
KEY_PASSWORD      # 密钥密码
```

未配置以上 Secrets 时，Release 构建会自动回退使用 `$HOME/.android/debug.keystore`（debug 签名）。

## 二、本地构建

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建（需要在 gradle.properties 或环境变量中提供签名信息）
./gradlew assembleRelease bundleRelease
```

### gradle.properties 签名变量

```properties
KEYSTORE_FILE=release.keystore
KEYSTORE_PASSWORD=your_store_pass
KEY_ALIAS=your_alias
KEY_PASSWORD=your_key_pass
```

## 三、下载 APK 的方法

1. **Actions 页面下载**：仓库 → Actions → 最新构建记录 → Artifacts → 下载 `app-debug.apk` / `app-release.apk`
2. **Releases 页面下载**：在打 `v*` 标签后，仓库 → Releases → 对应版本 Assets 下载
