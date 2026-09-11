# Compose 与 Kotlin 协程 · 开发手册

> 面向本工程的「组件全局清单 + 选型速查」。日后开发时当手册查：需要什么组件、来自哪个依赖、典型写法、坑在哪。
> 版本均以本工程实测为准（见下表），Compose 侧版本由 **BOM 统一管理**，升级只改 BOM 一处即可。

---

## 0. 依赖与版本（本工程实测）

### 0.1 构建插件与关键版本（`gradle/libs.versions.toml`）

| 项 | 版本 | 说明 |
| --- | --- | --- |
| AGP（`com.android.application`） | `9.3.2` | Android Gradle Plugin |
| Kotlin（`org.jetbrains.kotlin.plugin.compose`） | `2.4.10` | Compose 编译器插件随 Kotlin 版本走 |
| Compose BOM | `2026.09.00` | 统一约束所有 `androidx.compose.*` 版本 |
| ├─ compose ui / foundation / animation / runtime | `1.12.1` | BOM 解析结果 |
| └─ compose material3 | `1.4.0` | BOM 解析结果 |
| `androidx.lifecycle:*` | `2.11.0` | runtime-ktx / runtime-compose / viewmodel(-ktx) 等 |
| `androidx.activity:activity-compose` | `1.13.0` | `setContent` / `rememberLauncherForActivityResult` |
| `androidx.core:core-ktx` | `1.19.0` | KTX 扩展 |
| `androidx.datastore:datastore-preferences` | `1.2.1` | 键值持久化（读为 Flow） |
| kotlinx-coroutines | 传递引入（约 `1.9.x+`） | 由 lifecycle/compose 带入，一般无需显式声明 |
| compileSdk / targetSdk / minSdk | `37 / 37 / 24` | |
| JVM 目标 | Java 11 | `compileOptions` |

> **BOM 心智**：`implementation(platform(libs.androidx.compose.bom))` 之后，所有 `androidx.compose.*`
> 依赖都**不写版本号**，由 BOM 统一决定。想升级 Compose 只改 `composeBom` 一行。

