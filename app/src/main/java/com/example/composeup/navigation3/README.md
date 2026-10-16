# Jetpack Navigation 3（返回栈 / ViewModel 作用域 / 返回结果 / metadata / 模块化 / Deep Links / Scenes / NavEntryDecorator）

本目录用 **8 个可交互示例**，把 Jetpack **Navigation 3**（nav3）官方文档讲的能力逐个落地：
它不是把 API 罗列一遍，而是串成一条主线——**从一个「你自己持有的返回栈」出发，逐步长出一套完整的导航系统**。

nav3 与 Navigation 2 最大的区别是一句话：

> **返回栈归你所有。** 你持有一个可观察的 `List<NavKey>`，`NavDisplay` 观察它、把 key 解析成 `NavEntry` 并渲染。
> 库不再替你藏一个 `NavController`，而是把「栈、状态、布局、装饰」都摊开给你，让你按需组合。

理解了这一点，后面 8 个主题都是围绕它的自然延伸。

依赖版本（本项目实际使用，已核实）：

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Kotlin | `2.4.21` | 语言 / `@Serializable` |
| Compose BOM | `2026.09.00` | Compose 运行时与 UI |
| `androidx.navigation3:navigation3-runtime` | `1.2.0` | `NavKey` / `NavBackStack` / `NavEntry` / 装饰器 / metadata / **deeplink** / **result** |
| `androidx.navigation3:navigation3-ui` | `1.2.0` | `NavDisplay` / `Scene` / `SceneStrategy` / `SceneDecoratorStrategy` / `DialogSceneStrategy` |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | `2.11.0` | `rememberViewModelStoreNavEntryDecorator`（VM 作用域到 NavEntry） |
| `androidx.compose.material3.adaptive:adaptive-navigation3` | `1.3.0` | Material 自适应 List-Detail Scene（本项目已引入，示例以手写 Scene 讲原理为主） |
| `org.jetbrains.kotlinx:kotlinx-serialization` | — | key 的序列化（保存栈、deeplink 抽参） |

> ⚠️ **版本提醒（核实结论，均以解包 `navigation3-*:1.2.0` 的 class 与 sources jar 为准）**
> - `ResultEventBus`（`sendResult` / `ResultEffect` / `conflateAsState`）与 `DeepLinkMatcher` 系列都是 **1.2.0 新增**；网上大量 nav3 教程仍停留在旧的自定义 ResultStore / 手写 URI 解析，勿混用。
> - `rememberNavBackStack(...)` 返回的是 **`NavBackStack<NavKey>`**，不是 `SnapshotStateList`；它实现了 `List` + `StateObject`，可直接传给 `NavDisplay(backStack = ...)`。
> - `entry<Key> { }` 是 **`EntryProviderScope` 的成员函数**（inline reified），在 `entryProvider { }` 里直接写即可，**不需要 import `entry`**（没有这个顶层符号）。
> - `SceneStrategy.calculateScene` / `SceneDecoratorStrategy.decorateScene` 都是 **`Scope` 的扩展函数**：`override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>?`。
> - `NavDisplay` 1.2.0 的完整参数：`backStack, modifier, contentAlignment, onBack, entryDecorators, sceneStrategies, sceneDecoratorStrategies, sharedTransitionScope, sizeTransform, transitionSpec, popTransitionSpec, predictivePopTransitionSpec, entryProvider`。

---

## 一、怎么跑起来

```
MainActivity
  └─ ComposeUpTheme
       └─ ComposeUpRoot            ← 主页面 = Topic 列表
            ├─ ...
            └─ Navigation3  Nav3Hub()   ← 本模块（8 个示例的话题列表）
```

主页面点 **Jetpack Navigation 3** 进入 `Nav3Hub`，再点列表里任意一项进入具体示例；
每个示例内部都是一个**真实的 `NavDisplay` + 独立返回栈**，用左上角箭头或系统返回键逐级退回。

另有 **`Nav3Activity`**：一个真实的 deep link 入口 Activity（已在 `AndroidManifest.xml` 注册 `scheme=app / host=www.composeup.com`）。
用 adb 触发，验证「从外部链接进入 + 合成返回栈」：

```bash
adb shell am start -a android.intent.action.VIEW -d "app://www.composeup.com/users/42"
adb shell am start -a android.intent.action.VIEW -d "app://www.composeup.com/detail/7"
```

> nav3 的很多行为发生在**幕后**（栈的增删、装饰器的 decorate/onPop、结果的收发、深链的匹配、Scene 的计算）。
> 静态 UI 表达不了，所以本模块统一用两类教学容器把它们「画出来」：
> - `BackStackView`：把当前返回栈的每个 key 画成一摞卡片（栈顶在上）；
> - `LogBox`：把每一次事件按时间顺序打印到屏幕（最新在上）。

