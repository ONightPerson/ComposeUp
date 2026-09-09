# Compose 进阶 · 页面嵌套滑动（Nested Scrolling）

本目录用 5 个可交互的示例，把 Jetpack Compose 的嵌套滑动从「协议原理」到「框架内置能力」讲透。
所有代码基于本项目实际依赖的版本：

| 依赖 | 版本 |
| --- | --- |
| Compose BOM | `2026.02.01` |
| `androidx.compose.ui:ui` | `1.10.4` |
| `androidx.compose.foundation:foundation` | `1.10.4` |
| `androidx.compose.material3:material3` | `1.4.0` |

> ⚠️ **版本提醒**：在 Compose 1.10 中，嵌套滚动 API 的包名是
> **`androidx.compose.ui.input.nestedscroll`**。
> 网上大量教程写的是 `androidx.compose.foundation.nestedscroll`（1.1 ~ 1.8 时代的包名），
> 在本项目里会直接编译不过。同理，旧教程里的 `Modifier.consumeWithinBounds`、
> `Modifier.scrollWithoutConsuming`、`rememberNestedScrollFlingBehavior` 在 1.10 中**已不存在**，
> 需要自己用一个「返回 available 的 connection」实现同样效果（见示例③解法四）。

---

## 一、怎么跑起来

不需要任何额外配置，直接运行 App：

```
MainActivity
  └─ ComposeUpTheme
       └─ ComposeUpRoot            ← Scaffold + 右下角 FAB
            ├─ Home          ComposeUpApp()          原示例
            ├─ NestedScroll  NestedScrollHub()       ← 本次新增
            └─ Settings      SettingsScreen()        DataStore 示例
```

点右下角 FAB 循环切换页面，切到 **嵌套滑动进阶** 后进入 `NestedScrollHub`，
点击列表里任意一项进入具体示例，用系统返回键或左上角箭头退回列表。

---

## 二、核心概念：嵌套滚动是一套「二维 · 双向 · 四阶段」的事件协议

### 2.1 三个角色

| 角色 | 由谁承担 | 作用 |
| --- | --- | --- |
| **派发子级**（dispatching child） | 真正接收手势并滚动的组件，如 `LazyColumn`、`verticalScroll` | 通过 `NestedScrollDispatcher` 把增量抛给祖先 |
| **链条成员**（chain member） | 任意挂了 `Modifier.nestedScroll(connection)` 的祖先节点 | 通过 `NestedScrollConnection` 决定「我要不要消费」 |
| **协议载体** | `Offset` / `Velocity` | **二维**，同时携带 x、y 两个分量 |

`Modifier.nestedScroll(connection, dispatcher)` 中：

- `connection` 是**必填**的 —— 哪怕你只想派发、不想接收，也得挂一个空实现占位；
- `dispatcher` 是**可选**的 —— 只有「自己会动、还想通知父级」的组件才需要它。

### 2.2 四个阶段的时序

一次完整的「拖动 + 松手」会依次触发：

```
手指按下并拖动，产生 delta: Offset
│
├─① onPreScroll(available, source): Offset
│     方向：自下而上（子 → 父）
│     语义：「我还没滚，你要不要先吃一点？」
│     返回：父级实际吃掉的量 parentConsumed
│     子级只剩 available - parentConsumed 可用
│
├─  子级自己滚动，吃掉 selfConsumed
│
├─② onPostScroll(consumed, available, source): Offset
│     方向：自下而上（子 → 父）
│     语义：「我滚完了，这是剩下的，你要么？」
│     consumed = 子级及更下层已吃掉的量
│     available = 还剩多少可以被父级吃
│
手指抬起，产生 velocity: Velocity
│
├─③ onPreFling(available): Velocity        ← suspend
│     语义：「我要开始惯性滑动了，你要不要抢走一部分速度？」
│
├─  子级自己做 fling
│
└─④ onPostFling(consumed, available): Velocity   ← suspend
      语义：「我惯性滑完了，剩下的速度给你」
```

**记住两条经验法则：**

1. **想「抢在子级前面」动 → 实现 `onPreScroll`**（例：上滑时先把工具栏收起来）
2. **想「等子级滚不动了」再动 → 实现 `onPostScroll`**（例：列表到顶后才下拉刷新 / 才展开工具栏）