### 0.2 本工程已引入的依赖（`app/build.gradle.kts`）

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))          // Compose BOM
    implementation(libs.androidx.activity.compose)               // setContent / edge-to-edge
    implementation(libs.androidx.compose.material3)              // Material3 组件
    implementation(libs.androidx.compose.ui)                     // Compose UI 核心
    implementation(libs.androidx.compose.ui.graphics)            // 图形/绘制
    implementation(libs.androidx.compose.ui.tooling.preview)     // @Preview
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)          // repeatOnLifecycle / flowWithLifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)      // collectAsStateWithLifecycle
    implementation(libs.androidx.datastore.preferences)          // DataStore
    implementation(libs.androidx.compose.material.icons.extended)// 扩展图标 Icons.Filled.*
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)        // 布局检查器/预览渲染
}
```

> **常见缺失（本工程尚未引入，按需在 §C/§E 补）**：`lifecycle-viewmodel-compose`（ViewModel）、
> `navigation-compose`（导航）、`compose.foundation`（多数经 material3 传递，用到 `FlowRow` 等可显式加）、
> Hilt/Room/Retrofit/Coil/kotlinx-serialization 等生态库。

---

# Part A · Kotlin 协程 & Flow

协程解决「异步、并发、取消、异常」；Flow 解决「随时间到达的多值数据流」。二者是 Compose 数据层的基座。

## A1. 协程核心构件

| 组件 | 作用 | 典型来源 / 写法 |
| --- | --- | --- |
| `CoroutineScope` | 协程的作用域，承载生命周期与上下文 | `viewModelScope` / `lifecycleScope` / `rememberCoroutineScope()` / `CoroutineScope(...)` |
| `CoroutineContext` | 上下文元素集合（调度器+Job+异常处理+名字） | `Dispatchers.IO + SupervisorJob() + handler` |
| `Job` | 代表一个协程，可 `cancel()` / `join()` / 查 `isActive` | `launch` 返回 `Job`；`async` 返回 `Deferred` |
| `SupervisorJob` | 子协程失败**不**牵连兄弟/父级 | `SupervisorJob()`；`supervisorScope { }` |
| `CoroutineExceptionHandler` | 兜底捕获未处理异常（仅对 `launch` 生效） | `CoroutineExceptionHandler { _, e -> log(e) }` |
| `Dispatchers.Main` | 主线程（UI）；`immediate` 变体避免多一次调度 | `Dispatchers.Main` / `Dispatchers.Main.immediate` |
| `Dispatchers.IO` | IO 密集（网络/磁盘/DB） | `withContext(Dispatchers.IO) { }` |
| `Dispatchers.Default` | CPU 密集（解析/排序/计算） | `flowOn(Dispatchers.Default)` |
| `Dispatchers.Unconfined` | 不切线程（测试/特殊场景，业务慎用） | — |
| `CoroutineName` | 给协程起名，便于日志/调试 | `+ CoroutineName("feed")` |

## A2. 构建器与结构化并发

| 构建器 | 返回 | 用途 |
| --- | --- | --- |
| `launch { }` | `Job` | 启动「即发即忘」协程，不返回结果 |
| `async { }` | `Deferred<T>` | 并发执行并返回结果，`await()` 取值 |
| `withContext(ctx) { }` | 块结果 | **切线程**执行一段并等待结果（不新建并发） |
| `coroutineScope { }` | 块结果 | 结构化子作用域：任一子失败则整体失败 |
| `supervisorScope { }` | 块结果 | 同上但子失败互不牵连 |
| `runBlocking { }` | 块结果 | **阻塞当前线程**跑协程；仅 `main`/测试用，禁止上主线程 |

**结构化并发（Structured Concurrency）**：子协程绑定父作用域，父取消→子全取消；父等所有子完成才完成。
好处是**不泄漏、可级联取消**。Compose/Android 中优先用框架给的作用域，**避免 `GlobalScope`**。

| 作用域 | 来自 | 生命周期 |
| --- | --- | --- |
| `viewModelScope` | `lifecycle-viewmodel-ktx` | ViewModel 清空时取消 |
| `lifecycleScope` | `lifecycle-runtime-ktx` | Lifecycle 销毁时取消 |
| `rememberCoroutineScope()` | `androidx.compose.runtime` | 该组合离开时取消（Compose 首选） |

## A3. 取消、超时、异常

| 能力 | API |
| --- | --- |
| 取消作用域/任务 | `job.cancel()` / `scope.cancel()` |
| 协作式检查取消 | `isActive` / `ensureActive()` / `yield()`（挂起点自动响应取消） |
| 取消原因 | `CancellationException`（**正常取消**，不要吞掉它） |
| 不可取消段 | `withContext(NonCancellable) { }`（收尾/落盘） |
| 超时 | `withTimeout(ms) { }`（超时抛异常） / `withTimeoutOrNull(ms) { }`（超时返回 null） |
| 异常传播 | `async` 的异常在 `await()` 抛出；`launch` 的异常交给 `CoroutineExceptionHandler`/父级 |

> 坑：`delay`/`emit` 等挂起函数在协程被取消时会抛 `CancellationException`；
> **不要**用 `try/catch(Exception)` 把它吞掉，否则取消失效。需要时单独 `catch (e: CancellationException) { throw e }`。

## A4. Flow 全家桶

### A4.1 构建（冷流）

| 构建器 | 说明 |
| --- | --- |
| `flow { emit(x) }` | 最通用，块内可调用 suspend（delay/网络/DB） |
| `flowOf(a, b, c)` | 固定几个值 |
| `list.asFlow()` / `(1..n).asFlow()` | 集合/区间转流 |
| `channelFlow { }` | 可在**多个协程/不同上下文**里 `send`，支持并发生产 |
| `callbackFlow { }` | 把**回调式 API**（监听器/广播）桥接成 Flow，`awaitClose { 注销监听 }` |
| `flow { }.asSharedFlow()/asStateFlow()` | 见 A4.4 |

### A4.2 中间操作符（惰性，返回新流）

| 分类 | 操作符 |
| --- | --- |
| 变换 | `map` · `transform` · `filter` · `filterIsInstance` · `filterNot` · `take` · `drop` · `takeWhile` · `dropWhile` |
| 累积 | `scan`（含初值，逐个发） · `reduce`（终止，见下） · `fold`（终止） · `runningFold` |
| 合并 | `combine`（各取最新） · `zip`（一一配对） · `merge` · `flatMapLatest` · `flatMapConcat` · `flatMapMerge` |
| 时间/去重 | `debounce`（`@FlowPreview`） · `sample`（`@FlowPreview`） · `distinctUntilChanged` · `delayEach` |
| 副作用打点 | `onStart` · `onEach` · `onCompletion` · `catch` |
| 线程/背压 | `flowOn`（切上游线程） · `buffer` · `conflate` · `collectLatest` |

### A4.3 终止操作符（触发执行）

`collect { }` · `collectLatest { }` · `toList()` · `toSet()` · `first()/firstOrNull()` · `single()` ·
`reduce { }` · `fold(init) { }` · `count()` · `launchIn(scope)`（把收集挂到某作用域，Compose/ViewModel 常用）

### A4.4 热流：StateFlow / SharedFlow

| | `StateFlow` | `SharedFlow` |
| --- | --- | --- |
| 创建 | `MutableStateFlow(initial)` | `MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)` |
| 只读暴露 | `asStateFlow()` | `asSharedFlow()` |
| 写 | `.value = x` / `update { copy() }` / `tryEmit` | `emit(x)`（挂起） / `tryEmit(x)`（非挂起，返回 Boolean） |
| 当前值 | `.value`（同步读，必有） | 无 `.value`；只有 `replayCache` |
| 去重 | 会（`equals` 相等不发） | 不会 |
| 订阅者数 | — | `subscriptionCount`（StateFlow<Int>） |
| 适用 | **UI 状态** | **一次性事件**（replay=0）/ 多播 |

冷流转热流：

| API | 产出 | 关键参数 |
| --- | --- | --- |
| `stateIn(scope, started, initial)` | `StateFlow` | 需初值 |
| `shareIn(scope, started, replay)` | `SharedFlow` | 可配 replay |
| `SharingStarted` | 启停策略 | `Eagerly`（永不停） / `Lazily`（首次订阅后不停） / `WhileSubscribed(stopTimeout, replayExpiration)`（**无人订阅即停，UI 首选**） |

### A4.5 背压（生产快于消费）

| 策略 | 行为 | 适用 |
| --- | --- | --- |
| 默认 | `emit` 挂起，生产者被消费者拖慢（**天然背压安全**） | 一个都不能丢 |
| `buffer(cap, onBufferOverflow)` | 上下游解耦各自全速；满时 `SUSPEND`/`DROP_OLDEST`/`DROP_LATEST` | 允许积压、削峰 |
| `conflate()` | 忙时只保留最新、丢中间 | 只关心最新状态 |
| `collectLatest { }` | 新值到来取消上一次处理、重新开始 | 只处理最新请求（搜索） |

`BufferOverflow`（`kotlinx.coroutines.channels`）：`SUSPEND` / `DROP_OLDEST` / `DROP_LATEST`。

### A4.6 时间参数用 `Duration`

现代写法优先用 `kotlin.time.Duration`（本工程示例已采用）：
`delay(500.milliseconds)` · `debounce(300.milliseconds)` · `withTimeout(2.seconds)` ·
`import kotlin.time.Duration.Companion.milliseconds/seconds`。

## A5. Compose × 协程/Flow 集成

| API | 来自 | 用途 |
| --- | --- | --- |
| `flow.collectAsState(initial = …)` | `compose.runtime` | 把 Flow 收成 Compose `State`；**朴素版，不感知生命周期** |
| `flow.collectAsStateWithLifecycle(initialValue = …)` | `lifecycle-runtime-compose` | **Compose 首选**：界面不可见自动停收集（详见 A6） |
| `stateFlow.collectAsState()` / `.collectAsStateWithLifecycle()` | 同上 | StateFlow 无需初值 |
| `lifecycle.repeatOnLifecycle(STARTED) { flow.collect {} }` | `lifecycle-runtime-ktx` | 通用（View/Compose 皆可）手动安全收集 |
| `flow.flowWithLifecycle(lifecycle, STARTED)` | `lifecycle-runtime-ktx` | 把生命周期门控做成操作符 |
| `lifecycleOwner.lifecycle.currentStateAsState()` | `lifecycle-runtime-compose` | 把当前生命周期状态收成 Compose State |
| `LaunchedEffect(key) { }` | `compose.runtime` | 进入组合时启动协程，key 变化重启，离开取消 |
| `rememberCoroutineScope()` | `compose.runtime` | 事件回调里 `scope.launch { }`（如 onClick 调 suspend） |
| `produceState(initial, key) { value = … }` | `compose.runtime` | 把非 Flow 的挂起/回调数据源转成 State |
| `snapshotFlow { }` | `compose.runtime` | 把 Compose 快照状态变化转成 Flow（如滚动位置） |
| `rememberUpdatedState(value)` | `compose.runtime` | 长生命周期 lambda 里读取最新值 |

> **参数名坑（本工程版本，已核实源码）**：两者初值参数名**不同**，别混用——
> `collectAsState`（`compose.runtime`）用 **`initial`**；`collectAsStateWithLifecycle`（`lifecycle-runtime-compose`）用 **`initialValue`**。
> **`LocalLifecycleOwner`** 用 `androidx.lifecycle.compose.LocalLifecycleOwner`（`compose.ui.platform` 下的已迁移/弃用）。

---

## A6. 专题：`androidx.lifecycle:lifecycle-runtime-compose`

**定位**：Compose 与 Lifecycle 的官方桥接库，所有 API 都在包 **`androidx.lifecycle.compose`** 下。
它把「Flow 收集、生命周期副作用、点击回调」都变得**生命周期感知**——界面不可见时自动暂停、
回到前台自动恢复，从而省电、省流量、避免泄漏。本工程已引入（见 §0.2），版本随 lifecycle `2.11.0`。

**何时需要它**：只要在 Compose 里消费 `Flow/StateFlow`、或需要「按生命周期做副作用 / 门控回调」，就该用它，
而不是朴素的 `collectAsState` / 手写 `DisposableEffect + LifecycleEventObserver`。

### A6.1 公开 API 一览（2.11.0，已逐个核实 sources）

| API | 签名要点 | 用途 |
| --- | --- | --- |
| `Flow<T>.collectAsStateWithLifecycle` | `(initialValue, lifecycleOwner?, minActiveState = STARTED, context?)` → `State<T>` | 收集**冷/普通 Flow**，必须给 `initialValue` |
| `StateFlow<T>.collectAsStateWithLifecycle` | `(lifecycleOwner?, minActiveState = STARTED, context?)` → `State<T>` | 收集 **StateFlow**，无需初值（用 `.value`） |
| `Lifecycle.currentStateAsState()` | → `State<Lifecycle.State>` | 把当前生命周期状态收成 Compose 状态，随 START/STOP 重组 |
| `LocalLifecycleOwner` | `ProvidableCompositionLocal<LifecycleOwner>` | 取当前 `LifecycleOwner`（新包路径，替代 `compose.ui.platform` 下旧的） |
| `rememberLifecycleOwner` | `(maxLifecycle = RESUMED, parent = LocalLifecycleOwner.current)` → `LifecycleOwner` | 给子树一个**受限**生命周期的 owner（如让子树最高只到 STARTED） |
| `LifecycleEventEffect` | `(event, lifecycleOwner?, onEvent)` | 监听**单个**生命周期事件（如 `ON_RESUME`）触发一次性动作，无需清理 |
| `LifecycleStartEffect` | `(key1…, lifecycleOwner?) { onStopOrDispose { } }` | 进入 `STARTED` 启动、`ON_STOP`/离开组合时清理（成对的 start/stop 副作用） |
| `LifecycleResumeEffect` | `(key1…, lifecycleOwner?) { onPauseOrDispose { } }` | 进入 `RESUMED` 启动、`ON_PAUSE`/离开组合时清理（成对的 resume/pause 副作用） |
| `dropUnlessStarted` | `(block: () -> Unit)` → `() -> Unit` | 包装点击回调：**低于 STARTED 时丢弃**该回调（界面不可见就不响应） |
| `dropUnlessResumed` | `(block: () -> Unit)` → `() -> Unit` | 同上，但门控到 **RESUMED**（防抖：非 resumed 不响应，避免重复点击/重复导航） |

> `minActiveState` 语义：只有当生命周期**至少**处于该状态时才收集上游，跌破即取消。
> 默认 `STARTED`（可见即收集）；要更省电可用 `RESUMED`（有焦点才收集）。
> **不允许**传 `INITIALIZED`（会抛异常），`DESTROYED` 也不允许。

### A6.2 典型用法

```kotlin
// 1) 收集 StateFlow（ViewModel 的 uiState）——最常见
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