---

## 二、核心心智模型

### 2.1 三个基本角色

| 角色 | 是什么 | 关键点 |
| --- | --- | --- |
| **NavKey** | 内容的**引用**（不是内容本身） | `@Serializable` + 实现 `NavKey`，通常是简单 data class/object |
| **NavEntry** | 一个目的地的**内容 + metadata** | 由 `entryProvider` 把 key 解析而来；含 `contentKey`、`Content()`、`metadata` |
| **NavDisplay** | 观察返回栈、渲染 UI 的**容器** | 把栈顶（或 Scene 计算的多个）entry 显示出来，管动画与返回 |

一句话记忆：

> **栈里放 key（引用），entryProvider 把 key 变成 NavEntry（内容），NavDisplay 负责渲染。**
> push = `backStack.add(key)`，pop = `backStack.removeLastOrNull()`——就是这么朴素。

### 2.2 装饰器（Decorator）：给每个 NavEntry「统一加料」

`NavEntryDecorator` 用一个 composable 包裹栈里**每一个** NavEntry。它是 nav3 能力扩展的主干：

- `rememberSaveableStateHolderNavEntryDecorator()`：默认，负责 `rememberSaveable` 的状态保存（**放列表第一个**）；
- `rememberViewModelStoreNavEntryDecorator()`：给每个 NavEntry 独立 `ViewModelStore`（→ 示例②）；
- `rememberResultEventBusNavEntryDecorator()`：给每个 NavEntry 提供 `LocalResultEventBus`（→ 示例③）；
- 自定义：日志 / 埋点 / 依赖注入 / 资源清理（→ 示例⑧）。

### 2.3 Scene 与 SceneStrategy：布局由「策略」计算

`Scene` 是渲染一个或多个 NavEntry 的**布局单元**。`NavDisplay` 把当前栈解析出的 `List<NavEntry>`
依次交给 `sceneStrategies` 里的每个 `SceneStrategy.calculateScene(...)`，谁先返回非 null 就用谁；
全返回 null 则回退默认 `SinglePaneSceneStrategy`（单页）。`SceneDecoratorStrategy` 再对**算出来的 Scene**
做第二阶段加工（→ 示例⑦）。

---

## 三、8 个示例逐个拆解

### ① 管理返回栈 · `BackStackDemo.kt`

**解决什么**：nav3 的地基——你如何持有并操作返回栈。

- 用 `rememberNavBackStack(HomeKey)` 建一个可跨配置变更 / 进程死亡存活的栈；
- `add` 前进、`removeLastOrNull` 后退、`clear + addAll` 直接改写；
- `BackStackView` 实时把栈画出来，`NavDisplay` 同步渲染栈顶。

```kotlin
val backStack = rememberNavBackStack(HomeKey)
backStack.add(DetailKey("1"))     // push
backStack.removeLastOrNull()      // pop
NavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() }, entryProvider = ...)
```

### ② 把 ViewModel 作用域限制在 NavEntry · `ViewModelScopingDemo.kt`

**解决什么**：默认 VM 绑到 Activity，多个目的地会共用；我想让 VM 随**某个 key** 生灭。

- `entryDecorators` 里加 `rememberViewModelStoreNavEntryDecorator()`，每个 NavEntry 拥有独立 `ViewModelStore`；
- entry 内容里 `viewModel(factory = ...)` 自动按 `contentKey` 隔离；
- 日志实时打印 VM 的 `created` / `onCleared`。

