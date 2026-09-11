# Kotlin 协程 · Flow 全家桶（Flow / StateFlow / SharedFlow / 操作符 / 背压 / 生命周期收集）

本目录用 **6 个可交互示例**，把 Flow 全家桶串成一条**真实业务主线**讲清楚：
从一次网络请求出发，逐步加上检索、UI 状态管理、事件通知、高频数据处理，
最后落到与界面生命周期安全协作。它不是孤立的 API 罗列，而是「一个功能如何一步步长出来」。

Flow 是 Kotlin 协程生态的**异步数据流**标准，也是 Compose 世界里连接「数据层 ↔ UI 层」的主干道：
Repository 用 Flow 暴露数据、ViewModel 用 StateFlow/SharedFlow 承载状态与事件、Compose 用
`collectAsStateWithLifecycle` 安全消费。理解 Flow，就理解了现代 Android 的数据流转方式。

依赖版本（本项目实际使用）：

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Kotlin | `2.2.10` | 协程 / Flow 语言支持 |
| Compose BOM | `2026.02.01` | `collectAsState`、Compose 运行时 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.10.0` | `repeatOnLifecycle` / `flowWithLifecycle` |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.10.0` | `collectAsStateWithLifecycle`（**本次新增依赖**） |

> ⚠️ **版本提醒（核实结论）**
> - 本项目 Compose 版本里，`collectAsState` 与 `collectAsStateWithLifecycle` 的初值参数名是 **`initial`**（不是旧教程的 `initialValue`）。
> - `debounce` / `sample` 仍是 `@FlowPreview`，`flatMapLatest` 仍是 `@ExperimentalCoroutinesApi`——使用时需 `@OptIn`。
> - `LocalLifecycleOwner` 用 `androidx.lifecycle.compose.LocalLifecycleOwner`（`androidx.compose.ui.platform` 下那个已弃用/迁移）。

---

## 一、怎么跑起来

不需要任何额外配置，直接运行 App：

```
MainActivity
  └─ ComposeUpTheme
       └─ ComposeUpRoot            ← 主页面 = Topic 列表（点击跳转，返回键回列表）
            ├─ Home          ComposeUpApp()          原示例 App
            ├─ NestedScroll  NestedScrollHub()       嵌套滑动进阶
            ├─ Animation     AnimationHub()          动画进阶
            ├─ Keywords      KeywordsHub()           Compose 底层关键字
            ├─ Flow          FlowHub()               ← 本模块（Icons.Filled.Timeline）
            ├─ Settings      SettingsScreen()        DataStore 设置页
            └─ ScreenRecord  ScreenRecordHub()       录屏演示
```

主页面点击 **Flow 全家桶** 进入 `FlowHub`，再点击列表里任意一项进入具体示例；
用系统返回键或左上角箭头逐级退回。

> Flow 的核心行为都发生在**时间轴**上（何时 emit、何时被 collect、缓冲是否溢出、
> 生命周期是否暂停收集），静态 UI 表达不了。因此本模块统一用 `LogBox` 把每一次事件
> **按时间顺序打印**到屏幕上（最新在上），让「看不见的流」变得可见；每个示例都可交互重跑。

---

## 二、核心心智模型

### 2.1 冷流 vs 热流：一切区别的来源

| | 冷流（Flow） | 热流（StateFlow / SharedFlow） |
| --- | --- | --- |
| 何时产生数据 | **被 collect 时**才开始，每次 collect 从头再来 | 独立于 collect 存在，持续/按需发出 |
| 多个订阅者 | 各自独立跑一遍（互不影响） | **共享**同一份数据源 |
| 有无当前值 | 无（不 collect 就没有值） | 有（StateFlow 必有；SharedFlow 可选 replay） |
| 典型用途 | 一次性任务：网络请求、DB 查询、读文件 | 长期状态 / 事件：UI 状态、传感器、消息总线 |

一句话记忆：

> **冷流像「点播」——你按一次播放它才从头演一遍；热流像「直播」——它一直在播，谁来看都能看到当前画面。**
> `stateIn` / `shareIn` 就是把「点播」升级成「直播」的开关。

### 2.2 StateFlow vs SharedFlow：状态 vs 事件

