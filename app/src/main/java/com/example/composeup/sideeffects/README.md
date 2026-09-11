# Compose 进阶 · Side-effects（副作用）

本目录用 **8 个可交互示例**，逐个讲清 Jetpack Compose 提供的每一种 **副作用（Side-effect）API**。
内容严格对照官方文章 **《Side-effects in Compose》**
（https://developer.android.com/develop/ui/compose/side-effects），
每个示例都**复刻文章里的真实案例**，并按文章顺序编排，方便对照阅读。

> **什么是副作用？** 副作用是「发生在 composable 函数作用域**之外**的状态改变」。
> 由于 composable 的重组是**顺序不定、可能被丢弃、不可预测**的，理想情况下 composable 应当**无副作用**。
> 但有时确实需要副作用（弹 Snackbar、导航、埋点、订阅外部数据源……），
> 这时必须把它们放进**感知 composable 生命周期**的受控环境里执行——这就是 Effect API 存在的意义。
>
> **关键术语**：effect 是一个「不产出 UI、在组合完成后触发副作用」的 composable 函数。

依赖版本（本工程实测，Compose 由 BOM 统一管理）：

| 依赖 | 版本 |
| --- | --- |
| Compose BOM | `2026.09.00` |
| `androidx.compose.runtime` | `1.12.1` |
| `androidx.compose.ui` / `foundation` / `animation` | `1.12.1` |
| `androidx.compose.material3` | `1.4.0` |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.11.0`（`LocalLifecycleOwner`） |

---

## 一、怎么跑起来

不需要任何额外配置，直接运行 App：

```
MainActivity
  └─ ComposeUpRoot            ← 主页面 = Topic 列表（点击跳转，返回键回列表）
       ├─ …
       ├─ Flow          FlowHub()                    Flow 全家桶
       ├─ SideEffects   SideEffectsHub()             ← 本模块（Icons.Filled.Sync）
       ├─ Settings      SettingsScreen()
       └─ ScreenRecord  ScreenRecordHub()
