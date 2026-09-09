# Compose 进阶 · 动画（Animation）

本目录用 6 个可交互的示例，把 Jetpack Compose 的动画从「分类心智模型」到「每一类怎么写」讲透。
所有代码基于本项目实际依赖的版本：

| 依赖 | 版本 |
| --- | --- |
| Compose BOM | `2026.02.01` |
| `androidx.compose.animation:animation` | `1.10.x` |
| `androidx.compose.animation:animation-core` | `1.10.4` |
| `androidx.compose.foundation:foundation` | `1.10.4` |
| `androidx.compose.material3:material3` | `1.4.0` |

> ⚠️ **版本提醒**：`rememberInfiniteTransition` / `InfiniteTransition.animateFloat` /
> `InfiniteTransition.animateColor` 在 1.10 中**必须带 `label` 参数** —— 无 label 的旧重载已被标为
> `DeprecationLevel.HIDDEN`（编译期直接不可见），照抄旧教程会编译不过。
> 列表项动画用 **`LazyItemScope.Modifier.animateItem(...)`**，旧的 `animateItemPlacement` 已改名。
> 所有 `animate*AsState` / `Transition.animate*` 也都新增了 `label` 形参（有默认值，可省略）。

---

## 一、怎么跑起来

不需要任何额外配置，直接运行 App：

```
MainActivity
  └─ ComposeUpTheme
       └─ ComposeUpRoot            ← Scaffold + 右下角 FAB
            ├─ Home          ComposeUpApp()          原示例
            ├─ NestedScroll  NestedScrollHub()       嵌套滑动进阶
            ├─ Animation     AnimationHub()          ← 本次新增
            └─ Settings      SettingsScreen()        DataStore 示例
```

点右下角 FAB 循环切换页面，切到 **动画进阶** 后进入 `AnimationHub`，
点击列表里任意一项进入具体示例，用系统返回键或左上角箭头退回列表。

---

## 二、核心心智模型：按「谁管理动画状态」分三层

Compose 动画 API 看着多，其实可以按一个问题归类：**动画的「当前值」由谁持有、由谁驱动？**

| 层次 | 代表 API | 谁持有值 | 你要做什么 | 典型场景 |
| --- | --- | --- | --- | --- |
| **① 值动画** | `animate*AsState`、`updateTransition`、`rememberInfiniteTransition` | 框架持有，**暴露一个 State 给你读** | 自己把值画到屏幕上 | 颜色/尺寸/透明度/旋转、状态机联动、常驻循环动画 |
| **② 组件级 API** | `AnimatedVisibility`、`AnimatedContent`、`Crossfade`、`animateContentSize`、`animateItem` | 框架托管 **Composable 的进出场 / 尺寸 / 增删** | 只描述「怎么进、怎么出」 | 显示隐藏、换页、列表增删、展开收起 |
| **③ 底层手势动画** | `Animatable` | **你**持有，手动 `snapTo` / `animateTo` / `animateDecay` | 自己接管手势、速度、边界 | 拖拽跟手、惯性 fling、滑动关闭、可打断动画 |

再叠加一个正交的维度 —— **AnimationSpec（动画规格）**，决定「怎么动」：
`tween`（定时长）、`spring`（物理弹簧、可回弹、无固定时长）、`keyframes`（关键帧编排）、
`snap`（瞬切），以及 `repeatable` / `infiniteRepeatable`（重复）。上面三层的每个 API 都接受一个 spec。

一句话记忆：

> **值动画给你「数」，组件级 API 帮你「换」，Animatable 让你「手动跟手」；
> spec 决定它们「以什么节奏」完成。**

### 2.1 性能主线：改 Modifier vs 改 graphicsLayer

贯穿所有示例的一条经验：**每帧变化的「位移 / 缩放 / 旋转 / 透明度」尽量走
`Modifier.graphicsLayer { }` 或 `Modifier.offset { }` 的 lambda 版本**，而不是去改布局尺寸。