// 2) 收集普通 Flow——必须给 initialValue（注意参数名是 initialValue，不是 initial）
val tick by remember { tickFlow() }.collectAsStateWithLifecycle(initialValue = 0)

// 3) 更省电：只在有焦点（RESUMED）时收集
val data by viewModel.data.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

// 4) 把当前生命周期状态显示出来 / 参与逻辑
val lifecycleState = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
Text("当前：${lifecycleState.value}")

// 5) 生命周期副作用：进入 RESUMED 注册、ON_PAUSE 或离开组合时注销
LifecycleResumeEffect(Unit) {
    val listener = registerSensorListener()
    onPauseOrDispose { unregister(listener) }
}

// 6) 只监听单次事件：回到前台刷新一次
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

// 7) 门控点击回调，防止界面在后台/切换动画中被重复触发（常见于导航按钮）
Button(onClick = dropUnlessResumed { navController.navigate("detail") }) { Text("去详情") }
```

### A6.3 与 `lifecycle-runtime-ktx` 的分工

| | `lifecycle-runtime-ktx` | `lifecycle-runtime-compose` |
| --- | --- | --- |
| 面向 | 通用（View / 纯协程） | Compose 专用 |
| 代表 API | `lifecycleScope` · `repeatOnLifecycle` · `flowWithLifecycle` | `collectAsStateWithLifecycle` · `LocalLifecycleOwner` · `Lifecycle{Start/Resume/Event}Effect` · `dropUnless*` · `currentStateAsState` |
| 关系 | 底层机制 | 上层封装（`collectAsStateWithLifecycle` 内部就是 `produceState` + `repeatOnLifecycle`） |

**结论**：Compose 里优先用 `lifecycle-runtime-compose` 的封装（一行搞定、更易读）；
需要在一个协程里收集**多条**流、或在非 Compose 层，才下沉到 `repeatOnLifecycle`。

### A6.4 坑位提醒

- `Flow<T>` 重载**必须**传 `initialValue`；`StateFlow<T>` 重载**不要**传（会解析到另一个重载）。
- `LifecycleStartEffect` / `LifecycleResumeEffect` **必须**提供 `onStopOrDispose` / `onPauseOrDispose` 做清理，
  且建议传 key；只监听「单个事件的一次性动作」时改用更轻的 `LifecycleEventEffect`。
- `dropUnlessStarted/Resumed` 是 `@Composable`，只能在组合期调用（通常直接内联到 `onClick =` 参数处）。
- 别再用 `launchWhenStarted` / `launchWhenResumed`——它们已废弃（在 STOPPED 时只是挂起而非取消，仍占资源），
  用 `repeatOnLifecycle` 或本库的 `collectAsStateWithLifecycle` 取代。

---

# Part B · Jetpack Compose

## B1. 库/依赖分工

| 依赖（artifactId） | 提供什么 |
| --- | --- |
| `androidx.compose.runtime:runtime` | 运行时核心：`@Composable`、`remember`、State、副作用、`snapshotFlow`、重组机制 |
| `androidx.compose.ui:ui` | UI 基元：`Modifier`、布局/绘制/输入、`LocalXxx`、`pointerInput` |
| `androidx.compose.ui:ui-graphics` | 图形：`Canvas` 相关、`Brush`、`Color`、`Path`、`graphicsLayer` |
| `androidx.compose.ui:ui-text` | 文本：`AnnotatedString`、`TextStyle`、`SpanStyle` |
| `androidx.compose.ui:ui-unit` | 单位：`dp` / `sp` / `Dp` / `IntSize` / `Offset` |
| `androidx.compose.foundation:foundation` | 基础组件与交互：`Column/Row/Box`、滚动、`LazyColumn`、`FlowRow`、手势、`BorderStroke` |
| `androidx.compose.foundation:foundation-layout` | 布局：`Row/Column/Box/Spacer/FlowRow`、`Arrangement`、`weight` |
| `androidx.compose.material3:material3` | Material3 组件与设计系统 |
| `androidx.compose.animation:animation` | 动画：`AnimatedVisibility/AnimatedContent/Crossfade`、`animate*AsState` |
| `androidx.compose.material:material-icons-extended` | 扩展图标 `Icons.Filled/Outlined/...` |
| `androidx.compose.ui:ui-tooling` / `ui-tooling-preview` | `@Preview` 与布局检查器（debug） |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | Compose UI 测试 |

## B2. 布局组件

| 组件 | 说明 |
| --- | --- |
| `Column` / `Row` | 纵/横排列；配 `verticalArrangement` / `horizontalArrangement`（`Arrangement.*`）与 `verticalAlignment` / `horizontalAlignment`（`Alignment.*`） |
| `Box` | 层叠；`contentAlignment`；`BoxScope.align/matchParentSize` |
| `Spacer(Modifier.size/weight)` | 占位/弹性间隔 |
| `FlowRow` / `FlowColumn`（`@ExperimentalLayoutApi`） | 自动换行排列 |
| `ConstraintLayout`（`androidx.constraintlayout:constraintlayout-compose`） | 约束布局，复杂相对定位 |
| `LazyColumn` / `LazyRow` | 懒加载列表；`items(list, key = …)`、`item { }`、`contentPadding` |
| `LazyVerticalGrid` / `LazyHorizontalGrid` | 网格；`GridCells.Fixed/Adaptive` |
| `LazyVerticalStaggeredGrid` | 瀑布流 |
| `Scaffold` | Material 脚手架：`topBar/bottomBar/fab/drawer/snackbarHost` + `innerPadding` |
| `Surface` | 带背景/形状/海拔/点击的容器 |

> 列表务必给 `items` 提供稳定 **`key`**：保障重组正确性、支持增删动画（`Modifier.animateItem()`）。

## B3. Material3 组件清单（`androidx.compose.material3`）

| 分类 | 组件 |
| --- | --- |
| 按钮 | `Button` · `OutlinedButton` · `TextButton` · `ElevatedButton` · `FilledTonalButton` · `IconButton` · `FloatingActionButton`（+`ExtendedFloatingActionButton`） |
| 输入 | `TextField` · `OutlinedTextField` · `BasicTextField` · `Checkbox` · `RadioButton` · `Switch` · `Slider` · `RangeSlider` |
| 选择/菜单 | `DropdownMenu`/`DropdownMenuItem` · `ExposedDropdownMenuBox`（`@ExperimentalMaterial3Api`） · `FilterChip`/`AssistChip`/`InputChip`/`SuggestionChip` · `SegmentedButton` |
| 展示 | `Text` · `Icon` · `Card`/`ElevatedCard`/`OutlinedCard` · `Divider`/`HorizontalDivider` · `Badge` · `LinearProgressIndicator`/`CircularProgressIndicator` · `ListItem` |
| 容器/浮层 | `Scaffold` · `Surface` · `AlertDialog` · `Dialog` · `BasicAlertDialog` · `ModalBottomSheet`/`ModalBottomSheet`(adaptive) · `SnackbarHost`/`SnackbarHostState` · `NavigationBar`/`NavigationBarItem` · `NavigationRail` · `NavigationDrawer`/`ModalNavigationDrawer` · `TabRow`/`PrimaryTabRow`/`ScrollableTabRow` + `Tab` · `TopAppBar`/`SmallTopAppBar`/`MediumTopAppBar`/`LargeTopAppBar`（`@ExperimentalMaterial3Api`） |
| 状态/主题 | `MaterialTheme`（`colorScheme`/`typography`/`shapes`） · `lightColorScheme()`/`darkColorScheme()` · `rememberTooltipState`/`TooltipBox` · `PullToRefreshBox`（`@ExperimentalMaterial3Api`） |
| 涟漪/交互 | `ripple()` · `InteractionSource`/`MutableInteractionSource` |

> 进度条新版签名：`LinearProgressIndicator(progress = { 0..1f }, …)`（lambda 版）。
> `TopAppBar` / `BottomSheet` / `PullToRefresh` 等仍是 `@ExperimentalMaterial3Api`，需 `@OptIn`。

## B4. Modifier 系统

- **顺序敏感**：`Modifier.padding(8).background(c)` 与 `background(c).padding(8)` 效果不同（先裁剪后填充 vs 反之）。
- 组合用 `infix then`：`a then b`（**没有**重载 `+`）；`Modifier` 是只读接口，`mutableStateListOf` 不适用。
- 常用：布局 `size/width/height/fillMaxSize/fillMaxWidth/wrapContentSize/padding/offset/aspectRatio/weight(作用域内)`；
  外观 `background/border/clip/shadow/alpha`；交互 `clickable/combinedClickable/pointerInput/scroll/verticalScroll/horizontalScroll/draggable/swipeable`；
  绘制 `graphicsLayer { }`（位移/缩放/旋转/透明度，**不触发重组**）· `drawBehind/drawWithContent/drawWithCache`；
  语义/焦点 `semantics { }/focusable/focusRequester/testTag`；动画 `animateContentSize()/animateItem()`。
- 自定义：`Modifier.Node`（现代高性能写法）或 `composed { }`（有状态修饰符，已不推荐用于无状态场景）。

## B5. 状态管理

| API | 用途 |
| --- | --- |
| `remember { }` | 跨重组保留计算结果（配置变更**不**保留） |
| `remember(key) { }` | key 变化时重算 |
| `rememberSaveable { }` | 跨重组 + 配置变更 + 进程重建保留（需可存 Bundle，或配 `Saver`） |
| `mutableStateOf` / `mutableIntStateOf` / `mutableLongStateOf` / `mutableFloatStateOf` / `mutableDoubleStateOf` | 可观察状态；基本类型用专用版**避免装箱**（本工程示例已用 `mutableIntStateOf`） |
| `mutableStateListOf` / `mutableStateMapOf` | 可观察列表/映射 |
| `by remember { mutableStateOf(x) }` | 属性委托读写（`getValue/setValue`） |
| `derivedStateOf { }` | 由其他状态派生，仅在派生结果变化时触发重组（减少无谓重组） |
| `snapshotFlow { }` | 把状态变化转成 Flow |
| `CompositionLocalProvider(LocalX provides v)` + `LocalX.current` | 隐式向下传值（主题、密度、生命周期等），避免层层传参 |
| `StructuralEqualityPolicy` / `referentialEqualityPolicy` | State 的相等策略 |

**状态托管（Hoisting）与单向数据流（UDF）**：把状态上提到无状态组件之外，组件只接收 `value` + `onValueChange`
（Stateless ↔ Stateful）。ViewModel 持状态、经 StateFlow 下发、UI 上报事件，是标准架构。

## B6. 副作用 API

| API | 时机 |
| --- | --- |
| `LaunchedEffect(key)` | 进入组合启动协程；key 变则取消旧的再起；离开取消 |
| `DisposableEffect(key)` | 需要 `onDispose { }` 清理（注册/注销监听、观察者） |
| `SideEffect` | 每次成功重组后执行（把 Compose 状态同步给非 Compose 对象） |
| `rememberCoroutineScope()` | 拿到作用域，在**回调**里 `launch`（onClick 等） |
| `rememberUpdatedState(value)` | 长生命周期 lambda 内读取**最新**值 |
| `produceState(initial, key)` | 把挂起/回调数据源转成 State |
| `derivedStateOf` | 见 B5 |

## B7. 动画 API（三层心智）

| 层 | API | 用途 |
| --- | --- | --- |
| 值动画 | `animateFloatAsState` / `animateColorAsState` / `animateDpAsState` / `animateOffsetAsState` / `animateIntAsState` / `animateSizeAsState` / `animateRectAsState` | 一个目标值自动补间 |
| 状态机 | `updateTransition(targetState)` + `transition.animateXxx` | 一个状态驱动多值同步过渡；`transitionSpec` 定制 |
| 组件级 | `AnimatedVisibility`（enter/exit） · `AnimatedContent` · `Crossfade` · `animateContentSize()` · `LazyItemScope.animateItem()` | 框架托管进出场/内容替换/尺寸/列表增删 |
| 无限 | `rememberInfiniteTransition()` + `animateFloat/animateColor` · `infiniteRepeatable` | 常驻循环动画 |
| 底层手势 | `Animatable` + `snapTo/animateTo/animateDecay/updateBounds` · `VelocityTracker` · `exponentialDecay` | 手势驱动、可打断 |
| 规格 | `AnimationSpec`：`tween` / `spring` / `keyframes` / `snap`；`repeatable` / `infiniteRepeatable`；`Easing`（`LinearEasing`/`FastOutSlowInEasing`/`EaseInOut`…） | 定制时长/曲线/重复 |
| 过渡组合 | `EnterTransition`/`ExitTransition`（`fadeIn/fadeOut/slideIn/slideOut/expandIn/shrinkOut`），`fadeIn() togetherWith fadeOut()`（`infix`） | 组合进出场 |

> 性能：每帧位移/缩放/旋转/透明度用 `Modifier.graphicsLayer { }` 或 `offset { }` 的 **lambda 版**，只走绘制阶段、不触发重组。

## B8. 手势与交互

| API | 用途 |
| --- | --- |
| `Modifier.clickable` / `combinedClickable` | 点击/长按/双击 |
| `Modifier.pointerInput(key) { detectTapGestures { } }` | 点击/双击/长按/拖动细节 |
| `detectDragGestures` / `detectDragGesturesAfterLongPress` | 拖动 |
| `detectTransformGestures` | 缩放/旋转/平移（多指） |
| `detectVerticalDragGestures` / `detectHorizontalDragGestures` | 单轴拖动 |
| `Modifier.draggable(orientation, state)` | 方向拖动 |
| `Modifier.swipeable` / `anchoredDraggable`（`AnchoredDraggableState`） | 吸附式滑动（如底部抽屉） |
| `Modifier.scrollable(state, orientation)` | 自定义滚动 |
| `rememberScrollState` / `rememberLazyListState` / `rememberExpandableState` | 滚动/列表状态 |
| `Modifier.nestedScroll(connection/dispatcher)`（`androidx.compose.ui.input.nestedscroll`） | 嵌套滚动协作 |
| `VelocityTracker` / `exponentialDecay` / `SplineBasedDecay` | 惯性滑动 |
| `awaitPointerEventScope` / `awaitFirstDown` / `waitForUpOrCancellation` | 底层指针事件 |

## B9. 图形绘制

`Canvas(modifier) { drawLine/drawRect/drawCircle/drawArc/drawPath/drawImage }` ·
`DrawScope`（`size`/`center`/`drawXxx`） · `Path`（`moveTo/lineTo/cubicTo/close`） ·
`Brush`（`linearGradient/radialGradient/sweepGradient`） · `Color`/`BlendMode`/`Paint` ·
`Shape`（`RoundedCornerShape/CircleShape/CutCornerShape/GenericShape`） · `Modifier.clip/drawBehind/drawWithContent/drawWithCache` ·
`ImageBitmap`/`ImageVector`/`painterResource`。

## B10. 导航

| 方案 | 依赖 | 说明 |
| --- | --- | --- |
| Navigation Compose | `androidx.navigation:navigation-compose` | `NavHost` + `composable(route)` + `rememberNavController` + `NavController.navigate`；支持参数、深链、`SavedStateHandle` |
| Navigation 3（新一代） | `androidx.navigation3:navigation3-*` | 基于场景（Scene）的导航，官方力推的新方向 |
| 轻量自管 | 无 | 如本工程 `MainActivity` 用 `rememberSaveable { Destination? }` + `BackHandler` 手动切换（适合演示/小应用） |

`BackHandler(enabled) { }`（`activity-compose`）拦截系统返回键。

## B11. 主题与设计系统

`ui/theme/` 三件套：`Color.kt`（色板） · `Type.kt`（`Typography`） · `Theme.kt`（`MaterialTheme` +
`lightColorScheme/darkColorScheme` + 动态取色 `dynamicLightColorScheme`（Android 12+） + `WindowInsets` 处理）。
边到边：`enableEdgeToEdge()`（`activity`）+ `Modifier.systemBarsPadding()/safeDrawingPadding()/windowInsetsPadding()`。

## B12. 环境值（`CompositionLocal` / `Local*`）

常用：`LocalContext` · `LocalConfiguration` · `LocalDensity` · `LocalLayoutDirection` ·
`LocalLifecycleOwner`（`androidx.lifecycle.compose`） · `LocalView` · `LocalFocusManager` ·
`LocalSoftwareKeyboardController` · `LocalHapticFeedback` · `LocalClipboardManager` · Material 的 `LocalColorScheme/LocalTypography/LocalContentColor`。

## B13. 性能与稳定性

| 手段 | 说明 |
| --- | --- |
| `@Stable` / `@Immutable` | 标注类型稳定，帮助 Compose **跳过**未变化的重组 |
| 稳定参数 | 传不可变类型（`String/Int/不可变 data class`）；避免把 `List`/lambda 直接传导致不稳定（用 `kotlinx.collections.immutable`） |
| `key(...)` / `items(key=)` | 稳定标识，减少重组、支持动画 |
| `derivedStateOf` | 只在结果变化时重组 |
| `graphicsLayer`/`offset` lambda | 高频变换走绘制阶段 |
| `remember` 昂贵计算 | 避免每次重组重算 |
| 懒加载 | `LazyColumn` 只组合可见项；避免在其中放 `Column` 全量渲染 |
| 重组计数/检查 | Layout Inspector、`Modifier.recomposeHighlighter()`（官方示例）、Baseline Profile |

## B14. 预览与调试

`@Preview(showBackground/showSystemUi/widthDp/heightDp/uiMode/name)` · `@PreviewParameter` + `PreviewParameterProvider` ·
多预览 `@Preview` 重复标注或自定义注解 · `LocalInspectionMode`（判断是否预览态）。

## B15. 测试

| 依赖 | 用途 |
| --- | --- |
| `androidx.compose.ui:ui-test-junit4` | `createComposeRule()` / `createAndroidComposeRule<A>()`；`onNodeWithText/WithTag/WithContentDescription`、`performClick/performTextInput`、`assertIsDisplayed/assertTextEquals` |
| `androidx.compose.ui:ui-test-manifest`（debug） | 提供测试用 `ComponentActivity` |
| `robolectric` + `compose ui test` | JVM 上跑 Compose UI 测试（免设备） |
| `kotlinx-coroutines-test` | `runTest` / `TestDispatcher` / `advanceTimeBy` / `UnconfinedTestDispatcher`（测协程与 Flow） |
| `turbine`（第三方） | Flow 测试利器（`test { awaitItem() }`） |

---


# Part C · 架构与配套生态（按需引入）

> 本工程当前未引入下列库；它们是「Compose + 协程」应用的常见配套件，坐标可直接抄进 `libs.versions.toml`（见 Part E）。
> 版本请查各自最新稳定版，勿照抄下方示例号。

| 领域 | 首选库 | artifact | 关键点 |
| --- | --- | --- | --- |
| ViewModel | AndroidX Lifecycle | `androidx.lifecycle:lifecycle-viewmodel-compose` | `viewModel()` / `hiltViewModel()`；`viewModelScope`；配合 StateFlow |
| 导航 | Navigation Compose / Nav3 | `androidx.navigation:navigation-compose` | 见 B10 |
| 依赖注入 | Hilt（或 Koin） | `com.google.dagger:hilt-android` + `androidx.hilt:hilt-navigation-compose` | `@HiltAndroidApp`/`@AndroidEntryPoint`/`@HiltViewModel` |
| 网络 | Retrofit + OkHttp | `com.squareup.retrofit2:retrofit` · `com.squareup.okhttp3:okhttp` · `logging-interceptor` | 接口 `suspend` 化，返回 `Response<T>`；用 Flow 做流式 |
| 序列化 | kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` + 插件 `org.jetbrains.kotlin.plugin.serialization` | `@Serializable`；替代 Gson/Moshi |
| 本地数据库 | Room | `androidx.room:room-runtime` · `room-ktx` · KSP `room-compiler` | DAO 返回 `Flow<List<T>>`，数据变化自动推 UI |
| 键值存储 | DataStore | `androidx.datastore:datastore-preferences`（本工程已用）/ `datastore`(Proto) | 读为 Flow、写为 suspend `edit { }`；委托须顶层声明 |
| 图片加载 | Coil | `io.coil-kt.coil3:coil-compose`（+`coil-network-okhttp`） | `AsyncImage`；`ImageRequest`；支持 GIF/视频帧 |
| 后台任务 | WorkManager | `androidx.work:work-runtime-ktx` | 可约束、可重试的后台作业 |
| 权限 | Activity Result API | `activity-compose`（已含） | `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` |
| 自适应/大屏 | Material3 Window Size Class | `androidx.compose.material3:material3-window-size-class` | `calculateWindowSizeClass()`；折叠屏/平板布局 |
| 不可变集合 | kotlinx.collections.immutable | `org.jetbrains.kotlinx:kotlinx-collections-immutable` | `ImmutableList` 提升 Compose 稳定性（见 B13） |
| Paging | Paging 3 | `androidx.paging:paging-compose` | `LazyPagingItems` 分页列表 |

