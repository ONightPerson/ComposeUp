---
kind: configuration_system
name: Gradle 版本目录 + Android Gradle 配置体系
category: configuration_system
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
    - gradle.properties
---

## 概述

本仓库是一个单模块（`:app`）的 Compose 教学工程，不存在运行时应用配置系统（无 `application.properties`、`.env`、feature flag 或远程配置）。其“配置”集中在 **构建期**，通过 Gradle 版本目录（Version Catalog）统一管理插件与依赖版本。

## 关键文件

- `gradle/libs.versions.toml` — 版本目录中心，集中声明所有版本号与 library alias。
- `settings.gradle.kts` — 根项目设置：仓库源、pluginManagement、dependencyResolutionManagement。
- `build.gradle.kts`（根）— 顶层仅声明 `apply false` 的插件别名，不引入任何子模块公共逻辑。
- `app/build.gradle.kts` — 唯一业务模块的 AGP / protobuf / dependencies 配置。
- `gradle.properties` — Gradle JVM 参数、Configuration Cache、Kotlin code style。

## 架构与约定

1. **版本目录（libs.versions.toml）**
   - `[versions]` 段统一存放 `agp`、`kotlin`、`composeBom`、`room`、`nav3`、`workManager` 等版本号。
   - `[libraries]` 段以 `androidx-*`、`kotlinx-*`、`protobuf-*` 等前缀命名库，并通过 `version.ref = "..."` 引用版本号，避免在 build script 中硬编码字符串。
   - `[plugins]` 段将 `com.android.application`、`org.jetbrains.kotlin.plugin.compose`、`ksp`、`protobuf` 等插件也暴露为别名。

2. **插件引用方式**
   - 根 `build.gradle.kts` 使用 `alias(libs.plugins.xxx) apply false` 声明全局可用但暂不启用的插件。
   - `app/build.gradle.kts` 使用 `alias(libs.plugins.android.application)` 等直接启用。

3. **仓库与依赖解析策略**
   - `settings.gradle.kts` 中 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 强制禁止子模块自行声明仓库，所有仓库（google、mavenCentral、gradlePluginPortal）集中在根级定义。
   - `pluginManagement.repositories` 对 Google/AndroidX 组名使用 `includeGroupByRegex` 白名单，其余走 mavenCentral。

4. **AGP 与 Kotlin 编译配置**
   - `compileSdk { version = release(37) { minorApiLevel = 1 } }` 使用 AGP 的 `release()` DSL 指定 compileSdk。
   - `minSdk = 24`、`targetSdk = 37`、`JavaVersion.VERSION_11` 固定 Java/Kotlin 兼容级别。
   - `buildTypes.release.optimization.enable = false` 关闭 release 优化以便教学 Demo 调试。
   - `buildFeatures.compose = true` 启用 Compose。

5. **Proto 生成**
   - 通过 `com.google.protobuf` 插件 + `generateProtoTasks` 为每个 proto 任务同时生成 `java` 与 `kotlin` 目标，并开启 `lite` 模式。
   - `protoc.artifact` 指向 `libs.protobuf.protoc`（即版本目录中的 protoc 包）。

6. **Gradle 运行期配置**
   - `gradle.properties` 开启 `org.gradle.configuration-cache=true`，设置 `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`，并声明 `kotlin.code.style=official`。

## 约定与约束

- **版本必须集中在 `gradle/libs.versions.toml`**：所有依赖与插件均通过 `libs.*` 别名引用，build script 中不出现裸版本号（由 Version Catalog 保证一致性）。
- **子模块不得声明仓库**：`RepositoriesMode.FAIL_ON_PROJECT_REPOS` 会在子模块声明仓库时抛出异常，这是硬性约束（由 Gradle 强制执行）。
- **插件版本与 Kotlin/AGP 版本解耦**：插件 ID 与版本分别位于 `[plugins]` 与 `[versions]`，便于统一升级。
- **Compose BOM 管理 Compose 生态版本**：依赖声明使用 `implementation(platform(libs.androidx.compose.bom))` 后，Compose 相关库不再写版本号。
- **Room/Moshi 编译器走 KSP**：`ksp(libs.androidx.room.compiler)` 与 `ksp(libs.moshi.ksp)` 替代了传统的 annotation processor。
- **Proto 字段序列化器三选一**：`data/datastore/` 下提供 Gson、Moshi、Proto Settings 三种 UserProfileSerializer 实现，作为 DataStore 持久化格式的对比示例，而非运行时可切换的配置项。