```

主页面点击 **Compose 副作用** 进入 `SideEffectsHub`，再点击列表里任意一项进入具体示例；
用系统返回键或左上角箭头逐级退回。

> 副作用都发生在**时间轴**上（进入组合、重组、离开组合、key 变化……），静态 UI 表达不了。
> 因此本模块和 flow 模块一样，用 `LogBox` 把这些时刻**如实打印**出来（最新在上），让「看不见的时机」可见。

---

## 二、核心心智模型：8 种副作用各解决什么问题

| API | 一句话 | 典型场景 | 官方示例（本模块复刻） |
| --- | --- | --- | --- |
| **`LaunchedEffect`** | 在 composable 作用域里跑 suspend；进入启动、离开取消、key 变重启 | 一次性/长任务、动画、加载 | 按可配置频率闪烁 alpha |
| **`rememberCoroutineScope`** | 拿一个绑定组合位置的 scope，在**回调**里 launch | onClick 里弹 Snackbar、手动取消协程 | MoviesScreen 的 Snackbar |
| **`rememberUpdatedState`** | effect 不重启也能读到**最新值** | 长生命周期 effect 捕获回调/值 | LandingScreen(onTimeout) |
| **`DisposableEffect`** | 需要**清理**的副作用；key 变则 dispose→reset | 注册/注销监听器、观察者 | LifecycleObserver 埋点 |
| **`SideEffect`** | 每次**成功重组后**把状态发布给非 Compose 对象 | 同步用户属性给分析 SDK | rememberFirebaseAnalytics |
| **`produceState`** | 把非 Compose 状态（Flow/回调/suspend）转成 `State` | 网络加载、订阅外部源 | loadNetworkImage |
| **`derivedStateOf`** | 把高频状态派生成「变化更少」的状态，减少重组 | 滚动越过阈值才显示按钮 | MessageList 的 showButton |
| **`snapshotFlow`** | 把 Compose `State` 转成**冷 Flow**，可用操作符 | 滚动位置埋点、状态流式处理 | 越过首项上报分析事件 |

一句话记忆：

> **要在组合里跑协程 → `LaunchedEffect`；在回调里跑协程 → `rememberCoroutineScope`；
> effect 不重启又要最新值 → `rememberUpdatedState`；要清理 → `DisposableEffect`；
> 发布给外部对象 → `SideEffect`；外部数据 → State 用 `produceState`；
> 减少重组 → `derivedStateOf`；State → Flow 用 `snapshotFlow`。**

### 2.1 两条「转换」方向别搞混

```
非 Compose 世界  ──produceState──▶  Compose State      （外部数据进 Compose）
Compose State    ──snapshotFlow──▶  冷 Flow            （Compose 状态出去，用 Flow 操作符）
高频 State       ──derivedStateOf─▶  低频 State         （结果不变就不重组）
```

- `produceState` 与 `snapshotFlow` 互为逆向：一个把外部搬进来变成 State，一个把 State 搬出去变成 Flow。
- `derivedStateOf` 产出 **State**（给 UI 读）；`snapshotFlow` 产出 **Flow**（给协程 collect）。

---

## 三、文件结构与职责

```
sideeffects/
├── SideEffectsHub.kt           入口：话题列表 + 二级页面切换（含 BackHandler）
├── LaunchedEffectDemo.kt        ① LaunchedEffect —— 可配置频率闪烁 alpha（key 变化重启）
├── RememberCoroutineScopeDemo.kt② rememberCoroutineScope —— onClick 弹 Snackbar + 手动启停协程
├── RememberUpdatedStateDemo.kt  ③ rememberUpdatedState —— LandingScreen 计时，正确/错误用法对比
├── DisposableEffectDemo.kt      ④ DisposableEffect —— 注册/注销 LifecycleEventObserver（切前后台可见）
├── SideEffectDemo.kt            ⑤ SideEffect —— 把 userType 发布给 FakeAnalytics（非 Compose 对象）
├── ProduceStateDemo.kt          ⑥ produceState —— loadNetworkImage：Loading/Success/Error 三态
├── DerivedStateOfDemo.kt        ⑦ derivedStateOf —— MessageList 滚动越过首项才显示「回到顶部」
├── SnapshotFlowDemo.kt          ⑧ snapshotFlow —— 滚动越过首项上报一次埋点事件
├── SideEffectsWidgets.kt        包内复用组件（CodeBlock/LogBox/Stage/ButtonRow/OptionChips/Readout）
│                                + 共享的「非 Compose 对象」FakeAnalytics
└── README.md                    本文件
```

---

## 四、逐个示例的实现方式

### ① `LaunchedEffect`：在 composable 作用域里跑 suspend

进入组合时用 block 启动协程；离开组合取消；**用不同 key 重组则取消旧协程、重启新的**。
复刻官方「按可配置频率闪烁 alpha」：

```kotlin
var pulseRateMs by rememberSaveable { mutableLongStateOf(1500L) }
val alpha = remember { Animatable(1f) }
LaunchedEffect(pulseRateMs) {            // key=pulseRateMs：改频率就重启 effect
    while (isActive) {
        delay(pulseRateMs)               // suspend
        alpha.animateTo(0f); alpha.animateTo(1f)   // suspend
    }
}
```

切换频率时，`LogBox` 会打出「旧 effect 取消 → 新 effect 启动」，直观呈现 **Restarting effects**。
alpha 每帧变化用 `graphicsLayer { }` 应用，只走绘制阶段、不触发重组。

### ② `rememberCoroutineScope`：在回调里启动协程

`LaunchedEffect` 是 composable，只能写在 composable 体内；而 `onClick` 是普通回调。
用 `rememberCoroutineScope()` 拿到绑定组合位置的 scope，在回调里 `launch`，离开组合自动取消。
复刻官方 Snackbar 示例，并加一个「手动启停计数协程」体现「手动控制协程生命周期」这层用途：

```kotlin
val scope = rememberCoroutineScope()
Button(onClick = {
    scope.launch { snackbarHostState.showSnackbar("Something happened!") }  // 回调里跑 suspend
}) { Text("Press me") }
```

### ③ `rememberUpdatedState`：effect 不重启也能拿到最新值

长生命周期 effect 不想因某个值变化而重启，就用它把值包一层——effect 只创建一次，但总能读到最新值。
复刻官方 `LandingScreen(onTimeout)`，并把**正确 vs 错误**并排做出来：计时 4 秒内不断改变回调携带的内容，

- 正确：`val currentOnTimeout by rememberUpdatedState(onTimeout)` → 超时触发**最新**回调；
- 错误：直接用被 effect 捕获的旧 `onTimeout` → 触发**启动那一刻**的旧内容（stale closure）。

> 官方 Warning：`LaunchedEffect(true)` 和 `while(true)` 一样可疑——常量 key 表示「只随调用点生命周期、不重启」，用前想清楚。

### ④ `DisposableEffect`：需要清理的副作用

key 变化或离开组合时要「善后」的副作用。block 最后一句**必须**是 `onDispose { }`（否则编译报错）。
复刻官方：注册 `LifecycleEventObserver` 上报前后台埋点，`onDispose` 里注销；`onStart/onStop` 回调用
`rememberUpdatedState` 包（不作为 key），`lifecycleOwner` 作为 key。

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> /* ON_START/ON_STOP 埋点 */ }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

**按 Home 键切后台再回来**即可看到 ON_STOP/ON_START；另用一个可变 key 演示「key 变 → 先 dispose 旧的再注册新的」。

### ⑤ `SideEffect`：把 Compose 状态发布给非 Compose 代码

保证在**每次成功重组之后**执行——直接把副作用写在 composable 体内是错误的（重组可能被丢弃/顺序不定）。
复刻官方 `rememberFirebaseAnalytics`：切换用户类型 → 重组 → `SideEffect` 把最新 `userType` 写进
共享的 `FakeAnalytics`（扮演分析 SDK 这个「非 Compose 对象」），界面实时展示它持有的属性。

```kotlin
@Composable
fun rememberFakeAnalytics(user: User): FakeAnalytics {
    val analytics = remember { FakeAnalytics }
    SideEffect { analytics.setUserProperty("userType", user.userType) }   // 重组后执行
    return analytics
}
```

> 实现细节：`FakeAnalytics.setUserProperty` 带**去重守卫**（值没变不写），否则会陷入
> 「SideEffect 写状态 → 触发重组 → 又执行 SideEffect」的死循环。这也是使用 SideEffect 的通用注意事项。

### ⑥ `produceState`：把非 Compose 状态转成 Compose State

启动一个绑定组合的协程，把外部数据（Flow/LiveData/回调/suspend）写进返回的 `State`（`value =`）。
进入启动、离开取消、key 变重启；返回的 State 是 **conflated** 的。复刻官方 `loadNetworkImage`：

```kotlin
@Composable
fun loadNetworkImage(url: String, repo: ImageRepository): State<ImageResult> =
    produceState<ImageResult>(initialValue = ImageResult.Loading, url, repo) {  // key: url, repo
        val image = repo.load(url)                 // suspend
        value = if (image == null) ImageResult.Error else ImageResult.Success(image)
    }
