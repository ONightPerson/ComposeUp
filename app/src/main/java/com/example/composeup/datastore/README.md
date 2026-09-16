# Android 本地数据持久化选型与分层架构指南

本指南对 Jetpack 架构组件中的 **Preferences DataStore**、**Proto DataStore** 以及 **Room Database** 进行全方位对比，并演示了如何使用 Android 官方推荐的**分层架构 (Layered Architecture)** 进行开发。

---

## 一、 官方推荐架构模型 (Modern Android Architecture)

本模块代码严格遵循 [Guide to App Architecture](https://developer.android.com/topic/architecture) 进行重构：

### 1. UI Layer (用户界面层)
* **Screen (Composable)**: 仅负责订阅 ViewModel 暴露的 `uiState` (StateFlow)，并将用户操作转化为 Action 发送给 ViewModel。
* **ViewModel**: 负责管理 UI 状态，处理业务逻辑转换（如搜索过滤），并通过 `viewModelScope` 跨越配置变更。

### 2. Data Layer (数据层)
* **Repository (仓储)**: 系统的**唯一真实数据源**入口。它协调不同的数据源（Room, DataStore, Network），并将原始数据实体转换为业务模型。
* **Data Source (数据源)**: 具体的底层实现，如 `AppDatabase` (Room)、`UserPreferencesDataStore`、`UserProfileDataStore`。

---

## 二、 持久化组件一览表 (Selection Matrix)

| 特性 / 维度 | Preferences DataStore | Proto DataStore | Room Database |
| :--- | :--- | :--- | :--- |
| **底层存储介质** | 原生文本文件 | 原生二进制文件 | SQLite 关系型数据库 |
| **数据结构模型** | 键值对映射 (Key-Value) | 强类型结构化对象 | 关系型多维表格 |
| **类型安全** | 局部类型安全 | 编译期强类型安全 | 编译期强类型安全与 SQL 校验 |
| **异步流支持** | `Flow<Preferences>` | `Flow<T>` | `Flow<List<T>>` |
| **推荐架构位置** | Data Layer -> DataSource | Data Layer -> DataSource | Data Layer -> DataSource |

---

## 三、 架构设计黄金定律

1.  **数据流向 (Unidirectional Data Flow)**:
    *   **State 向下**: 数据从 Repository -> ViewModel -> Composable。使用 `collectAsStateWithLifecycle()` 确保生命周期安全。
    *   **Action 向上**: 用户点击 -> ViewModel 函数 -> Repository 挂起函数。
2.  **职责解耦**:
    *   **Composable** 不应直接调用 `context.dataStore` 或 `database.dao()`。
    *   **ViewModel** 不应直接处理 `IOException` 或 SQL 原始解析。
    *   **Repository** 是处理原始 I/O 和数据转换的绝佳场所。

---

## 四、 本模块目录结构

```text
datastore/
├── data/
│   ├── model/         # 数据模型 (Entity & Data Class)
│   ├── local/         # 本地数据源 (Room DB, DAO, DataStore Serializer)
│   └── repository/    # 仓储层 (屏蔽具体存储细节)
├── ui/
│   ├── viewmodel/     # ViewModel (持有 UI State)
│   └── screens/       # UI 页面 (Composable)
└── README.md          # 总结说明
```

---

## 五、 避坑与最佳实践总结

*   **Dependency Injection**: 虽然本演示为保持简洁使用了手动注入 (Manual DI)，但在真实生产项目中，强烈建议使用 **Hilt** 来管理 Repository 和 ViewModel 的依赖。
*   **响应式编程**: 充分利用 Room 和 DataStore 的 `Flow` 特性。在 ViewModel 中使用 `flatMapLatest` 或 `combine` 动态响应查询条件的变化，从而实现“零手动刷新”的 UI。
*   **二进制安全**: 在 **Proto DataStore** 中，利用 `kotlinx.serialization` 可以非常优雅地处理复杂的 Kotlin 对象，无需编写繁琐的 `.proto` 文件（除非需要跨语言兼容）。