**推荐分层架构**：`UI(Compose) ← ViewModel(StateFlow/事件) ← Repository ← DataSource(网络/DB/DataStore)`。
数据层用**冷流/suspend**暴露一次性数据，用 **StateFlow** 暴露可观察状态，用 **SharedFlow(replay=0)** 发一次性事件；
UI 层用 `collectAsStateWithLifecycle` 消费。

---

# Part D · 选型速查 & 常见坑

## D1. 该用哪个？（决策速查）

```
执行一次性异步任务、要结果？                    → async/await 或 suspend + withContext
Fire-and-forget、随作用域取消？                 → launch（viewModelScope/lifecycleScope/rememberCoroutineScope）
切换线程执行一段并等结果？                       → withContext(Dispatchers.IO/Default)
一次性数据（网络/DB 查询/读文件）？              → 冷流 flow { } / suspend 函数
「当前是什么状态」可观察？                       → StateFlow（有初值、去重、可 .value 同步读）
「发生了一件事」通知 UI？                        → SharedFlow(replay = 0) + tryEmit
把冷流升级成多订阅者共享热流？                   → stateIn/shareIn + SharingStarted.WhileSubscribed(5000)
搜索框「输入即搜、取消过期请求」？               → debounce + distinctUntilChanged + flatMapLatest
聚合多个状态源成一份 UI 状态？                   → combine（各取最新）
生产快于消费、旧数据可丢？                       → conflate / collectLatest / buffer（按需）
Compose 里消费 Flow？                           → collectAsStateWithLifecycle（勿用裸 collectAsState）
回调式 API 转 Flow？                            → callbackFlow { awaitClose { 注销 } }
Compose 状态转 Flow？                           → snapshotFlow { }
跨重组保留？rememberSaveable 还是 remember？     → 需扛配置变更/进程重建 → rememberSaveable，否则 remember
基本类型状态？                                  → mutableIntStateOf/mutableFloatStateOf…（避免装箱）
高频位移/缩放/旋转/透明度动画？                  → Modifier.graphicsLayer { } / offset { } lambda
```