### 2.3 `NestedScrollSource`：区分「手指」与「惯性」

```kotlin
NestedScrollSource.UserInput   // 手指拖动、鼠标、键盘等直接用户输入
NestedScrollSource.SideEffect  // 动画、fling 等非直接输入
```

`onPreScroll` / `onPostScroll` 都带 `source` 参数。**必须判断它**，否则惯性阶段会被同一套逻辑
重复处理一遍，出现「列表滑到底还猛地抖一下」的 bug。本目录示例统一的做法是：
拖动阶段只认 `UserInput`，惯性阶段只走 `onPreFling` / `onPostFling`，互不干扰。

> 旧代码里的 `NestedScrollSource.Drag` / `.Fling` / `.Wheel` 已废弃，
> 分别被 `UserInput` / `SideEffect` / `UserInput` 取代。

### 2.4 协议是「与方向无关」的

官方源码注释原文（`NestedScrollModifier.kt`）：

> *The nested scroll system is orientation independent. This means it is based off the screen
> direction (x and y coordinates) rather than being locked to a specific orientation.*

因为传递的是二维 `Offset`，每个可滚动组件**只消费自己方向上的那一个分量**，另一个分量原样透传。
这就是「`HorizontalPager` 里套 `LazyColumn` 一行代码都不用写」的根本原因（示例④）。

### 2.5 框架内置组件走的是哪条路？

`Modifier.verticalScroll` / `LazyColumn` / `LazyRow` / `HorizontalPager` 底层都是 `Modifier.scrollable`。
它内部的 `ScrollableNestedScrollConnection`（foundation `Scrollable.kt`）**只实现了 post 阶段**：

```kotlin
internal class ScrollableNestedScrollConnection(
    val scrollingLogic: ScrollLogic,
    var enabled: Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (enabled) scrollingLogic.performRawScroll(available) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = ...
}
```

**推论（很重要）**：一个可滚动的祖先**天然就是「兜底消费者」** —— 子级吃剩的都会给它。
所以「内层列表滚到底后外层接着滚」这种联动是免费送的；反过来想要「内层滚到底后外层**别**动」，
就必须自己插一个 connection 把事件吃掉（示例③解法四）。

---

## 三、文件结构与职责

```
nestedscroll/
├── NestedScrollHub.kt           入口：话题列表 + 二级页面切换（含 BackHandler）
├── CollapsingHeaderDemo.kt      示例①：手写 connection 实现可折叠 Header（四阶段全覆盖）
├── DispatcherDemo.kt            示例②：手写 dispatcher，让自绘组件参与嵌套滚动
├── SameDirectionNestingDemo.kt  示例③：同方向嵌套的崩溃原因 + 四种解法（可切换对比）
├── OrthogonalNestingDemo.kt     示例④：正交方向嵌套的三种常见结构
├── Material3CoopDemo.kt         示例⑤：Material3 组件内置的嵌套滚动协作
├── NestedScrollWidgets.kt       包内复用的小组件（DemoRow / DemoNote / DemoPlaceholder …）
└── README.md                    本文件
```

---

## 四、逐个示例的实现方式

### 示例① 手写 `NestedScrollConnection`：可折叠 Header

**目标手感**：上滑先折叠 Header → 折叠到底后列表才开始滚；下滑列表先回到顶部 → 到顶后 Header 才展开；
松手时 Header 若停在半路则自动吸附到最近端点。

**实现要点**

1. **状态设计**：用 `progress: Float`（`0f` 展开 / `1f` 折叠）而不是「像素偏移」。
   `rememberSaveable` 保存比例，旋转屏幕、切换 DPI 都不会错乱；像素换算在 `remember` 时
   用 `LocalDensity` 现算成 `travelPx`。

   ```kotlin
   var progress by rememberSaveable { mutableFloatStateOf(0f) }
   val travelPx = with(LocalDensity.current) {
       (ExpandedHeaderHeight - CollapsedHeaderHeight).toPx()
   }
   ```

