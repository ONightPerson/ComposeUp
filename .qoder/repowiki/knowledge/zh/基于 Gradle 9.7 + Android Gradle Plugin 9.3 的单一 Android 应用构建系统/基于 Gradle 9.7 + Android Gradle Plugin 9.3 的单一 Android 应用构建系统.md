---
kind: build_system
name: 基于 Gradle 9.7 + Android Gradle Plugin 9.3 的单一 Android 应用构建系统
category: build_system
scope:
    - '**'
source_files:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - gradle.properties
    - gradle/wrapper/gradle-wrapper.properties
---

## 1. 使用的构建系统与工具链

- **Gradle 9.7.1**（通过 `gradle/wrapper/gradle-wrapper.properties` 锁定，`distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`）。
- **Android Gradle Plugin (AGP) 9.3.3**，Kotlin 2.4.21，Compose BOM 2026.09.00。
- 使用 **Version Catalog**（`gradle/libs.versions.toml`）集中管理所有依赖与插件版本。
- 使用 **Plugin Aliases**（`alias(libs.plugins.*)`）在根 `build.gradle.kts` 与 `app/build.gradle.kts` 中声明式启用插件。
- 使用 **Foojay Toolchains Resolver**（`org.gradle.toolchains.foojay-resolver-convention` v1.0.0）自动解析 JDK，避免本地 JDK 配置差异。
- 启用 **Gradle Configuration Cache**（`org.gradle.configuration-cache=true`）以提升增量构建性能。
- 仓库源：Google Maven、Maven Central、Gradle Plugin Portal；通过 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 禁止子模块自行声明仓库。

## 2. 关键文件

| 文件 | 作用 |
|---|---|
| `settings.gradle.kts` | 工程入口，声明 pluginManagement 仓库、Foojay toolchain、全局仓库源、项目名 `ComposeUp`，仅 include `:app` 一个模块 |
| `build.gradle.kts`（根） | 以 `apply false` 方式注册 AGP、kotlin-compose、kotlin-serialization、ksp、protobuf 五个插件别名 |
| `gradle/libs.versions.toml` | Version Catalog：集中定义 `agp=9.3.3`、`kotlin=2.4.21`、`composeBOM=2026.09.00`、`nav3=1.2.0`、`room=2.8.5`、`workManager=2.12.0` 等全部版本及 library/plugin 条目 |
| `app/build.gradle.kts` | 唯一业务模块的构建脚本：声明 namespace、compileSdk/targetSdk/minSdk、BuildType、CompileOptions、Compose 开关、protobuf 生成、dependencies |
| `gradle.properties` | JVM args `-Xmx2048m -Dfile.encoding=UTF-8`、开启 configuration cache、Kotlin code style = official |
| `gradle/wrapper/gradle-wrapper.properties` | 锁定 Gradle 分发版本 9.7.1 |
| `gradle/gradle-daemon-jvm.properties` | Gradle Daemon 的 JVM 配置（存在但内容未读取） |

## 3. 架构与约定

### 3.1 单模块结构
当前仓库仅包含一个 Android 应用模块 `:app`（`include(":app")`），所有教学 Demo（Animation、Architecture、CompositionLocal、DataStore、Flow、Navigation3、NestedScroll、Remember、ScreenRecord、SideEffects、WorkManager、Delegates、Variance、Keywords 等）均以包形式组织在 `app/src/main/java/com/example/composeup/<feature>/` 下，而非拆分为独立 Gradle 模块。因此“多模块聚合”的实际形态是：**一个 Gradle 工程 + 一个 app 模块 + 按功能分包的代码**。

### 3.2 版本统一管理
所有第三方库版本集中在 `gradle/libs.versions.toml` 的 `[versions]` 段，并通过 `version.ref = ...` 引用；library 与 plugin 条目统一放在 `[libraries]` 与 `[plugins]` 段。新增依赖时应在 catalog 中添加条目，再由 `app/build.gradle.kts` 通过 `libs.xxx` 引用，避免硬编码版本号。

### 3.3 插件启用方式
根 `build.gradle.kts` 仅做插件别名注册（`apply false`），实际启用集中在 `app/build.gradle.kts`：
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}
```
这保证了五个核心插件（AGP、Compose、Serialization、KSP、Protobuf）的版本来源一致。

### 3.4 Compose 与 Kotlin 编译配置
- `android { buildFeatures.compose = true }` 启用 Compose。
- `compileOptions.sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11`。
- `compileSdk` 使用新版 API：`compileSdk { version = release(37) { minorApiLevel = 1 } }`，`targetSdk = 37`，`minSdk = 24`。
- `release` BuildType 显式关闭 R8/ProGuard 优化：`optimization.enable = false`。

### 3.5 Protobuf 代码生成
通过 `com.google.protobuf` 插件，在 `generateProtoTasks` 中为每个 task 同时生成 `java` 与 `kotlin` 两种输出，并均启用 `lite` 模式（对应 `protobuf-kotlin-lite` 依赖）。`protoc` 二进制由 `libs.protobuf.protoc` 提供。

### 3.6 KSP 注解处理器
Room 编译器与 Moshi 代码生成均通过 KSP 接入：
```
ksp(libs.androidx.room.compiler)
ksp(libs.moshi.ksp)
```
而非传统的 annotationProcessor。

### 3.7 测试依赖
- 单元测试：`testImplementation(libs.junit)`。
- 仪器测试：`androidTestImplementation` 引入 `espresso-core`、`androidx.junit`、`androidx.compose.ui.test.junit4`，以及 `debugImplementation(libs.androidx.compose.ui.test.manifest)`。

## 4. 约定与约束

- **仓库源集中管控**：`settings.gradle.kts` 中 `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 强制所有依赖解析走根工程的仓库列表，子模块不得自行添加仓库。
- **插件版本集中管控**：所有插件通过 `alias(libs.plugins.*)` 引用，版本来源统一于 `gradle/libs.versions.toml`。
- **JDK 由 Foojay 自动解析**：通过 `org.gradle.toolchains.foojay-resolver-convention` v1.0.0，无需开发者手动配置 JAVA_HOME。
- **Configuration Cache 已启用**：`org.gradle.configuration-cache=true`，要求构建脚本具备配置缓存兼容性。
- **无 CI/Docker/Makefile**：仓库未发现 `.github/workflows`、`Dockerfile`、`Makefile`、`*.sh` 等 CI 或容器化脚本，发布产物为 APK/AAB（由 AGP 默认产出）。
- **Release 不执行混淆**：`app/build.gradle.kts` 中 `release { optimization { enable = false } }` 明确关闭了 release 构建的代码优化/混淆流程。