## D2. 常见坑清单

**协程**
- [ ] 用 `GlobalScope.launch`（脱离结构化并发，易泄漏）→ 用带生命周期的作用域。
- [ ] `catch(Exception)` 吞掉 `CancellationException` → 取消失效；应放行或单独重抛。
- [ ] 在主线程 `runBlocking` → 卡 UI/ANR；仅 `main`/测试用。
- [ ] 以为 `async` 的异常会立刻抛 → 它在 `await()` 抛；`launch` 的异常走 handler/父级。
- [ ] CPU/IO 任务不切线程 → 用 `withContext(Dispatchers.Default/IO)`。

**Flow / Compose 集成**
- [ ] 用裸 `collectAsState` 收持续流 → 界面不可见仍在跑；改 `collectAsStateWithLifecycle`。
- [ ] 一次性事件放进 StateFlow → 回前台重放最新值、重复弹提示；改 `SharedFlow(replay=0)`。
- [ ] `repeatOnLifecycle { }` 块里写「只做一次」的副作用 → 它会被反复启停，须幂等。
- [ ] 并发下用 `stateFlow.value = ...` 做读改写 → 丢更新；用 `update { copy() }`。
- [ ] 混淆初值参数名：`collectAsState(initial = …)`（compose.runtime）vs `collectAsStateWithLifecycle(initialValue = …)`（lifecycle-runtime-compose）——两者不同（详见 A6）。
- [ ] `debounce/sample` 忘了 `@OptIn(FlowPreview::class)`；`flatMapLatest` 忘了 `@OptIn(ExperimentalCoroutinesApi::class)`。
- [ ] `LocalLifecycleOwner` 用了 `compose.ui.platform` 下的旧包 → 改 `androidx.lifecycle.compose`。