- 改 `size` / `padding` / `shape` → 触发**重新测量 + 布局**，甚至重组，代价高；
- 改 `graphicsLayer` / `offset` 的 lambda → 只在**绘制阶段**读取插值，不重组、不重测量，帧率稳。

所以示例①里，尺寸/圆角/颜色走 Modifier（这些本就要影响布局），而旋转/透明度/位移走 graphicsLayer 与 offset lambda。

---

## 三、文件结构与职责

```
animation/
├── AnimationHub.kt            入口：话题列表 + 二级页面切换（含 BackHandler）
├── ValueAnimationDemo.kt      示例①：animate*AsState 家族（Float/Color/Dp/Offset）
├── TransitionDemo.kt          示例②：updateTransition 状态机驱动多个同步子动画
├── VisibilityContentDemo.kt   示例③：AnimatedVisibility / AnimatedContent / Crossfade
├── SizeAndListDemo.kt         示例④：animateContentSize + LazyItemScope.animateItem
├── AnimationSpecDemo.kt       示例⑤：四种 spec 竞速对比 + 无限动画
├── GestureAnimatableDemo.kt   示例⑥：Animatable 手势驱动、惯性、可打断
├── AnimationWidgets.kt        包内复用的小组件（DemoStage / DemoButtonRow / DemoOptionChips …）
└── README.md                  本文件
```

---

## 四、逐个示例的实现方式

### 示例① 值动画：`animate*AsState` 家族

**心智**：你持有一个「目标值」状态，`animate*AsState` 返回一个「正在补间的当前值」State；
目标一变，返回值自动从旧值平滑过渡到新值。**它只算数，怎么画由你决定。**

按数据类型选专用 API，好处是内置「可见性阈值」，变化小到肉眼不可见时提前收尾：

```kotlin
var expanded by rememberSaveable { mutableStateOf(false) }

val size  by animateDpAsState(if (expanded) 160.dp else 80.dp, tween(450), label = "size")
val color by animateColorAsState(if (expanded) tertiary else primary, tween(450), label = "color")
val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(450), label = "rotation")
```

**要点**

- 一个布尔状态可以同时驱动多个 `animate*AsState`，各自独立补间。
- 尺寸 / 圆角 / 颜色直接改 Modifier（本就要影响布局或重建绘制指令）；
  旋转 / 透明度 / 位移放进 `graphicsLayer { }` / `offset { }` 的 lambda（只走绘制阶段）。
- `animateOffsetAsState` 返回 `Offset`（float 像素），配合 `Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }` 使用。

### 示例② `updateTransition`：一次状态变化驱动一组同步动画

**心智**：当「同一个状态切换」牵动多个属性时，用一个**状态机**统一驱动，而不是写一堆各自为政的 `animate*AsState`。

```kotlin
val transition = updateTransition(targetState = cardState, label = "cardTransition")

val color by transition.animateColor(label = "cardColor") { state -> when (state) { ... } }
val size  by transition.animateDp(
    transitionSpec = {
        if (targetState == CardState.Expanded)                       // ← Segment 作用域里可读 initial/target
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        else spring(stiffness = Spring.StiffnessMedium)
    },
    label = "cardSize",
) { state -> when (state) { ... } }
```

**相比一堆 `animate*AsState` 的三个好处**

1. **同步**：所有子动画共享同一个「运行 / 结束」生命周期，节奏天然对齐。
2. **可分段定制**：`transitionSpec` 是 `Transition.Segment<S>.() -> FiniteAnimationSpec<V>`，
   能针对「从哪个状态到哪个状态」用不同规格（本例：进入 Expanded 用回弹弹簧）。
3. **可观测**：`transition.currentState` / `targetState` / `isRunning` 可用来驱动 UI（如动画中禁用按钮）。

> 选型：1~2 个独立属性 → `animate*AsState` 更轻；3+ 个属性且要求节奏一致 / 分段 / 观测 → `updateTransition`。
> Transition 上还有 `animateContentSize()`、`Crossfade`、`AnimatedContent`、`SlideInSlideOut` 等扩展。