2. **connection 必须 `remember` 复用**。每次重组都新建一个 connection，会让嵌套滚动图反复重建，
   既浪费也可能丢事件（官方文档明确建议复用）。

   ```kotlin
   val connection = remember(travelPx) {
       CollapsingHeaderConnection(travelPx, { progress }, { progress = it })
   }
   ```

   把 connection 抽成普通 `class` 而非匿名 `object`，好处是：能持有 `travelPx` 做换算、
   能通过读写 lambda 访问 Compose 状态而不依赖 Composable 作用域、可以写单元测试。

3. **符号处理**是这里最容易写错的地方。约定：`available.y < 0` 表示手指上滑。

   ```kotlin
   private fun consume(deltaY: Float): Offset {
       val old = readProgress()
       val new = (old - deltaY / travelPx).coerceIn(0f, 1f)  // 上滑 deltaY<0 → progress 变大
       writeProgress(new)
       val consumedProgress = new - old
       return Offset(x = 0f, y = -consumedProgress * travelPx)  // 还原成与 available.y 同号
   }
   ```

   **返回值的符号必须和 `available` 一致**，否则父层链上的计算会全部乱掉。

4. **阶段分配**：

   | 阶段 | 本例的处理 |
   | --- | --- |
   | `onPreScroll` | 仅当 `source == UserInput && available.y < 0` 时消费 → 实现「上滑先折叠」 |
   | `onPostScroll` | 仅当 `source == UserInput && available.y > 0` 时消费 → 实现「列表到顶才展开」 |
   | `onPreFling` | 返回 `Velocity.Zero`，**故意不抢速度**，保证快速甩动列表时依然顺滑 |
   | `onPostFling` | 用剩余速度决定吸附方向，或直接吸附到最近端点 |

5. **在 suspend 回调里直接跑动画**。`onPreFling` / `onPostFling` 本身是 `suspend`，
   可以直接用 `androidx.compose.animation.core.animate` 每帧回写状态，无需再开协程：

   ```kotlin
   override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
       ...
       animate(initialValue = current, targetValue = target, animationSpec = tween(220)) { value, _ ->
           writeProgress(value)
       }
       return available   // 返回 available = 「速度我全吃了」，阻止继续向上冒泡
   }
   ```

6. **渲染**：Header 高度按 `progress` 在 `200dp ~ 56dp` 之间连续变化，配合
   `Modifier.clipToBounds()` 做出「卷起来」的观感。

   > 生产环境更推荐固定高度 + `Modifier.graphicsLayer { translationY = ... }` 或
   > `Modifier.offset { IntOffset(...) }`：只改绘制阶段，不触发重新测量，帧率更稳。
   > 本例为了让「折叠」这件事在视觉上最直白，选择了改高度。

### 示例② `NestedScrollDispatcher`：让自绘组件参与嵌套滚动

**问题场景**：页面中间嵌了一个自绘控件（本例是一个只能在 ±48dp 轨道里移动的小球）。
它**没有 `ScrollState`**，Compose 完全不知道它在滚动 —— 用户在它上面滑动时外层页面纹丝不动，手感割裂。

**实现要点**：把示例①的四个阶段**反过来主动执行一遍**。

```kotlin
val dispatcher = remember { NestedScrollDispatcher() }

Box(
    modifier
        // connection 必填但这里无事可做 → 用一个空实现占位
        .nestedScroll(connection = NoOpConnection, dispatcher = dispatcher)
        .pointerInput(dispatcher, trackPx) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()                       // 自己接管手势，别让祖先的 scrollable 抢走
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val delta = Offset(0f, dragAmount)

                    val parentPre = dispatcher.dispatchPreScroll(delta, NestedScrollSource.UserInput)
                    val leftForSelf = delta - parentPre

                    val old = ballOffset
                    val new = (old + leftForSelf.y).coerceIn(-trackPx, trackPx)
                    ballOffset = new
                    val selfConsumed = Offset(0f, new - old)

                    dispatcher.dispatchPostScroll(
                        consumed  = selfConsumed,
                        available = leftForSelf - selfConsumed,
                        source    = NestedScrollSource.UserInput,
                    )
                },
                onDragEnd = { /* dispatchPreFling → dispatchPostFling，见下 */ },
            )
        }
)
```