**Compose**
- [ ] `Modifier` 顺序写反（padding/background/clip 效果不同）。
- [ ] `LazyColumn` 不给 `items` 传 `key` → 重组错乱、增删动画失效。
- [ ] 传不稳定参数（`List`/lambda）导致组件无法跳过重组 → 用不可变类型 / `@Immutable` / `derivedStateOf`。
- [ ] 在组合期直接改状态或做副作用 → 用 `LaunchedEffect`/`SideEffect`/`rememberCoroutineScope`。
- [ ] 每帧动画改布局尺寸 → 用 `graphicsLayer`/`offset` lambda 走绘制阶段。
- [ ] 用 `Modifier.composed { }` 写无状态修饰符 → 首选 `Modifier.Node`。

---

# Part E · 依赖坐标速查（可抄进 `libs.versions.toml`）

```toml
[versions]
# —— 本工程现有 ——
agp = "9.3.2"
kotlin = "2.4.10"
composeBom = "2026.09.00"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
coreKtx = "1.19.0"
datastore = "1.2.1"
# —— 按需补充（示例号，请用最新稳定版）——
coroutines = "1.10.2"          # 仅当需要显式声明或用 test 时
navigationCompose = "2.9.7"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
room = "2.7.0"
coil = "3.0.0"
workManager = "2.10.0"
immutableCollections = "0.3.8"
paging = "3.3.2"

[libraries]
# —— Compose（版本交给 BOM，不写 version）——
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-animation = { group = "androidx.compose.animation", name = "animation" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-window-size = { group = "androidx.compose.material3", name = "material3-window-size-class" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

# —— 协程 ——
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# —— 生命周期 / ViewModel ——
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# —— 数据 / 网络 / DI / 图片 等 ——
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "immutableCollections" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "<对应 Kotlin 的 KSP 版本>" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

> 用到 Room/Hilt 需引入 **KSP**（`com.google.devtools.ksp`）并在 `plugins { }` 应用；KSP 版本要与 Kotlin 版本匹配。

---

## 附：本工程内的活教材（对照阅读）

| 目录 | 覆盖 |
| --- | --- |
| `flow/` | Flow 全家桶：冷流/操作符/StateFlow/SharedFlow/背压/生命周期收集（本手册 Part A 的可运行示例） |
| `animation/` | 动画三层：值动画/组件级/Animatable + AnimationSpec（Part B7） |
| `nestedscroll/` | 嵌套滚动协议、dispatcher、同向/正交、Material3 协作（Part B8） |
| `keywords/` | Compose DSL 背后的 Kotlin 关键字：operator/infix/invoke/inline/crossinline/noinline |
| `delegates/` | 属性委托：`by` / provideDelegate（`by remember` 的底层机制） |
| `sealedtypes/` `variance/` | 密封类型、型变（数据建模基础） |
| `datastore/` | DataStore：Flow 在数据层的真实用例（读为 Flow、写为 suspend） |
| `screenrecord/` | 前台 Service + 协程 + 系统 API 综合 |