```

> 官方 Key Point：`produceState` 底层就是 `remember { mutableStateOf(initial) }` + `LaunchedEffect`，
> 每次给 `value` 赋值就更新那个 state——**你完全可以基于现有 API 组合出自己的 effect**。
> 对非挂起源（回调/监听器），用 producer 作用域里的 `awaitDispose { }` 在离开时注销订阅。
> 注意显式写 `produceState<ImageResult>(...)`，否则类型会被推断成 `ImageResult.Loading`。

### ⑦ `derivedStateOf`：把状态派生成「变化更少」的状态

当输入变化频率 ≫ UI 需要的更新频率（如滚动位置），用它只在**结果跨越阈值**时才重组（类似 `distinctUntilChanged`）。
复刻官方 `MessageList`：`showButton = derivedStateOf { listState.firstVisibleItemIndex > 0 }`，
配 `AnimatedVisibility` 只在需要时显示「回到顶部」浮动按钮。

```kotlin
val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
AnimatedVisibility(visible = showButton) { ScrollToTopButton() }
```

> 官方 Caution：`derivedStateOf` 有开销，**别滥用**。把两个普通状态拼在一起（如 `"$firstName $lastName"`）
> 用它纯属浪费——直接算即可（README 与示例里都给了正确/错误对比）。
> 实现坑：`AnimatedVisibility` 若写在 `Column` 作用域里的 `Box` 内，会被解析成 `ColumnScope` 扩展而报错；
> 本示例把「列表 + 按钮」抽成独立 composable `MessageListBox`，其内无 ColumnScope 接收者，解析到顶层版本。

### ⑧ `snapshotFlow`：把 Compose 的 State 转成 Flow

`snapshotFlow { }` 被 collect 时运行 block，把读到的 State 结果发出；之后 State 变化且新值≠旧值时再发
（内建 `distinctUntilChanged` 语义）。这样就能对 Compose 状态用上 Flow 的全部操作符。
复刻官方：滚动越过首项时上报一次分析事件。

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .map { it > 0 }
        .distinctUntilChanged()
        .filter { it == true }
        .collect { FakeAnalytics.logEvent("scrolledPastFirstItem") }
}
```

---

## 五、Restarting effects（重启与 key）—— 贯穿所有 effect 的横切主题