| | StateFlow | SharedFlow |
| --- | --- | --- |
| 初始值 | **必须**有 | 可以没有 |
| 去重 | 会（`equals` 相等不再发） | **不会**（每个值都发） |
| replay 可配 | 固定为 1（只留最新） | 0..N 任意 |
| 同步读 `.value` | ✅ | ❌（只有 `replayCache`） |
| 适合 | 「当前**是什么**」：登录态、表单、列表 | 「发生了**一件事**」：弹提示、导航、播放动画 |

> 经典结论：**UI 状态用 StateFlow，一次性事件用 SharedFlow(replay=0)**。
> 事件若用 StateFlow，切回界面会因「重放最新值」而把已消费的提示再弹一次；
> SharedFlow(replay=0) 的新订阅者不补收历史，天然避免这个问题（见示例④）。

### 2.3 一条 Flow 的三段式

```
构建（flow{}/flowOf/asFlow） → 变换（中间操作符，惰性） → 收集（终止操作符，触发执行）
```

中间操作符（`map/filter/debounce/...`）只是「描述」要做什么，返回新流、不执行；
只有终止操作符（`collect/toList/first/...`）才真正按下开始键。

---

## 三、文件结构与职责

```
flow/
├── FlowHub.kt              入口：话题列表 + 二级页面切换（含 BackHandler）
├── FlowSources.kt          各示例共享的「假数据源」：articleFeed / searchArticles / sensorStream
├── ColdFlowDemo.kt         示例①：冷流基础 —— 三段式、emit、onStart/onEach/onCompletion/catch、flowOn、终止操作符
├── OperatorsDemo.kt        示例②：操作符 —— 搜索框 debounce+distinctUntilChanged+flatMapLatest、combine、速查表
├── StateFlowDemo.kt        示例③：StateFlow —— 下载进度、update、.value、asStateFlow、stateIn(WhileSubscribed)
├── SharedFlowDemo.kt       示例④：SharedFlow —— 一次性事件、tryEmit、replay=0/1/2 缓存对比
├── BackpressureDemo.kt     示例⑤：背压 —— 默认/buffer/conflate/collectLatest 四策略实时对比
├── LifecycleCollectDemo.kt 示例⑥：生命周期收集 —— collectAsStateWithLifecycle / repeatOnLifecycle / flowWithLifecycle
├── FlowWidgets.kt          包内复用小组件（CodeBlock / LogBox / Stage / ButtonRow / OptionChips / Readout …）
└── README.md               本文件
```

---

## 四、逐个示例的实现方式

### 示例① Flow 基础：冷流与三段式

**真实案例**：模拟一次「网络分页拉取文章标题」。

**实现方式**：`FlowSources.articleFeed()` 是一个 `flow { }` 冷流，内部 `delay(400)` 假装网络耗时、
逐条 `emit`。点「开始拉取」才 `collect`（冷流此刻才执行），用 `onStart/onEach/onCompletion/catch`
在时间轴上打点，全部渲染进 `LogBox`。`flowOn(Dispatchers.Default)` 只把**上游**切到后台线程，
下游回调仍在主线程执行（改 Compose 状态才安全）。另用一组按钮跑 `toList/reduce/first` 演示终止操作符。

```kotlin
fun articleFeed(): Flow<String> = flow {
    titles.forEach { delay(400); emit(it) }   // 冷流：每次 collect 都从头执行
}
articleFeed()
    .flowOn(Dispatchers.Default)              // 只影响上游线程
    .onStart { /* collect 启动 */ }
    .onEach { /* 每收到一个值 */ }
    .catch { e -> /* 捕获上游异常 */ }
    .onCompletion { cause -> /* 结束（catch 已吞异常时 cause=null）*/ }
    .collect { }
```

> **要点**：中间操作符惰性、终止操作符触发执行；`catch` 只管**上游**异常；取消是协作式的。

### 示例② 操作符：搜索框防抖 + combine

**真实案例**：一个「输入即搜」的文章搜索框——日常业务里操作符最经典的组合拳。

**实现方式**：用 `MutableStateFlow<String>` 承载输入，`LaunchedEffect` 里把管线跑起来：

```kotlin
queryFlow
    .debounce(300)                    // 停顿 300ms 才继续（过滤连击）
    .distinctUntilChanged()           // 内容没变不重复检索
    .flatMapLatest { q -> searchArticles(q) }  // 新关键词取消上一次的过期检索
    .collect { results = it }
```

`searchArticles()` 内部 `delay(500)` 模拟检索耗时；正因为 `flatMapLatest` 会在**新值到来时取消旧的内层流**，
用户连续输入时不会堆积/闪烁过期请求。下半部分用 `combine` 合并「价格流 × 汇率流」，
体会它「任一流更新即取各自最新值重算」的语义，并附一份操作符速查表与 `combine vs zip` 辨析。