要点补充：

- `change.consume()` 不能省。不 consume 的话，祖先 `verticalScroll` 的手势探测也会启动，
  同一次拖动会被两个组件同时处理。
- **fling 需要自己算速度**：`detectVerticalDragGestures` 不给速度，要用
  `androidx.compose.ui.input.pointer.util.VelocityTracker`，在 `onVerticalDrag` 里持续
  `addPosition(change.uptimeMillis, change.position)`，`onDragEnd` 时 `calculateVelocity()`。
- `dispatchPreFling` / `dispatchPostFling` 都是 `suspend`，必须在协程里调用。
  官方建议用 `dispatcher.coroutineScope` 而非 `rememberCoroutineScope()`，
  这样即使组件在 fling 途中被销毁，动画也能在父级作用域里跑完而不会戛然而止。
- 界面顶部实时打印每一次的 `Δ / 父预吃 / 自吃 / 父后吃`，可以直观看到增量是如何被瓜分的。

### 示例③ 同方向嵌套滚动：崩溃原因与四种解法

#### 坑

```kotlin
Column(Modifier.verticalScroll(rememberScrollState())) {   // 外层：纵向可滚动
    Text("头部说明")
    LazyColumn { items(100) { Text("第 $it 项") } }         // 内层：纵向懒列表 ← 崩溃
}
```

```
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinite
maximum height constraints, which makes it unscrollable. ...
```

#### 为什么崩

`verticalScroll` 的本质是：**把子级的最大高度约束改成 Infinity，让子级按内容真实高度测量，
自己再负责裁剪与偏移**。而 `LazyColumn` 需要一个有限高度才能算出「可视区放得下几项」，
拿到 Infinity 就无法工作，于是主动抛错。

> 关键认知：**这不是「嵌套滚动协议」的问题，而是「测量约束」的问题。**
> 协议解决的是*手势如何协调*；这里首先得让*布局成立*。

#### 四种解法（示例内可切换对比）

| 解法 | 做法 | 适用场景 |
| --- | --- | --- |
| **一 · 合并成一个 `LazyColumn`**（官方推荐） | 头部用 `item { }`，分组标题用 `stickyHeader { }`，正文用 `items { }`，全部塞进同一个懒列表 | 绝大多数「长页面」需求。懒加载/回收/吸顶全由框架处理，性能与手感最好 |
| **二 · 内层固定高度** | `LazyColumn(Modifier.height(240.dp))` | 内层确实需要独立滚动区域（如内嵌评论列表）。内层滚到边界后剩余增量会自动冒泡给外层 |
| **三 · 内层退化为普通 `Column`** | `Column { list.forEach { ... } }`，放弃懒加载 | 条目数量可控（几十条）。⚠️ `LazyColumn(userScrollEnabled = false)` **不能**解决问题 —— 崩溃发生在测量阶段，与手势开关无关 |
| **四 · 阻断冒泡** | 在内层挂一个「吞噬连接」 | 恰恰不希望「内层滚到底后带动外层」（内嵌地图、内嵌代码块等） |

解法四的实现只有几行，正好补上 1.10 中已被移除的 `Modifier.consumeWithinBounds`：

```kotlin
private class ScrollEaterConnection : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .height(280.dp)
        .then(if (isolate) Modifier.nestedScroll(eater) else Modifier),
) { ... }
```

- 只实现 post 阶段：pre 阶段走默认的 `Offset.Zero`，表示「我不抢，让子级先滚」。
- `onPostScroll` 返回 `available` = 谎报「我全吃了」，外层的 `onPostScroll` 就再也收不到东西。
- **修饰符顺序很重要**：`nestedScroll` 必须挂在 `LazyColumn` 自身的 modifier 链上，
  使其节点成为 `LazyColumn` 内部 `scrollable` 节点的**祖先**，才能拦到它派发的事件。

### 示例④ 正交方向嵌套：为什么「不用管」就是最好的处理

三种常见结构，全部零额外代码：

1. **Tab + 列表**：`PrimaryTabRow` ↔ `HorizontalPager` 联动，每页一个撑满的 `LazyColumn`。
   横向手势归 Pager、纵向手势归 LazyColumn，斜着滑时框架自动判定主方向。