`LaunchedEffect` / `produceState` / `DisposableEffect` 都接受可变数量的 **key**，形式为：

```
EffectName(restartIfThisKeyChanges, orThisKey, orThisKey, ...) { block }
```

key 用错的两种后果：
- **该重启却没重启** → 用到过期的值/对象，产生 bug；
- **不该重启却重启** → 反复取消重建，低效。

**经验法则（官方）**：
- effect block 里用到的**可变/不可变变量**，原则上都应作为 key 传入；
- 若某变量变化**不应**导致重启，用 `rememberUpdatedState` 包住它（如回调 `onStart/onStop`）；
- 若某变量用 `remember`（无 key）持有、**从不变化**，则无需作为 key；
- 想让 effect 只随「调用点生命周期」，用常量 key（`Unit`/`true`）——但要像对待 `while(true)` 一样谨慎。

---

## 六、选型速查

```
在 composable 体内自动跑协程（一次性/长任务/动画）？        → LaunchedEffect(key)
在 onClick 等【回调】里跑协程 / 要手动 cancel？             → rememberCoroutineScope + scope.launch
长生命周期 effect 里要读【最新】回调/值但不想重启？          → rememberUpdatedState
副作用需要清理（注册/注销、订阅/退订）？                    → DisposableEffect(key) { … onDispose { } }
把 Compose 状态同步给【非 Compose 对象】（分析 SDK 等）？    → SideEffect
把外部数据（Flow/LiveData/回调/suspend）变成 State？        → produceState（非挂起源配 awaitDispose）
输入高频变化、只关心结果是否跨越阈值、想少重组？            → derivedStateOf
把 Compose State 变成 Flow、用操作符处理？                 → snapshotFlow
```

---

## 七、实践清单

**必须做**

- [ ] 副作用一律放进对应 Effect API，别裸写在 composable 函数体里（重组不可预测、可能被丢弃）。
- [ ] effect block 里用到的、会变化且应触发重启的值，作为 **key** 传入；不该触发重启的回调用 `rememberUpdatedState` 包住。
- [ ] 需要清理资源（监听器/订阅/观察者）时用 `DisposableEffect`，且 `onDispose { }` 必须是 block 最后一句。
- [ ] 把状态发布给外部对象用 `SideEffect`；若写入的是可观察状态，加**去重守卫**防止重组死循环。
- [ ] `produceState` 显式标注泛型（`produceState<Result>(...)`），避免被推断成初值的具体子类型。
- [ ] `derivedStateOf` 用 `remember { derivedStateOf { } }` 持有；只在「输入变化远多于所需重组」时用。
- [ ] `snapshotFlow` 必须在 `LaunchedEffect`/`rememberCoroutineScope` 里 collect，随组合生命周期取消。

**必须避免**

- [ ] 在回调里试图写 `LaunchedEffect`（它是 composable，只能写在 composable 体内）——改用 `rememberCoroutineScope`。
- [ ] `LaunchedEffect(true)`/`(Unit)` 当默认选择而不思考（常量 key = 永不因值变化重启，像 `while(true)` 一样谨慎）。
- [ ] 空的 `onDispose { }`（说明你其实不需要 DisposableEffect，换别的 effect）。
- [ ] 滥用 `derivedStateOf` 去「派生」本就同频变化的值（如姓名拼接），纯属开销。
- [ ] 在 `SideEffect`/组合期直接改可观察状态又不加守卫，导致无限重组。
- [ ] 忘记把 `lifecycleOwner` 等会变对象作为 `DisposableEffect` 的 key（会用错 owner）。

---

## 八、延伸阅读

- 官方文章《Side-effects in Compose》——本模块的组织蓝本（developer.android.com/develop/ui/compose/side-effects）。
- 配套官方文档：《State and Jetpack Compose》《Managing state》《Thinking in Compose》。
- `androidx.compose.runtime` sources —— `Effects.kt`（LaunchedEffect/DisposableEffect/SideEffect）、
  `ProduceState.kt`（produceState/awaitDispose）、`DerivedState.kt`（derivedStateOf）、
  `SnapshotFlow.kt`（snapshotFlow/collectAsState）、`SnapshotState.kt`（rememberUpdatedState）。
- 本项目 `flow/` 模块 —— `snapshotFlow` 产出的 Flow 可用其中的操作符；`collectAsStateWithLifecycle` 是消费流的首选。
- 本项目 `animation/` 模块 —— `LaunchedEffect` 里配合 `Animatable` 做动画的更多示例。