> **要点**：`debounce` 是 `@FlowPreview`、`flatMapLatest` 是 `@ExperimentalCoroutinesApi`，需 `@OptIn`；
> `combine`（各取最新）用于聚合多状态源，`zip`（一一配对、用完即止）用于成对数据。

### 示例③ StateFlow：下载进度 UI 状态

**真实案例**：文件下载进度——典型的「当前是什么状态」。

**实现方式**：一个 `DownloadModel`（等价于 ViewModel）持有 `MutableStateFlow<DownloadState>`，
`asStateFlow()` 只读暴露，改状态一律用 `update { copy(...) }`（原子读改写）。UI 用 `collectAsState()`
消费，值一变即重组。另用 `stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)` 把一条「每秒 +1」的
**冷流**升级成**热 StateFlow**——有订阅者才跑、离开 5 秒后自动停，避免后台空转。还提供按钮同步读 `.value`。

```kotlin
private val _state = MutableStateFlow(DownloadState())
val state: StateFlow<DownloadState> = _state.asStateFlow()   // 私有可写 + 只读暴露
val elapsed = ticker().stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)
_state.update { it.copy(progress = p) }                       // 用 update 而非 .value=
```

> **要点**：`update{}` 是 CAS 原子操作，并发下不丢更新；StateFlow 自带去重（data class 的 `equals` 要正确）；
> `SharingStarted` 三档里 UI 状态首选 `WhileSubscribed`。

### 示例④ SharedFlow：一次性事件 + replay 对比

**真实案例**：Snackbar / 导航 / Toast 这类「发生了一件事」的通知。

**实现方式**：第一段用 `MutableSharedFlow(replay=0, extraBufferCapacity=4, onBufferOverflow=DROP_OLDEST)`
发事件，`tryEmit`（非挂起、立即返回）保证发送方不被阻塞；`replay=0` 让新订阅者**不补收**旧事件。
第二段做**交互对比**：用 `OptionChips` 选 `replay=0/1/2`（`remember(replay)` 重建流），
先 `emit` 几个值填缓存，再「添加新订阅者」，直接观察它能否立刻补收历史（`replayCache`）与订阅者计数。

```kotlin
val events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 4,
    onBufferOverflow = BufferOverflow.DROP_OLDEST)
events.tryEmit("Event#1")           // 非挂起发送
events.subscriptionCount.value      // 当前订阅者数
events.replayCache                  // replay 缓存里的值
```

> **要点**：`onBufferOverflow` 三种：`SUSPEND`（默认，挂起发送方）/`DROP_OLDEST`/`DROP_LATEST`；
> `extraBufferCapacity>0` 或 `DROP_*` 时 `tryEmit` 才不会因缓冲满而失败。

### 示例⑤ 背压：生产快于消费的四种策略

**真实案例**：高频传感器 / 行情推送（生产 50ms/个）遇到慢消费者（处理 150ms/个）。

**实现方式**：同一条 `sensorStream()` 配同一个慢消费者，用 `OptionChips` 切换四种策略重跑，
实时对比「已生产 vs 已消费」计数（差值就是被缓冲/丢弃/取消的量）：

| 策略 | 写法 | 结果 | 适用 |
| --- | --- | --- | --- |
| 默认 | `collect { }` | 生产==消费，一个不丢，但被拖慢 | 一个都不能丢 |
| `buffer` | `.buffer(4, DROP_OLDEST)` | 生产者全速、缓冲满丢最旧 | 允许积压、削峰 |
| `conflate` | `.conflate()` | 忙时只留最新、丢中间 | 只关心最新状态 |
| `collectLatest` | `.collectLatest { }` | 新值取消旧处理、只跑完最后一次 | 只处理最新请求 |

> **要点**：Flow **默认就背压安全**（`emit` 是挂起函数，消费者没准备好生产者自然被挂起），
> 不必像某些响应式库那样担心无界缓冲撑爆内存；`flowOn` 切线程时也会引入内部缓冲 channel。

### 示例⑥ 生命周期收集：三种安全姿势对比

**真实案例**：持续推送的数据源（时钟/定位/行情），界面切后台时应停止收集以省电省流量。