### 示例③ 组件级：`AnimatedVisibility` / `AnimatedContent` / `Crossfade`

**心智**：这一层反过来 —— **框架托管 Composable 的进出场**，你只描述「怎么进、怎么出」。

| 组件 | 解决什么 | 关键参数 |
| --- | --- | --- |
| `AnimatedVisibility(visible, enter, exit)` | 一个组件的**显示 / 隐藏**带过渡 | `enter` / `exit` |
| `AnimatedContent(targetState, transitionSpec)` | 状态变了，**整块内容替换**带过渡 | `transitionSpec`（用 `togetherWith` 编排新入旧出） |
| `Crossfade(targetState, animationSpec)` | 多屏之间**交叉淡入淡出** | `animationSpec`（只有 alpha） |

进出场由「原子过渡」用 `+` 组合而成：

```kotlin
// AnimatedVisibility 的三种预设（示例内可切换）
val (enter, exit) = when (preset) {
    FadeExpand -> (fadeIn(tween(300)) + expandVertically(tween(300))) to
                  (shrinkVertically(tween(300)) + fadeOut(tween(300)))
    Slide      -> (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) to
                  (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
    Scale      -> (scaleIn(tween(300), initialScale = 0.6f) + fadeIn(tween(300))) to
                  (scaleOut(tween(300)) + fadeOut(tween(300)))
}
AnimatedVisibility(visible, enter = enter, exit = exit) { /* ... */ }
```

```kotlin
// AnimatedContent：用 togetherWith 把「新内容怎么进」和「旧内容怎么出」编排在一起
AnimatedContent(
    targetState = index,
    transitionSpec = {
        (fadeIn(tween(220, delayMillis = 90)) + slideInVertically(tween(280)) { it / 2 })
            .togetherWith(fadeOut(tween(90)) + slideOutVertically(tween(280)) { -it / 2 })
    },
    label = "screenContent",
) { targetIndex -> /* 用 targetIndex 渲染新屏 */ }
```

**关键区分**：`slide*` / `scale*` 只影响**绘制**、不改变父容器尺寸；`expand*` / `shrink*` 会**真正改变布局尺寸**并顶开邻居。想要「不推动周围内容」的进出场用 slide/scale，想要「把下方内容顶下去」用 expand/shrink。

### 示例④ `animateContentSize` + 列表增删动画

两个「和布局尺寸打交道」的动画。

**(1) `animateContentSize`** —— 内容尺寸变化时自动补间，你只管改内容：

```kotlin
Surface(
    modifier = Modifier
        .animateContentSize(                       // 拦截尺寸变化并补间
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        ),
    onClick = { expanded = !expanded },
) { Column { if (expanded) Text("新增内容…") } }
```

它底层就是 `clipToBounds()` + 一个尺寸插值 Modifier，所以过渡期间内容不会画到边界外。

**(2) `LazyItemScope.Modifier.animateItem()`** —— 列表项的出现淡入、消失淡出、位移补间，一行搞定：

```kotlin
val items = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
    mutableStateListOf(1, 2, 3, 4, 5)
}
LazyColumn {
    items(items = items, key = { it }) { id ->       // ← 必须提供稳定唯一的 key
        Box(Modifier.animateItem()) { Text("条目 #$id") }
    }
}
```

`animateItem(fadeInSpec, placementSpec, fadeOutSpec)` 三个规格分别控制新增、位移、删除，默认都是 spring。

> ⚠️ **必须提供 `key`**：否则 Compose 无法把「移动后的一项」和「移动前的一项」对应起来，动画失效。
> ⚠️ **不要把 `LazyColumn` 放进纵向 `verticalScroll` 的 `Column`**（无限高度约束会让懒列表抛异常）。
> 本示例用 `Modifier.weight(1f)` 给列表一块确定高度，而不是整页滚动 —— 这正是「嵌套滚动」那节讲过的坑。

