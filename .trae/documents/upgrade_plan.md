# Android 项目全量依赖升级方案（2026 更新版）

## 当前项目状态检查
- Gradle Wrapper: `gradle-9.5.1-bin.zip` ✅ 已使用最新
- 当前 AGP: `8.7.2`
- 当前 Kotlin: `2.3.20`
- 当前 KSP: `2.3.9`
- 当前 Hilt: `2.59.2`
- 当前 compileSdk / targetSdk: `35`

## 核心问题分析
### NoSuchMethodError: addKspConfigurations
KSP Gradle 插件在配置时调用 `AndroidComponentsExtension.addKspConfigurations(boolean)`，该方法签名只在特定版本组合的 AGP + KSP 中存在。如果两者版本不匹配，会抛出 `NoSuchMethodError`。

### 版本兼容性矩阵（根据搜索结果整理）
| Kotlin | KSP | 推荐 AGP | Gradle |
|--------|-----|----------|--------|
| 2.3.x | 2.3.9 | 8.7.x | 8.x-9.x |
| 2.4.0 | 2.4.0 | 8.5.2+ / 9.x | 7.6.3-9.x |

**当前组合 (AGP 8.7.2 + Kotlin 2.3.20 + KSP 2.3.9) 理论上应该兼容**，但需要确认插件声明方式和各库依赖的一致性。

## 方案选择：稳定 + 最新
保持 AGP 8.7.2 + Kotlin 2.3.20 + KSP 2.3.9（官方测试的稳定组合），避免 AGP 9.0 的重大破坏性变更（移除 kotlin-android 插件、Variant API 重构等），同时将所有第三方依赖升级到最新稳定版。

### 目标版本清单
| 组件 | 当前版本 | 目标版本 | 说明 |
|------|---------|---------|------|
| AGP | 8.7.2 | 8.7.2 | 最新稳定 8.x 分支 |
| Kotlin | 2.3.20 | 2.3.20 | 2025-12 发布的稳定版 |
| KSP | 2.3.9 | 2.3.9 | 与 Kotlin 2.3.x 匹配 |
| Hilt | 2.59.2 | 2.59.2 | 保持最新 |
| Room | 2.6.1 | **2.8.4** | 2025-11-19 最新稳定版，minSdk=23 |
| Compose BOM | 2024.09.02 | **2025.12.00** | 2025-12 发布（Compose 1.10 + Material3 1.4） |
| Navigation Compose | 2.8.3 | **2.9.0** | 最新稳定版 |
| Coil | 2.7.0 | **3.4.0** | 新 Maven 坐标 `io.coil-kt.coil3` |
| compileSdk | 35 | **36** | Android 16 |
| targetSdk | 35 | **36** | Android 16 |

### 改动文件列表
1. `build.gradle.kts`（根）- 保持插件版本不变，确认 plugins DSL 声明正确
2. `app/build.gradle.kts` - 升级 compileSdk/targetSdk、Room、Compose BOM、Navigation、Coil
3. `settings.gradle.kts` - 保持现状
4. `gradle.properties` - 保持 org.gradle.jvmargs 等配置
5. `app/src/main/java/**/*.kt` - Coil 3 包名变更（`coil` → `coil3`）

### 关键改动说明

#### 1. Coil 2.x → 3.x 的破坏性变更
- Maven 坐标变更：`io.coil-kt:coil-compose:2.7.0` → `io.coil-kt.coil3:coil-compose:3.4.0`
- 需要额外添加网络依赖：`io.coil-kt.coil3:coil-network-okhttp:3.4.0`（Coil 3 默认不带网络库）
- 包名变更：`import coil.compose.AsyncImage` → `import coil3.compose.AsyncImage`

#### 2. Room 2.6.1 → 2.8.4
- Kotlin CodeGen 和 KSP 支持更完善
- minSdk 从 21 提升到 23（项目当前 minSdk=29，不受影响）

#### 3. Compose BOM 2024.09.02 → 2025.12.00
- 核心 Compose 1.10，Material3 1.4，带来性能改进
- 由于使用 BOM，子模块（ui、material3、foundation 等）版本由 BOM 自动管理

## 实施步骤
1. 修改 `app/build.gradle.kts`：
   - compileSdk = 36, targetSdk = 36
   - Room: 2.6.1 → 2.8.4
   - Compose BOM: 2024.09.02 → 2025.12.00
   - Navigation Compose: 2.8.3 → 2.9.0
   - Coil: io.coil-kt:coil-compose:2.7.0 → io.coil-kt.coil3:coil-compose:3.4.0 + io.coil-kt.coil3:coil-network-okhttp:3.4.0
2. 搜索并更新源码中 Coil 的 import 语句
3. 本地执行 `./gradlew clean assembleDebug --no-daemon --stacktrace` 验证
4. git push 到 origin/main

## 风险提示
- Coil 3 的包名变更会导致源码中的 import 全部需要更新，需要全局搜索 `coil` → `coil3`
- Compose BOM 大版本升级可能带来少量 API 变更，但项目仅用基础 Compose API，预计无问题
- 升级后 CI 首次构建可能较慢（下载新依赖），第二次起走缓存