**实现方式**：同屏跑三个「每 500ms +1」的计数做对照——
`collectAsStateWithLifecycle`（安全，后台停）、朴素 `collectAsState`（不安全，后台仍涨）、
手动 `repeatOnLifecycle(STARTED)`（安全）。再用 `LifecycleEventObserver` 把生命周期事件打进 `LogBox`。
**按 Home 键切后台停留几秒再回来**，即可看到安全计数停住、朴素计数继续涨的直观差异。

```kotlin
// (1) Compose 首选
val tick by remember { tickFlow() }.collectAsStateWithLifecycle(initial = 0)
// (2) 通用手动写法：每次回到 STARTED 重启 block，跌破时取消
lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { render(it) } }
// (3) 作为操作符挂在流上
flow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { render(it) }
```

> **要点**：`repeatOnLifecycle` 的 block 会被**反复启停**，内部要放**幂等**逻辑；
> 默认门控 `STARTED`（可见即收集），要更省电可用 `RESUMED`；冷流每次重启都从头执行，
> 若不想重复初始化就用 `stateIn(WhileSubscribed)` 转热流（呼应示例③）。

---

## 五、选型速查

```
一次性任务、每次都要重新执行（网络/DB/文件）？          → 冷流 flow { } / flowOf / asFlow
表示「当前是什么状态」（登录态/表单/列表）？             → StateFlow（有初值、去重、可 .value 读）
表示「发生了一件事」（弹提示/导航/动画）？               → SharedFlow(replay = 0)
需要新订阅者补收最近若干历史？                          → SharedFlow(replay = N)
把冷流升级成多订阅者共享的热流？                        → stateIn / shareIn + SharingStarted.WhileSubscribed
搜索框「输入即搜、取消过期请求」？                       → debounce + distinctUntilChanged + flatMapLatest
聚合多个状态源成一份 UI 状态？                          → combine
生产快于消费、旧数据可丢？                              → conflate / collectLatest / buffer（按需选）
Compose 里消费 Flow？                                   → collectAsStateWithLifecycle（首选，勿用裸 collectAsState）
```

---

## 六、实践清单

**必须做**

- [ ] UI 状态用 `StateFlow`，改状态用 `update { copy(...) }`；对外只 `asStateFlow()` 暴露只读流。
- [ ] 一次性事件用 `SharedFlow(replay = 0)`，发送用 `tryEmit` 并配 `extraBufferCapacity` / `DROP_OLDEST`。
- [ ] Compose 消费流一律 `collectAsStateWithLifecycle`；非 UI 层用 `repeatOnLifecycle(STARTED)`。
- [ ] 由 ViewModel 持有热流，`stateIn/shareIn` 用 `SharingStarted.WhileSubscribed(5000)` 控制上游启停。
- [ ] 搜索/详情类「只跟最新输入」的场景用 `flatMapLatest` 取消过期请求。
- [ ] 用 `flowOn` 把耗时上游切到后台线程；改 UI 状态的下游回调保持在主线程收集。

**必须避免**

- [ ] 用裸 `collectAsState` / `GlobalScope` 收集持续流（界面不可见仍在跑，浪费资源甚至泄漏）。
- [ ] 把「事件」放进 StateFlow（切回界面会重放最新值，导致提示被重复消费）。
- [ ] 在 `repeatOnLifecycle` 的 block 里写「只做一次」的副作用（它会被反复启停，须幂等）。
- [ ] 用 `.value =` 做并发下的读改写（可能丢更新，应用 `update {}`）。
- [ ] 误以为中间操作符会立即执行（它们惰性，只有终止操作符才触发）。
- [ ] 照抄旧教程的 `collectAsStateWithLifecycle(initialValue = ...)`（本项目参数名是 `initial`）。

---

## 七、延伸阅读

- Kotlin 官方文档「Asynchronous Flow」—— 冷流、操作符、背压、状态流的第一手讲解。
- `kotlinx.coroutines.flow` 源码 —— `Flow.kt` / `Builders.kt` / `Operators.kt` / `StateFlow.kt` / `SharedFlow.kt`。
- Android 官方「RepeatOnLifecycle / collectAsStateWithLifecycle」—— 生命周期安全收集的推荐姿势。
- 本项目 `datastore/SettingsDataStore.kt` —— DataStore 的 `data: Flow<Preferences>` 是 Flow 在数据层的真实用例，
  可与本模块对照：`SettingsScreen` 用 `collectAsState`，本模块示例⑥演示了如何升级为 `collectAsStateWithLifecycle`。