### 示例⑤ `AnimationSpec`：规格大全与无限动画

**四种有限规格**（决定动画「性格」）：

| 规格 | 特点 | 何时用 |
| --- | --- | --- |
| `tween(durationMillis, delayMillis, easing)` | 固定时长 + 缓动曲线 | 大多数进/出场、颜色、透明度 |
| `spring(dampingRatio, stiffness, visibilityThreshold)` | 物理弹簧，**无固定时长**，可回弹 | 跟手、可打断、需要弹性的位移与尺寸 |
| `keyframes { durationMillis = …; v at t using easing }` | 手动指定「某时刻到某值」，逐段设缓动 | 有精确编排需求 |
| `snap(delayMillis)` | 瞬间跳到终值 = 「没有动画」 | 需要立即到位，或按条件关闭动画 |

**缓动曲线**（`Easing`，用于 `tween`）：`LinearEasing` 匀速、`FastOutSlowInEasing` 两头慢中间快（最常用）、
`LinearOutSlowInEasing` 结尾减速、`FastOutLinearInEasing` 起步加速。

示例用「四条轨道同场竞速」直观对比：同一个 0→1 的目标值喂给四种 spec，
`spring` 会冲过头再回弹，`keyframes` 按脚本顿挫，`snap` 延迟后瞬间到位。

> `keyframes` 给的是**绝对值**，最适合「起点→终点已知」的编排；反向播放会把同一脚本再走一遍。

**无限动画**两条路：

```kotlin
val infinite = rememberInfiniteTransition(label = "showcase")   // label 必填（旧无参重载已 HIDDEN）
val rotation by infinite.animateFloat(0f, 360f,
    infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "spin")
val scale by infinite.animateFloat(0.7f, 1.15f,
    infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "breath")     // Reverse 来回呼吸
val color by infinite.animateColor(primary, tertiary,
    infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "cycle")
```

- 旋转 / 加载 / 呼吸灯这类常驻循环 → `rememberInfiniteTransition`。
- `RepeatMode.Restart` 每轮从头开始；`RepeatMode.Reverse` 每轮反向播放（更适合呼吸 / 摆动）。
- 无限动画只在组合可见时运行，离开组合自动取消，不必手动管理生命周期。

### 示例⑥ `Animatable`：手势驱动、可打断的底层动画

**为什么需要它**：真实手势（拖拽、滑动关闭、惯性 fling）有三个「目标驱动」API 满足不了的诉求 ——
① **跟手**（每帧精确等于手指位移）、② **带速度**（松手接惯性）、③ **可打断**（途中再次操作立刻接管）。

`Animatable` 是一个**持有当前值和速度的可变动画状态**，三个 `suspend` 操作共用一把互斥锁，
**后发起的调用自动取消前一个动画** —— 这就是「可打断」的原理：

```kotlin
val offsetX = remember { Animatable(0f) }
val velocityTracker = remember { VelocityTracker() }
val scope = rememberCoroutineScope()
val decaySpec = remember { exponentialDecay<Float>(frictionMultiplier = 1f) }

LaunchedEffect(travelPx) { offsetX.updateBounds(-travelPx, travelPx) }   // 值与目标都会被夹在区间内

Modifier.pointerInput(travelPx, decaySpec) {
    detectHorizontalDragGestures(
        onDragStart = { velocityTracker.resetTracking() },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }          // 跟手
        },
        onDragEnd = {
            val vx = velocityTracker.calculateVelocity().x
            scope.launch { offsetX.animateDecay(vx, decaySpec) }                 // 惯性，撞 bounds 自动停
        },
    )
}
// 归位：animateTo 会立即打断仍在跑的 decay
scope.launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
```

**要点**