2. **纵向列表内嵌横向 Pager**：外层 `LazyColumn`，某个 `item` 是 `height(260.dp)` 的
   `HorizontalPager`，每页又是一个 `LazyColumn`。注意内层仍需确定高度 ——
   **约束问题与方向无关**。
3. **纵向列表内嵌 `LazyRow`**：横向懒列表的高度由内容决定，本身就是有限值，不存在约束冲突。

### 示例⑤ Material3 组件内置的嵌套滚动协作

结论：**大多数业务场景根本不用手写协议**。

| 组件 | 内置行为 | 关键接线 |
| --- | --- | --- |
| `TopAppBar` + `TopAppBarDefaults.*ScrollBehavior()` | 三种预设：`pinned`（钉住不动）/ `enterAlways`（一滑就收、一回就出）/ `exitUntilCollapsed`（从 `expandedHeight` 收缩到默认高度后停住） | `Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))` —— **只有这一行是必须的** |
| `ModalBottomSheet` | 向下拖时内部列表先滚回顶部，到顶后剩余增量才交给 Sheet 下滑关闭，拖过阈值松手才 dismiss | 无需任何接线，`SheetState` 内部已实现 connection |
| `PullToRefreshBox` | 只有子列表已在顶部且仍继续下拉时，才用 `onPostScroll` 消费剩余位移拉出指示器 | 把可滚动内容直接放进 `content` 即可 |

`TopAppBar` 的三种行为与本目录示例①手写版本**完全同构** —— 它们内部同样是
`onPreScroll` / `onPostScroll` + `heightOffset` 状态机。理解了示例①，就能读懂 Material3 的实现。

> `TopAppBarDefaults.pinnedScrollBehavior()` / `enterAlwaysScrollBehavior()` /
> `exitUntilCollapsedScrollBehavior()` 与 `ModalBottomSheet` 在 material3 `1.4.0` 中
> 仍标注 `@ExperimentalMaterial3Api`，调用处需要 `@OptIn(ExperimentalMaterial3Api::class)`。
> `TopAppBar` 本体和 `PullToRefreshBox` 已是稳定 API。

---

## 五、实践清单

**必须做**

- [ ] connection / dispatcher 一律 `remember` 复用，不要每次重组新建。
- [ ] `onPreScroll` / `onPostScroll` 里判断 `source`，拖动与惯性分开处理。
- [ ] 返回的 `Offset` / `Velocity` 符号必须与入参 `available` 一致。
- [ ] 需要换算像素时用 `LocalDensity` 在组合期算好，不要在 connection 里访问 Composable。
- [ ] 每帧变化的位移优先用 `graphicsLayer { }` / `offset { }` 的 lambda 版本，避免重组与重测量。

**必须避免**

- [ ] `Column(verticalScroll) { LazyColumn { } }` —— 同方向无限高嵌套，必崩。
- [ ] 指望 `userScrollEnabled = false` 解决约束崩溃 —— 无效，崩溃发生在测量阶段。
- [ ] 在 `onPreScroll` 里无条件返回 `available` —— 子级将永远滚不动。
- [ ] 把 `nestedScroll` 挂在子级内部（比 `scrollable` 更靠后的位置）—— 拦不到事件。
- [ ] 抄旧教程的 `androidx.compose.foundation.nestedscroll` 包名与 `consumeWithinBounds` 等 API。

---

## 六、延伸阅读

- `NestedScrollModifier.kt`（`androidx.compose.ui:ui` sources jar）—— `nestedScroll` 修饰符的
  KDoc 是官方对四阶段协议最完整的描述。
- `Scrollable.kt` 中的 `ScrollableNestedScrollConnection`（`androidx.compose.foundation:foundation`）
  —— 看清「可滚动组件天然只兜底 post 阶段」。
- `AppBar.kt` 中的 `ExitAlwaysScrollBehavior` / `EnterAlwaysScrollBehavior`（material3）
  —— 对照示例①，看工业级实现如何处理 `heightOffsetLimit`、`snapAnimationSpec`、`flingAnimationSpec`。