**动手验证**：打开 Detail(#1) 加计数 → 返回 → 再进 Detail(#1)，计数还在（复用）；Detail(#2) 从 0 开始（独立）；
把 Detail pop 出栈，日志出现该 VM 的 `onCleared`。

### ③ 返回结果 · `ReturnResultsDemo.kt`

**解决什么**：目的地之间回传数据（选地址、选颜色、选联系人……）。1.2.0 起用 `ResultEventBus`。

- 前提：`entryDecorators` 加 `rememberResultEventBusNavEntryDecorator()`（提供 `LocalResultEventBus`）；
- **事件式** `ResultEffect<T>(resultKey = "...") { }`：按序处理每条结果，effect body 在协程里可挂起；
- **状态式** `resultBus.conflateAsState<T>(resultKey, defaultValue)`：只保留最新值，暴露为 Compose State；
- 发送方：`resultBus.sendResult(resultKey, value)` 后 pop。

> ⚠️ 显式 key 与类型派生 key **不互通**：收发双方要么都用 `resultKey=`，要么都不传（用类型 KClass 派生）。
> `ResultEventBus` 是内存总线，结果不自动跨进程死亡保存。

### ④ 配置 metadata · `MetadataDemo.kt`

**解决什么**：给 NavEntry 挂**类型安全**的元信息，让 NavDisplay / Scene 据此决定「怎么显示」。

- metadata 本质是 `Map<String, Any>`，用 **metadata DSL + `NavMetadataKey`** 类型安全地读写：
  `metadata { put(SomeKey, value) }` / `map[SomeKey]` / `map.contains(SomeKey)`；
- 最经典用途：为单个目的地覆盖转场——`put(NavDisplay.TransitionKey) { fadeIn() togetherWith fadeOut() }`；
- 示例三个页面分别是淡入淡出、竖向滑入、默认（无 metadata），肉眼即可分辨。

### ⑤ 模块化导航 · `ModularizationDemo.kt`

**解决什么**：App 变大后，key 与 entryProvider 不能堆在一个文件里。

- 官方推荐每个功能拆 **api（只放 key）+ impl（放 entryProvider builder 与内容）**；
- impl 暴露一个 `EntryProviderScope<NavKey>` 的扩展 builder；
- 主 App 只写一个 `entryProvider { featureAEntries(...); featureBEntries(...) }` 把它们拼起来，**不感知任何具体 key**；
- 规模化时改用 DI 多绑定：各模块把 builder 贡献进 `Set<EntryProviderScope<NavKey>.() -> Unit>`，主 App `forEach { it() }`。

### ⑥ 支持 Deep Links · `DeepLinksDemo.kt` + `Nav3Activity.kt`

**解决什么**：从外部链接（浏览器、adb、其它 App）直接进入某个目的地。1.2.0 起用 `DeepLinkRequest` + `DeepLinkMatcher`。

流程：**定义 matcher → 从 Intent/URI 造 request → 所有 matcher 匹配取最优 → 生成返回栈**。

- `UriDeepLinkMatcher(DeepLinkUri("www.example.com/users/{id}"), serializer<UserProfileKey>())`：按模式匹配并用 serialization 抽取 `{id}` 进 key；
- `StaticKeyDeepLinkMatcher(key, listOf(DeepLinkMatcher.actionFilter(...)))`：匹配固定链接 / 特定 action（无参数）；
- `.withBackStack { matchResult -> listOf(HomeKey, matchResult.key) }`：把匹配结果包成**合成返回栈**（从深链进来后按返回键先回首页）；
- **自定义 matcher**：继承 `DeepLinkMatcher<T, R>` 实现 `matchRequest`——示例里的 `TelDeepLinkMatcher` 处理 `tel:` 这类 opaque URI（`UriDeepLinkMatcher` 不支持）；
- 多个 matcher 命中同一请求时，靠 `MatchResult` 的 `Comparable` 排序，`matchers.mapNotNull { it.match(request) }.maxOrNull()` 取最优。

`DeepLinksDemo` 做成**可交互输入框**方便随时试不同 URI；`Nav3Activity` 则演示真实的 onCreate 匹配（配合 manifest 的 intent-filter）。

### ⑦ Scenes 与 scene decorators · `ScenesDemo.kt`

**解决什么**：自定义布局（对话框、双栏、平板多窗），以及对布局做统一加工。

- **自定义 Scene + Strategy**：`TwoPaneSceneStrategy` 在「宽屏 + 栈里有 list/detail metadata」时返回一个把两个 NavEntry 并排渲染的 `TwoPaneScene`，否则返回 null 交给默认策略；
- **内置 `DialogSceneStrategy`**：给 entry 挂 `DialogSceneStrategy.dialog(DialogProperties(...))` metadata 就弹成对话框（`OverlayScene`，渲染在独立窗口）；
- **scene decorator**：`TopBarSceneDecorator` 对算出来的 Scene 统一加一个顶栏；遇到 `OverlayScene` 主动跳过（装饰对话框没意义）。

> 自定义 Scene 要正确实现 `equals`/`hashCode`（用 `data class` 最省事），`key` 建议从「被装饰 Scene 的类 + key」派生以保留内建动画。切「宽屏模式」开关即可看双栏策略生效/失效。

### ⑧ 自定义 NavEntryDecorator · `NavEntryDecoratorDemo.kt`

**解决什么**：对**每个**目的地做同一件事（日志、埋点、注入依赖、清理资源）。

- 继承 `NavEntryDecorator<T>`，实现两个回调：
  - `decorate = { entry -> ... entry.Content() }`：entry 进入组合时调用，可用 `CompositionLocalProvider` 注入依赖，**必须调用 `entry.Content()`**；
  - `onPop = { contentKey -> ... }`：entry 离开组合时调用，用来清理该 key 关联的状态；
- 示例的 `LoggingNavEntryDecorator` 把进入/离开打进日志，并注入 `contentKey` 供内容读取；
- ⚠️ 除非你的装饰器自己提供 `SaveableStateProvider`，否则 `rememberSaveableStateHolderNavEntryDecorator()` 要放装饰器列表**第一个**。

**何时不该用装饰器**：只给单个目的地传依赖、或依赖作用域大于整个栈——那应直接在创建 NavEntry 时传。

---

## 四、目录结构

```
navigation3/
├── Nav3Hub.kt                 话题列表 + 分发（对标 flow 的 FlowHub）
├── Nav3Widgets.kt             共享教学组件：ScreenArea / BackStackView / LogBox / DemoLog / Pane / CodeBlock ...
├── Nav3Keys.kt                共享的 @Serializable NavKey（HomeKey / DetailKey / UserProfileKey / DialerKey ...）
├── BackStackDemo.kt           ① 管理返回栈
├── ViewModelScopingDemo.kt    ② ViewModel 作用域限制在 NavEntry
├── ReturnResultsDemo.kt       ③ 返回结果（ResultEventBus）
├── MetadataDemo.kt            ④ 配置 metadata
├── ModularizationDemo.kt      ⑤ 模块化导航
├── DeepLinksDemo.kt           ⑥ Deep Links（匹配 + 自定义 matcher，可交互）
├── ScenesDemo.kt              ⑦ Scenes 与 scene decorators
├── NavEntryDecoratorDemo.kt   ⑧ 自定义 NavEntryDecorator
├── Nav3Activity.kt            真实 deep link 入口 Activity（配合 manifest intent-filter）
└── README.md
```

---

## 五、关键洞察与踩坑记录

1. **API 核实方法**：写代码前先解包 `~/.gradle/caches` 里的 `navigation3-*:1.2.0` AAR，用 `javap -p` 看类与方法签名、用 `javap -v ... | grep 'C(...)N(...)'` 从 Kotlin metadata 里读**参数名**（`javap` 本身不显示参数名）。这是本模块所有 API 用法可靠的根本原因。
2. **`entry` 不用 import**：它是 `EntryProviderScope` 的成员（inline reified），在 `entryProvider { }` 里直接 `entry<Key> { }` 即可；误加 `import androidx.navigation3.runtime.entry` 会报 Unresolved。
3. **栈的类型**：`rememberNavBackStack` 返回 `NavBackStack<NavKey>`（实现 `List` + `StateObject`），别标注成 `SnapshotStateList`。
4. **装饰器顺序**：`rememberSaveableStateHolderNavEntryDecorator()` 永远放第一个，否则各 entry 里的 `rememberSaveable` 不会正确保存/恢复。
5. **ResultEventBus 寻址**：显式 key 与类型派生 key 互不相通，收发两端要一致。
6. **局部变量别和扩展函数同名**：`val log = ...` 再配 `SnapshotStateList<String>.log(...)` 扩展，调用 `log("x")` 会因「找不到隐式接收者」失败。本模块改用 `DemoLog` 包装类（既是 `List<String>` 又有 `operator fun invoke`）绕开该坑，调用点保持 `log("...")` / `LogBox(lines = log)` 的自然写法。
7. **Scene 的 key 与 equals**：自定义 Scene 用 `data class` 天然满足 `equals/hashCode`；scene decorator 返回的 Scene 其 `key` 应从被装饰 Scene 派生，否则内建转场动画会失效。
8. **NavDisplay 不允许空栈**：`backStack` 一旦被清空会抛 `IllegalArgumentException: NavDisplay backstack cannot be empty`。所以任何 pop / 「返回」操作都要守卫 `if (backStack.size > 1)` 再 `removeLastOrNull()`；栈底时的返回应改为退出示例（`onExit()`）或结束 Activity（`finish()`）。本模块 ① 的 pop 按钮、⑥ 深链单条栈的返回、`Nav3Activity` 的 `onBack` 均据此加了守卫。

---

## 六、延伸阅读（官方文档）

- Navigation 3 总览：<https://developer.android.com/guide/navigation/navigation-3>
- 基础与返回栈 / 保存状态：`.../basics`、`.../save-state`
- ViewModel 作用域、装饰器：`.../save-state`、`.../naventrydecorators`
- 返回结果：`.../return-results`
- metadata：`.../metadata`
- 模块化：`.../modularize`
- Deep Links：`.../deep-links`（含 `create-matchers` / `custom-matchers` / `uri-matcher`）
- Scenes 与装饰器：`.../scenes`、`.../scenes/scene-decorators`、`.../custom-layouts`
- 官方 recipes 代码集：<https://github.com/android/nav3-recipes>
