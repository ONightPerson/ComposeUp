# 新闻架构实战：分层、UseCase、Repository、单向数据流与状态归一化

本目录用一个可交互的**新闻主页 + 详情页**，演示如何把 Compose 界面与业务、数据管理分开。组织方式延续 `flow` 的「Hub 入口 + 示例代码 + README」，但用 `ui / domain / data` 子目录显式表达分层。

重点不是多写几层转发，而是回答三个问题：**谁拥有数据？谁能修改数据？多个页面如何保持一致？**

## 一、运行入口与交互

运行 App，在 ComposeUp 示例列表中点击 **新闻架构实战**。

```text
ComposeUpRoot
  └─ ArchitectureHub
       ├─ NewsHomeScreen       新闻列表、收藏筛选、刷新
       └─ NewsDetailScreen     文章正文、收藏、返回
```

- 点击新闻卡片进入详情；点卡片右上角图标可直接收藏。
- 在详情页收藏后返回，首页图标和收藏数量同步变化，不需要回传文章。
- 选择「仅看收藏」验证派生列表；取消最后一篇收藏后进入空态。
- 工具栏刷新模拟 700 ms 异步加载，保留旧文章及本地收藏。
- 详情页的系统返回键与工具栏箭头返回新闻主页；主页返回示例列表。

这里的新闻是**本地编写的架构教学内容，不是真实资讯**。不需要网络、API Key、图片服务或新依赖。收藏只存内存，不承诺进程重启后保留。

## 二、文件结构与职责

```text
architecture/
├── ArchitectureHub.kt                 组装依赖、订阅状态、选择页面、处理返回
├── domain/
│   ├── NewsModels.kt                  文章实体、归一化快照、刷新状态
│   ├── NewsRepository.kt              数据访问与修改契约
│   └── NewsUseCases.kt                观察新闻、刷新新闻、切换收藏
├── data/
│   ├── DemoNewsSource.kt              NewsSource 契约与离线延迟数据源
│   └── InMemoryNewsRepository.kt      唯一事实源、实体合并、收藏更新、并发控制
├── ui/
│   ├── NewsUiState.kt                 屏幕状态、派生属性、NewsEvent
│   ├── NewsViewModel.kt               事件分发、状态组合、轻量状态恢复
│   ├── NewsHomeScreen.kt              无业务依赖的新闻主页
│   ├── NewsDetailScreen.kt            无业务依赖的新闻详情页
│   └── NewsWidgets.kt                 说明块、收藏按钮、加载与错误组件
└── README.md
```

单元测试位于 `app/src/test/java/com/example/composeup/architecture/`。

## 三、分层：依赖方向与运行时调用不是一回事

```text
编译期依赖：

ui ──────────→ domain ←────────── data
                    NewsRepository
                         ↑ 实现
               InMemoryNewsRepository

ArchitectureHub：在组合根创建 data 实现，通过构造函数注入 domain UseCase 和 ViewModel。
```

| 层级 | 负责 | 不负责 |
| --- | --- | --- |
| UI / ViewModel | 渲染、接收事件、保存筛选与选择、组合屏幕状态 | 拉取数据源、维护另一份收藏事实 |
| Domain / UseCase | 用用户目标命名操作，定义数据契约 | Compose、Activity、具体数据源 |
| Data / Repository | 唯一事实源、刷新合并、原子更新、并发与 IO 失败处理 | 导航、控件、界面文案 |

`domain` 没有 Android/Compose 依赖，只有 Kotlin 模型和协程流。Repository **接口**属于领域契约，**实现**位于数据层；ViewModel 只依赖 UseCase，不直接创建或调用具体仓库。

### 为什么这里仍然保留三个很薄的 UseCase？

- `ObserveNewsUseCase`：观察新闻快照。
- `RefreshNewsUseCase`：刷新新闻。
- `ToggleBookmarkUseCase`：按文章 ID 切换收藏。

当前业务简单，三者主要是委托，刻意保留是为了展示用户目标和依赖注入边界，**不是要求所有项目都增加转发层**。真实业务有多仓库协调、内容权限或复杂排序规则时，UseCase 才会承载更多业务逻辑。

也没有增加 `BaseViewModel`、通用 `BaseRepository`、泛型 MVI 框架或 DI 框架：这个两页示例用构造注入已经足够。

## 四、Repository 是唯一事实源

