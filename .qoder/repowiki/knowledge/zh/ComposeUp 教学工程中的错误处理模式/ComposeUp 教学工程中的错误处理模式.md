---
kind: error_handling
name: ComposeUp 教学工程中的错误处理模式
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/com/example/composeup/architecture/data/InMemoryNewsRepository.kt
    - app/src/main/java/com/example/composeup/datastore/data/repository/PreferencesRepository.kt
    - app/src/main/java/com/example/composeup/datastore/data/local/datastore/UserProfileSerializer.kt
    - app/src/main/java/com/example/composeup/datastore/data/local/datastore/ProtoSettingsSerializer.kt
    - app/src/main/java/com/example/composeup/screenrecord/MediaStoreUtils.kt
    - app/src/main/java/com/example/composeup/flow/ColdFlowDemo.kt
---

## 1. 总体方式

本仓库是一个 Compose + Kotlin 教学示例集合，没有统一的错误处理框架、自定义异常类型或全局中间件。错误处理以**分散的、按场景采用的传统 Kotlin/Android 惯用法**呈现：`try/catch`、Flow 的 `catch` 操作符、DataStore 的 `CorruptionException`、以及 Android 的 `RecoverableSecurityException`。仓库中没有定义任何自定义 `sealed class` 错误类型或 `Result` 包装器（仅在注释中出现 `State<Result<Image>>` 的示例写法），也没有 panic/recover 语义（Kotlin 无 panic）。

## 2. 关键文件与位置

- **架构层数据源**：`app/src/main/java/com/example/composeup/architecture/data/InMemoryNewsRepository.kt`
- **DataStore 序列化器**：`UserProfileSerializer.kt`、`MoshiUserProfileSerializer.kt`、`GsonUserProfileSerializer.kt`、`ProtoSettingsSerializer.kt`
- **Preferences Repository**：`app/src/main/java/com/example/composeup/datastore/data/repository/PreferencesRepository.kt`
- **屏幕录制 MediaStore 工具**：`app/src/main/java/com/example/composeup/screenrecord/MediaStoreUtils.kt`
- **Flow 异常演示**：`app/src/main/java/com/example/composeup/flow/ColdFlowDemo.kt`

## 3. 观察到的约定与模式

### 3.1 try/catch + 状态回退（Repository 层）

`InMemoryNewsRepository.refresh()` 使用 `try/catch/finally` 包裹网络拉取逻辑：捕获 `CancellationException` 时恢复之前的 `RefreshStatus` 并重新抛出；捕获 `IOException` 时将状态置为 `RefreshStatus.Failed`；`finally` 中释放 `Mutex`。这是一种“用 UI 状态表示错误”的模式，而非抛出业务异常。

### 3.2 Flow 的 `catch` 操作符用于流式异常处理

`PreferencesRepository.userPreferencesFlow` 通过 `.catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }` 将 DataStore 读取失败降级为空偏好，其他异常继续上抛。`ColdFlowDemo` 在 `onStart/onEach/onCompletion/catch` 链中使用 `catch` 记录异常日志，并在 `onCompletion` 中根据 `cause` 是否为 null 区分正常结束与异常结束——注释明确说明“catch 已吞掉异常，所以这里 cause 通常为 null”。

### 3.3 DataStore Serializer 的容错策略分化

三种 JSON 类 Serializer（`UserProfileSerializer`、`MoshiUserProfileSerializer`、`GsonUserProfileSerializer`）在 `readFrom` 中统一采用 `try { decode } catch (_: Exception) { return defaultValue }` 的策略：解析失败直接返回默认值，不向上抛出。

而 `ProtoSettingsSerializer` 则不同：捕获 `InvalidProtocolBufferException` 后显式包装为 `CorruptionException("Cannot read proto.", exception)` 抛出，遵循 DataStore 对数据损坏的语义化异常约定。

### 3.4 Android 权限异常的分级处理

`MediaStoreUtils.deleteVideos()` 和 `deleteVideo()` 针对 Android 10+ 的 `RecoverableSecurityException` 做了分支处理：若可恢复则返回 `IntentSender` 让调用方弹出用户授权对话框；否则原样抛出。对于非可恢复的 `SecurityException`，`deleteVideo` 返回 `false`，`deleteVideos` 则抛出异常——同一工具类内对不同 API 采用了不同的失败语义（返回值 vs 异常）。

### 3.5 协程取消的正确传播

`InMemoryNewsRepository` 显式捕获 `CancellationException` 并重新抛出，避免协程取消被吞掉；`ColdFlowDemo` 的注释也强调“取消是协作式的：cancel 协程会让上游 flow 在下一个挂起点停止”。

## 4. 约束与规则

- 仓库未定义任何自定义错误类型（如 `sealed class AppError`）、错误码枚举或统一的 `Result<T>` 包装器——所有错误均以原生异常或 UI 状态表达。
- DataStore 的 `Serializer.readFrom` 必须返回 `defaultValue` 作为解析失败的兜底（三个 JSON Serializer 均如此实现）。
- DataStore 数据损坏应使用 `CorruptionException`（见 `ProtoSettingsSerializer`），这是 DataStore 框架约定的语义化异常。
- 协程取消必须通过重新抛出 `CancellationException` 来正确传播，不能静默吞掉（见 `InMemoryNewsRepository`）。
- Flow 的 `catch` 仅捕获上游异常，且位于 `catch` 之后的操作符产生的异常不会被捕获（见 `ColdFlowDemo` 注释与代码顺序）。

## 5. 结论

该仓库没有跨模块的统一错误处理体系；错误处理是**按 Demo 场景各自实现**的教学性代码，主要体现为：Repository 层用状态机表示成功/失败、DataStore 序列化器用默认值降级、Flow 用 `catch` 操作符收集异常、Android 权限异常按是否可恢复分流。这些模式本身即为仓库要演示的知识点，而非一个需要复用的基础设施。