- `snapTo` 无动画、逐帧设值 → 跟手；`animateTo` 补间 → 归位；`animateDecay` 用初速度衰减 → 惯性 fling。
- `updateBounds` 让值、目标、惯性都自动被限制在区间内，省去手写「越界拉回」。
- `detectHorizontalDragGestures` 不给速度，要用 `VelocityTracker`：拖动中持续 `addPosition`，`onDragEnd` 时 `calculateVelocity()`。
- 渲染用 `Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }`，每帧只走布局阶段读取，不重组。

> Material3 的 `Slider` / `Switch` / `SwipeToDismiss` / `AnchoredDraggable` 底层全是 `Animatable`。
> 读懂本示例，就能读懂它们的「跟手 + 吸附」手感是怎么来的。

---

## 五、选型速查

```
需要动画的是什么？
├─ 一个数值（颜色/尺寸/透明度/进度）        → animate*AsState
│    └─ 一个状态切换牵动多个数值            → updateTransition（同步 + 分段 + 可观测）
├─ 组件的出现 / 消失                        → AnimatedVisibility（enter/exit）
├─ 状态变了要换一整块内容                    → AnimatedContent（transitionSpec + togetherWith）
│    └─ 只想淡入淡出、不要位移               → Crossfade
├─ 内容变多/变少，容器尺寸要平滑            → Modifier.animateContentSize
├─ 列表项增 / 删 / 重排                      → LazyItemScope.Modifier.animateItem（记得给 key）
├─ 常驻循环（旋转/加载/呼吸灯）             → rememberInfiniteTransition + infiniteRepeatable
└─ 手势跟手 / 惯性 fling / 可打断           → Animatable（snapTo / animateTo / animateDecay）

节奏由 AnimationSpec 决定：tween / spring / keyframes / snap (+ repeatable)
```

---

## 六、实践清单

**必须做**

- [ ] 给所有 `rememberInfiniteTransition` / `InfiniteTransition.animate*` 传 `label`（旧无参重载已 HIDDEN）。
- [ ] 高频变化的位移 / 缩放 / 旋转 / 透明度用 `graphicsLayer { }` / `offset { }` 的 lambda 版本。
- [ ] `Animatable`、`VelocityTracker`、`connection/dispatcher` 一类的有状态对象一律 `remember` 复用。
- [ ] 用 `LazyItemScope.animateItem` 时给每个 item 提供稳定且唯一的 `key`。
- [ ] 手势动画用 `Animatable`：`snapTo` 跟手、`animateDecay` 惯性、`animateTo` 归位，`updateBounds` 限幅。

**必须避免**

- [ ] 用 `animate*AsState` 做手势跟手 —— 会「追不上手指」，且没有速度、不可无缝打断。
- [ ] 把 `LazyColumn` 放进纵向 `verticalScroll` 的 `Column`（无限高度约束，必崩）。
- [ ] 用 `size` / `padding` 去做每帧位移（触发重测量），而不用 `graphicsLayer` / `offset`。
- [ ] 抄旧教程里无 `label` 的 `rememberInfiniteTransition()`、或已改名的 `animateItemPlacement`。
- [ ] 在 `keyframes` 里指望「相对位移」—— 关键帧是绝对值，反向播放会重走脚本。

---

## 七、延伸阅读

- `Animatable.kt`（`animation-core` sources jar）—— `snapTo` / `animateTo` / `animateDecay` / `updateBounds`
  的 KDoc，以及内部 `mutatorMutex` 如何实现「后发起者取消前者」。
- `Transition.kt`（`animation-core` / `animation`）—— `updateTransition` 的 `Segment` 作用域与
  `Transition<S>.animate*`、`InfiniteTransition.animate*` 的定义。
- `EnterExitTransition.kt`（`animation`）—— `fadeIn/slideIn/expandIn/scaleIn` 等原子过渡，
  看清哪些改绘制、哪些改布局尺寸。
- `AnimatedContent.kt` —— `ContentTransform` 与 `togetherWith` 的编排语义。
- `LazyItemScope.kt`（`foundation`）—— `animateItem(fadeInSpec, placementSpec, fadeOutSpec)` 三个规格的含义。