`InMemoryNewsRepository` 私有持有 `MutableStateFlow<NewsSnapshot>`，只通过 `asStateFlow()` 暴露只读状态。UI 不直接访问数据源，也不能直接赋值仓库状态。

刷新流程：

1. `Mutex.tryLock()` 防止重复刷新产生并行请求；进行中的请求不排队重跑。
2. 将快照状态更新为 `Loading`，保留已有数据。
3. 数据源返回文章后按 ID 去重，构建实体表和有序 ID 列表。
4. 在 `update` 中读取**此刻最新**的收藏状态，与响应内容合并。
5. 一次发布实体表、列表顺序和 `Ready`，避免订阅者看到半更新快照。

收藏操作同样使用 `update { copy(...) }`，只替换一个 ID 对应的实体。其变换没有外部副作用，即使 CAS 重试也不会重复执行额外操作。

### 为什么不能直接用响应替换整个实体表？

例如请求开始时 A 未收藏，用户在等待期间收藏 A。如果直接写入旧响应中的 `isBookmarked = false`，用户刚完成的操作就会丢失。因此响应只更新文章内容，收藏字段从合并时的当前快照读取，而不是从请求开始时的副本读取。

刷新后的 `feedIds` 代表最新首页顺序，实体表保留曾加载的文章，避免某条新闻退出首页时当前详情立即消失。**「仅看收藏」及数量统计针对当前新闻列表**，不是独立的全量收藏库。本地小样本无需清理；生产环境通常交给数据库与缓存策略管理。

## 五、状态归一化：只存事实，不存副本

### 5.1 数据与页面选择分离

```text
Repository 的 NewsSnapshot
  articlesById = {
    "architecture" → NewsArticle(..., isBookmarked = true),
    "use-case"     → NewsArticle(..., isBookmarked = false)
  }
  feedIds = ["architecture", "use-case"]
  refreshStatus = Ready

ViewModel 的轻量页面状态
  bookmarkedOnly = false
  selectedArticleId = "architecture"
```

不要同时维护 `homeArticles`、`bookmarkedArticles`、`detailArticle` 三份可写文章副本。本例中：

```kotlin
详情 = articlesById[selectedArticleId]
首页 = feedIds.mapNotNull(articlesById::get)
仅收藏 = 首页.filter { it.isBookmarked }
收藏数 = 首页.count { it.isBookmarked }
```

这些都是 `NewsUiState` 的派生属性，没有独立的 `MutableStateFlow`，也不需要手动同步。`NewsUiState` 包装仓库快照用于读取，但不会在 UI 层维护第二套可写实体。

### 5.2 需要维持的不变量

- 一篇新闻在实体表中只有一个 ID 对应的权威记录。
- `feedIds` 无重复且指向已经存在的实体；实体与 ID 列表一起发布。
- 导航只保存 ID，不在路由参数中传整篇可变文章。
- 收藏数量、筛选列表和详情全部从同一个快照派生。
- 刷新失败不删除已有数据；刷新成功不覆盖最新本地收藏。

状态归一化不等于「把所有状态塞进全局单例」。实体属于 Repository；筛选和页面选择属于 ViewModel；列表滚动位置属于 Compose。每类状态都有明确的所有者。

## 六、单向数据流：一次详情收藏如何传播

```text
NewsDetailScreen
  │ onEvent(ToggleBookmark(articleId))
  ▼
NewsViewModel.onEvent
  ▼
ToggleBookmarkUseCase
  ▼
NewsRepository.toggleBookmark
  │ update articlesById[id]
  ▼
StateFlow<NewsSnapshot>
  │ combine(新闻快照, 筛选条件, 当前文章 ID)
  ▼
StateFlow<NewsUiState>
  │ collectAsStateWithLifecycle()
  ▼
NewsDetailScreen / NewsHomeScreen 重新渲染
```

Compose 页面只接收 `state` 和 `onEvent`，不持有 Repository，也不在组合函数中调用业务操作。`NewsEvent` 是界面操作的封闭集合，而不是跨页面广播总线。

打开详情、返回和切换筛选属于轻量页面状态操作，由 ViewModel 更新 `SavedStateHandle`；只有观察、刷新、收藏进入 UseCase。不是每一个 UI 事件都需要穿过所有层。

这里没有用 `SharedFlow(replay = 0)` 发送关键导航动作：当前文章 ID 本身就是可恢复状态，无需消费一次性事件，也没有「后台错过事件」或返回后再次消费的问题。

