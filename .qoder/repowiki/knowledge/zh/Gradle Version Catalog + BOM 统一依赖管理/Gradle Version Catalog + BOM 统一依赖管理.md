---
kind: dependency_management
name: Gradle Version Catalog + BOM 统一依赖管理
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
---

## 1. 使用的系统与方案

本项目使用 **Gradle** 作为构建工具，并通过以下机制统一管理第三方依赖：
- **Version Catalog（版本目录）**：`gradle/libs.versions.toml` 集中声明所有库的版本号与别名。
- **BOM（Bill of Materials）**：Compose 相关依赖通过 `androidx.compose:compose-bom` 进行版本对齐。
- **Repository 集中配置**：在根 `settings.gradle.kts` 中通过 `dependencyResolutionManagement` 强制子模块复用仓库源。
- **插件版本集中化**：Android Gradle Plugin、Kotlin、KSP、protobuf 等插件也通过 Version Catalog 的 `[plugins]` 段声明，并在根 `build.gradle.kts` 中以 `apply false` 方式注册。

没有使用任何私有 Maven 仓库或代理；仓库源仅包含 Google、Maven Central 和 Gradle Plugin Portal。

## 2. 关键文件

- `gradle/libs.versions.toml` — 唯一的版本与依赖别名定义中心（65 行）。
- `settings.gradle.kts` — 仓库源与依赖解析模式配置。
- `build.gradle.kts`（根）— 以 `alias(libs.plugins.*) apply false` 注册插件。
- `app/build.gradle.kts` — 唯一业务模块，通过 `implementation(libs.xxx)` 引用依赖。

## 3. 架构与约定

### 3.1 版本集中声明
`libs.versions.toml` 的 `[versions]` 段集中列出所有版本号，例如：
- `kotlin = "2.4.21"`
- `agp = "9.3.3"`
- `composeBom = "2026.09.00"`
- `nav3 = "1.2.0"`
- `room = "2.8.5"`
- `moshi = "1.15.2"`
- `protobuf = "4.36.2"`

### 3.2 依赖别名与分组
`[libraries]` 段为每个坐标定义一个语义化别名（如 `androidx-compose-material3`、`androidx-navigation3-runtime`），模块代码只引用别名而非完整 group:name:version。

### 3.3 Compose 依赖通过 BOM 管理
`app/build.gradle.kts` 中先 `implementation(platform(libs.androidx.compose.bom))`，再引入具体 Compose 组件（`ui`、`material3`、`ui-tooling-preview` 等），由 BOM 自动决定各组件版本，避免手动维护 Compose 生态的版本矩阵。

### 3.4 插件版本同样走 Catalog
`[plugins]` 段声明了 `android-application`、`kotlin-compose`、`kotlin-serialization`、`ksp`、`protobuf` 五个插件及其版本。根 `build.gradle.kts` 用 `alias(libs.plugins.*).apply false` 注册，`app/build.gradle.kts` 再用 `alias(libs.plugins.*)` 启用。

### 3.5 Proto/KSP 集成
- Room 编译器与 Moshi KSP 处理器通过 `ksp(...)` 配置项引入。
- protobuf 插件通过 `protobuf { protoc { artifact = libs.protobuf.protoc.get().toString() } ... }` 从 Catalog 获取 `protoc` 二进制。

### 3.6 仓库源集中管控
`settings.gradle.kts` 中：
- `pluginManagement.repositories` 限定 Android/Google/AndroidX 组名仅从 `google()` 拉取，其余走 `mavenCentral()` 与 `gradlePluginPortal()`。
- `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 禁止子模块自行声明仓库，强制所有依赖解析走根级配置。
- 最终仓库列表仅为 `google()` 与 `mavenCentral()`。

### 3.7 单模块结构
当前工程仅 `include(":app")` 一个业务模块，因此依赖声明集中在 `app/build.gradle.kts`，不存在跨模块共享依赖的问题。

## 4. 约定与约束

- **所有依赖版本必须写在 `gradle/libs.versions.toml` 的 `[versions]` 段**，模块代码通过 `libs.<alias>` 引用，不得在 `build.gradle.kts` 中硬编码版本号。（来源：`libs.versions.toml` 的结构与 `app/build.gradle.kts` 全部使用 `libs.*` 的事实）
- **Compose 生态依赖通过 `platform(libs.androidx.compose.bom)` 引入**，不再单独指定 Compose 组件版本。（来源：`app/build.gradle.kts` 第 62 行）
- **子模块禁止声明自己的仓库源**，否则构建失败（`RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。（来源：`settings.gradle.kts` 第 18 行）
- **Android / Google / AndroidX 组名的插件与库仅从 `google()` 仓库解析**，由 `pluginManagement` 中的 `includeGroupByRegex("com\.android.*" | "com\.google.*" | "androidx.*")` 强制。（来源：`settings.gradle.kts` 第 3–8 行）
- **插件版本与 Kotlin/AGP 版本均通过 Catalog 管理**，根 build 文件仅做 `apply false` 注册，不在子模块重复声明版本。（来源：根 `build.gradle.kts` 与 `libs.versions.toml` 的 `[plugins]` 段）
- **Proto 生成任务通过 Catalog 提供的 `libs.protobuf.protoc` 获取 protoc 二进制**，避免本地环境差异。（来源：`app/build.gradle.kts` 第 44–46 行）
- **Room 与 Moshi 的代码生成注解处理器通过 `ksp(...)` 引入**，而非 `annotationProcessor`。（来源：`app/build.gradle.kts` 第 77、80 行）

未观察到私有仓库、vendor 目录、锁文件或 CI 自动升级脚本；依赖更新完全由人工修改 `libs.versions.toml` 完成。