## 七、状态、生命周期与错误边界

### 7.1 加载不是清空数据

`RefreshStatus` 用 `Idle / Loading / Ready / Failed` 表达互斥阶段；`isLoading` 与 `hasError` 由它派生，不独立赋值。数据与加载阶段是两个维度，因此可以表达「有旧数据且正在刷新」。

| 情况 | 界面行为 |
| --- | --- |
| 首次加载，无文章 | 显示加载指示 |
| 有内容时刷新 | 继续展示内容，同时显示进度条 |
| IO 失败 | 展示重试入口，保留已有文章 |
| 无文章且成功 | 展示暂无新闻与重新加载入口 |
| 收藏筛选无结果 | 提示暂无收藏，可以切回全部新闻 |
| 恢复的详情 ID 不存在 | 加载结束后展示不存在提示，可返回主页 |
| 协程被取消 | 恢复之前的加载阶段并重新抛出取消异常，不伪装成业务失败 |

`DemoNewsSource` 总是成功；失败、空响应及挂起请求由测试中的 `NewsSource` 替身提供，不需要给生产业务接口塞入「模拟失败」参数。Repository 只处理约定的 `IOException` 与协程取消，不吞掉所有编程错误。

### 7.2 状态寿命

- `ArchitectureHub` 通过 `viewModelFactory` 手动注入依赖，工厂仅在创建 ViewModel 时创建一次 Repository。
- 该两页流程共享一个 `NewsViewModel`。当前宿主是 `MainActivity`，因此 ViewModel 实际为 **Activity 作用域**：旋转、退出本示例再进入仍使用同一个内存仓库，直到 Activity 真正销毁。
- `SavedStateHandle` 仅保存 `selectedArticleId` 和 `bookmarkedOnly`。系统恢复进程时重新获取新闻，不向 Bundle 塞全文。
- 进程结束后内存收藏会丢失，即使筛选条件或详情 ID 被系统恢复。不要把配置变更恢复与业务持久化混为一谈。
- 首页滚动位置由 `rememberSaveableStateHolder` 保存，切到详情再返回不必回到列表顶部；详情切换按文章 ID 隔离组合状态。
- ViewModel 使用 `stateIn(WhileSubscribed(5_000))`，UI 使用 `collectAsStateWithLifecycle()`。停止订阅会停止状态组合上游，但不会自动取消显式启动的刷新任务；刷新由 `viewModelScope` 管理，ViewModel 清理时取消。

仅两个页面时用可保存的选择 ID 足以表达导航，不需要为了架构示例绑定 Navigation 3。将来接入导航框架，也应只传 ID，并为新闻流程明确设置 ViewModel/Repository 作用域。

## 八、测试与手动验收

使用工程已有的 JUnit4 与协程依赖，不引入新的测试框架：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.composeup.architecture.*'
./gradlew :app:assembleDebug
```

测试重点是可观察行为，而不是验证某个内部函数调用次数：归一化、加载与失败状态、请求中的收藏、取消后可重试、重复刷新、无效 ID，以及首页/详情/筛选派生结果一致。

手动验收：

1. 从示例列表进入新闻首页，等待五篇新闻出现。
2. 进入首篇详情，收藏后返回：图标已收藏，收藏数为 1。
3. 切到仅看收藏，再进入详情取消收藏，返回应显示空态；切回全部仍有五篇。
4. 收藏任意新闻并刷新，确认文章不闪空、收藏不丢失；刷新期间继续切换收藏。
5. 从列表下方打开详情再返回，确认首页滚动位置保留。
6. 详情中旋转或切换到后台再返回，确认文章与收藏状态仍一致。
7. 确认详情返回主页、主页返回示例列表，其他主题入口不受影响。

## 九、如何演进为真实业务

本例刻意不包含服务端接口、数据库、分页、登录或离线同步，避免把架构学习变成基础设施堆叠。

真实应用可以让 `NewsSource` 调用网络并映射 DTO，Repository 把响应写入 Room，以数据库查询流作为唯一事实源；收藏独立持久化，合并在事务中完成。届时收藏写入应改为挂起操作，并定义对应失败状态。UI 与 UseCase 的边界仍可沿用，不需要让页面直接感知 Retrofit 或 DAO。

核心设计可以归结为一句话：**实体只存一份，页面只持有引用，操作有统一入口，状态由事实派生。**
