# Compose 底层 · 关键字（operator / infix / invoke / inline / crossinline / noinline）

本目录用 5 个可交互示例，讲清 `operator`、`infix`、`invoke`、`inline`、`crossinline`、`noinline`
这几个 **Kotlin 关键字**在 Compose 里的应用场景——它们不是 Compose 专有的语法，
却是 Compose 那套「看起来像声明、写起来像自然语言」的 DSL 背后的**隐形骨架**。

所有代码基于本项目实际依赖的版本（均已解包 sources jar 逐个核实签名）：

| 依赖 | 版本 |
| --- | --- |
| Compose BOM | `2026.02.01` |
| `androidx.compose.ui:ui` / `ui-unit` | `1.10.4` |
| `androidx.compose.foundation:foundation` | `1.10.4` |
| `androidx.compose.material3:material3` | `1.4.0` |
| `androidx.compose.animation:animation` | `1.10.2` |

> ⚠️ **版本提醒（核实结论，别照抄旧教程）**
> - `CompositionLocalProvider` 在 1.10.4 **不是** `inline`（很多旧文章误传它是）；
>   真正 `inline` 的是 `CompositionLocal.current`（`inline val`）与新增的 `withCompositionLocal`。
> - `Modifier` 组合两个修饰符用的是 **`infix fun then`**（`a then b`），**没有**重载 `+`；
>   会重载 `+` 的是 `Dp` 与 `EnterTransition` / `ExitTransition`（`operator fun plus`）。
> - `then` / `provides` / `togetherWith` 都是 **`infix`**；`EnterTransition.with` 是旧名，已弃用并改名为 `togetherWith`。
> - `Dp` 的全部算术（`plus/minus/times/div/unaryMinus/compareTo`）都是 `@Stable inline operator fun`；
>   `Int.dp` / `Float.dp` 是 `inline val`；`Int.times(Dp)` 等是**扩展**函数，用 `2 * size` 需 `import androidx.compose.ui.unit.times`。

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
            ├─ Keywords      KeywordsHub()           ← 本模块
            └─ Settings      SettingsScreen()        DataStore 设置页
```

主页面是一个 Topic 列表，点击 **Compose 底层关键字**（`Icons.Filled.Code`）进入 `KeywordsHub`，
再点击列表里任意一项进入具体示例；用系统返回键或左上角箭头逐级退回，最终回到主列表。

> 示例⑤（crossinline / noinline）这类「编译期行为」无法直接画出来，
> 于是把演示逻辑写成产出 `List<String>` 的函数，跑一遍后用 `LogBox` 把「运行结果」渲染到屏幕上，
> 让看不见的机制变得可见；noinline 部分还做成「入队 / 延迟执行」的可交互队列。

---

## 二、核心心智模型：每个关键字解决什么问题

| 关键字 | 一句话 | 解锁的能力 | Compose 里的典型场景 |
| --- | --- | --- | --- |
| **`operator`** | 让自定义类型用上 `+ - * / [] < > in ..` 等符号 | 符号语法糖（`a+b` → `a.plus(b)`） | `16.dp + 8.dp`、`fadeIn() + slideIn()`、`a > b` 尺寸比较 |
| **`infix`** | 把 `a.foo(b)` 写成 `a foo b`（省点号括号） | 中缀语法糖，读起来像自然语言 | `a then b`、`LocalX provides v`、`fadeIn() togetherWith fadeOut()` |
| **`invoke`** | 让**对象**像函数一样被 `obj()` 调用 | 可调用对象 / 重载 / 函数类型统一 | 每个 `content()` 插槽、工厂对象、`@Composable` 可调用组件 |
| **`inline`** | 把函数体展开到调用点，不生成真实调用 | 零分配 lambda、`reified` 泛型、非局部返回 | `Dp` 运算、`CompositionLocal.current`、内部 `reified` 节点查找 |
| **`crossinline`** | inline 的 lambda 被转发进**另一层上下文**执行 | 允许嵌套调用，但**禁止**非局部返回 | 把用户 lambda 包进另一层作用域 / receiver 再执行的封装 |
| **`noinline`** | inline 函数里，某个 lambda **不展开**、保留为真实对象 | 可存储 / 传递 / 延迟 / 异步调用 | 需要把 lambda 记住、稍后再 invoke 的场景 |

一句话记忆：

> **operator 让类型「会运算」，infix 让调用「像读句子」，invoke 让对象「会调用」，
> inline 让 lambda「零开销」，crossinline / noinline 是 inline 在「嵌套 / 存储」场景下的两个安全阀。**

### 2.1 为什么这几个关键字对 Compose 尤其重要

Compose 的写法有两个显著特征，正好各由一组关键字支撑：

- **满屏符号化的链式 / 组合表达**（`Modifier.padding(8.dp).size(40.dp)`、`fadeIn() + slideIn()`、`16.dp * 2`）
  → 靠 `operator`（运算）与 `infix`（中缀）让 API 读起来像自然语言；
- **满屏 lambda**（`content`、`onClick`、各种 `Spec`/`Builder`/`Scope`）
  → 靠 `inline` 消除 lambda 对象分配与虚调用，靠 `invoke` 统一「函数」与「可调用对象」的调用方式，
    再靠 `crossinline` / `noinline` 处理「lambda 要被嵌套执行或存起来」的边界情况。

---

## 三、文件结构与职责

```
keywords/
├── KeywordsHub.kt            入口：话题列表 + 二级页面切换（含 BackHandler）
├── OperatorDemo.kt           示例①：operator —— Dp 真实运算 + 自定义 Spacing 全套约定 + infix then 对比
├── InfixDemo.kt              示例②：infix —— 三条规则 + 自定义 infix + then/provides/togetherWith 实时演示
├── InvokeDemo.kt             示例③：invoke —— 可调用对象（含重载）/ content() 插槽 / @Composable 可调用对象
├── InlineDemo.kt             示例④：inline —— 零分配 / reified / 非局部返回 + CompositionLocal 实时演示
├── LambdaModifiersDemo.kt    示例⑤：crossinline / noinline —— 嵌套上下文转发 / 存储并延迟执行
├── KeywordsWidgets.kt        包内复用的小组件（CodeBlock / LogBox / Stage / ButtonRow / SectionTitle …）
└── README.md                 本文件
```

---

## 四、逐个示例的实现方式

### 示例① `operator`：运算符重载

**心智**：给函数加 `operator` 修饰 + 约定名，编译器就会把符号翻译成函数调用。它只是**语法糖**，
`a + b` 与 `a.plus(b)` 完全等价——好处是可读性，让领域类型用起来像内置类型。

约定对照（本示例覆盖到的）：

| 符号 | 函数名 | 符号 | 函数名 |
| --- | --- | --- | --- |
| `a + b` | `plus` | `a > b` `a <= b` … | `compareTo` |
| `a - b` | `minus` | `a[i]` | `get` / `set` |
| `a * b` | `times` | `x in a` | `contains` |
| `a / b` | `div` | `a..b` | `rangeTo` |
| `-a` | `unaryMinus` | `a()` | `invoke`（见示例③） |

**实现方式**：定义一个 `Spacing(value: Dp)` 类型，给它装上 `plus / minus / times / unaryMinus /
compareTo / get / contains` 一整套 operator，让「一段间距」也能像数字一样运算；同时用**真实的 `Dp`**
驱动方块尺寸，点按钮改变基准单位，直观看到「符号运算的结果真的在改布局」。

```kotlin
class Spacing(val value: Dp) {
    operator fun plus(other: Spacing) = Spacing(value + other.value)      // a + b
    operator fun times(factor: Int)   = Spacing(value * factor)           // a * n
    operator fun unaryMinus()         = Spacing(-value)                   // -a
    operator fun compareTo(other: Spacing) = value.compareTo(other.value) // a > b
    operator fun get(index: Int): Dp  = value * index                     // a[i]
    operator fun contains(other: Dp)  = other >= 0.dp && other <= value   // x in a
}

val spacing = Spacing(16.dp)
spacing * 2        // Spacing(32.dp)
spacing[3]         // 48.dp —— get 约定
12.dp in spacing   // true  —— contains 约定
```

**Compose 真实场景（已核实）**

```kotlin
16.dp + 8.dp          // Dp.plus  —— @Stable inline operator fun
2 * 12.dp             // Int.times(Dp) —— 扩展，需要 import androidx.compose.ui.unit.times
fadeIn() + slideIn()  // EnterTransition.plus —— public operator fun plus
```

> **对比要点**：`Modifier` 组合并**没有**重载 `+`，而是 `infix fun then`（`infix` 详解见示例②）：
> `Modifier.padding(8.dp) then Modifier.size(40.dp)`。`infix` 允许省略点号与括号，
> 但 `a then b` **必须写在同一行**——换行以 `then` 开头会被编译器当成上一条语句已结束。

### 示例② `infix`：中缀调用

**心智**：`infix` 让 `a.foo(b)` 可以写成 `a foo b`——省掉点号和括号，读起来像自然语言。
它**没有引入任何新的运算语义**，只是普通函数调用的语法糖；`a foo b` 与 `a.foo(b)` 完全等价。

**三条硬性规则（编译器强制）**：① 必须是**成员函数**或**扩展函数**（要有接收者，不能是无接收者的顶层函数）；
② 只能有**一个**参数；③ 该参数**不能**是 `vararg`、**不能**有默认值。

```kotlin
infix fun <T> T.shouldBe(expected: T) = this == expected   // 扩展 + 单参 → 合法
3 shouldBe 3            // == 3.shouldBe(3) == true
// ✗ infix fun pair(a: Int, b: Int)  —— 无接收者的顶层函数 + 两个参数
// ✗ infix fun Int.pow(n: Int = 2)   —— 参数带默认值
```

**Compose 真实场景（1.10.4 已核实，成员 infix 与扩展 infix 都有）**

```kotlin
// 成员 infix（定义在类型内部）
infix fun Modifier.then(other: Modifier): Modifier                 // modifierA then modifierB
infix fun <T> CompositionLocal<T>.provides(v: T): ProvidedValue<T> // LocalX provides v

// 扩展 infix（定义在类型外部）
infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform  // fadeIn() togetherWith fadeOut()
infix fun ContentTransform.using(size: SizeTransform?): ContentTransform
```

**实现方式**：`LogBox` 打印自定义 `shouldBe`、标准库里天天在用的 infix（`to` / `downTo` / `until` / `step` / `zip`）、
以及 `fadeIn() togetherWith fadeOut()` 的结果类型；再用两个**实时**演示落地——
`LocalAccent provides color`（成员 infix）切换强调色、`left then Modifier.padding(...)`（成员 infix）组合 Modifier。

> **要点与坑**
> - 多个 infix 链式调用**从左到右**结合：`a f b g c` == `(a f b) g c`（如 `fadeIn() togetherWith fadeOut() using …`）；
> - 点号 / 后缀调用绑定比 infix **更紧**：`Modifier.x().y() then z` == `(Modifier.x().y()) then z`；
> - `a foo b` **必须写在同一行**——换行以 infix 名开头会被当成上一条语句已结束（`Unresolved reference`）；
> - infix 可与 `inline` 叠加，Compose 内部就有 `inline infix fun NodeKind.or`；
> - `EnterTransition.with` 是**旧名**，已弃用并改名为 `togetherWith`。

### 示例③ `invoke`：调用约定

**心智**：声明 `operator fun invoke(...)` 后，类的**实例**就能被当函数调用：`obj(args)` == `obj.invoke(args)`。
一个类可以声明**多个不同签名**的 `invoke`，形成「可调用对象的重载」。

**为什么它对 Compose 是隐形支柱**：你写下的每一个 `content: @Composable () -> Unit` 插槽，
接收方内部都是用 `content()` 执行的——那正是函数类型的 `invoke`。函数和「可调用对象」因此有了统一的调用语法。

**实现方式**：三段演示层层递进。

```kotlin
// (1) 可调用对象 + 重载
class PasswordValidator(private val minLength: Int) {
    operator fun invoke(input: String) = input.length >= minLength
    operator fun invoke(input: String, minDigits: Int) =           // 重载
        invoke(input) && input.count { it.isDigit() } >= minDigits
}
val v = PasswordValidator(6)
v("abcdef")          // == v.invoke("abcdef")

// (2) content 插槽被反复 invoke —— 复现 Compose 所有 content 的执行方式
@Composable
fun Repeat(times: Int, content: @Composable () -> Unit) {
    Column { repeat(times) { content() } }   // content() == content.invoke()
}

// (3) 可调用的 @Composable 对象：像组件一样 chip { ... } 调用
class ActionChip(private val label: String) {
    @Composable operator fun invoke(onClick: () -> Unit) { Button(onClick = onClick) { Text(label) } }
}
val chip = ActionChip("点我 +1")
chip { taps++ }      // == chip.invoke { taps++ }
```

界面上用 `OutlinedTextField` 做**实时校验**：每次输入都会触发 `validator(text)`（即 `invoke`），
把「对象像函数一样被调用」这件事变成看得见摸得着的交互。

### 示例④ `inline`：内联函数，Compose 的零开销抽象

**心智**：`inline` 让编译器把函数体**直接展开到调用点**。对高阶函数（参数是 lambda）而言，
它解锁三件事：

1. **消除 lambda 对象分配**：普通高阶函数每次调用都 `new` 一个 `Function` 对象 + 一次虚调用；
   inline 后 lambda 就地展开，零分配、零虚调用。这是「Compose 满屏 lambda 却不卡」的根本原因。
2. **`reified` 具体化泛型**：只有 inline 函数能用 `reified T`，在函数体里拿到**真实类型**
   （`T::class`、`is T`）——因为展开后类型信息还在，没被擦除。
3. **非局部返回**：inline 函数里的 lambda 可以直接 `return` 它所在的**外层**函数。

**实现方式**：三个纯 Kotlin 演示 + 一个 Compose 实时演示。

```kotlin
// reified：读到真实类型（否则 T 被擦除成 Object）
inline fun <reified T> describe(value: T) = "${T::class.simpleName} = $value"
describe(42)                 // "Int = 42"

// 非局部返回：forEach 是 inline，所以 lambda 里的 return 跳出的是 firstPositive 本身
fun firstPositive(nums: List<Int>): Int {
    nums.forEach { if (it > 0) return it }
    return -1
}
```

Compose 实时演示用 `CompositionLocal`：父层 `provides` 一个值，子组件用 `.current` 直接读取，不必层层传参。

**Compose 真实场景（1.10.4 已核实）**

```kotlin
@Stable inline operator fun Dp.plus(other: Dp) = Dp(value + other.value)  // 尺寸运算零分配
inline val Int.dp: Dp                                                     // .dp 也是 inline
val current: T inline get() = ...                                         // LocalXxx.current
inline fun <reified T> NodeChain.headToTail(type: NodeKind<T>, ...)       // 内部按类型查找节点
```

> **边界**：inline 不是万能的——函数体太大会导致**代码膨胀**；inline 的 lambda **不能递归**，
> 也**不能存进字段/集合再异步调用**。当 lambda 需要被「存起来 / 转发到别的上下文」时，
> 就得请出示例⑤的 `noinline` / `crossinline`。

### 示例⑤ `crossinline` / `noinline`：给 inline 的 lambda 参数加约束

**心智**：这两个修饰符只出现在 **inline 函数的 lambda 形参**前，用来微调「这个 lambda 怎么被处理」：

| 形态 | 是否展开 | 非局部返回 | 能否存储/异步 | 何时用 |
| --- | --- | --- | --- | --- |
| 普通 inline lambda | 就地展开 | ✅ 允许 | ❌ 不能 | 就地执行、想用 `return` 跳出外层 |
| `crossinline` | 就地展开进**嵌套上下文** | ❌ 禁止 | ❌ 不能 | 要把 lambda 包进**另一层** lambda / 对象里再执行 |
| `noinline` | **不展开**，保留真实对象 | ❌ 无意义 | ✅ 能 | 要把 lambda **存起来 / 延迟 / 异步**调用 |

**实现方式**：各写一个最小可跑的 inline 函数，把行为差异通过日志 / 交互队列展示出来。

```kotlin
// crossinline：body 被包进 Runnable（另一层 lambda）后才 run —— 这就是「嵌套上下文」
inline fun runWrapped(crossinline body: () -> Unit) {
    val runnable = Runnable { body() }   // 若 body 不加 crossinline，这里编译不过
    runnable.run()
}

// noinline：task 要存进队列稍后执行；log 是普通 inline lambda（对照：同函数里两者并存）
inline fun enqueue(
    queue: MutableList<() -> String>,
    log: (String) -> Unit,        // 普通 inline：就地展开
    noinline task: () -> String,  // 需要存储 → 必须 noinline
) {
    queue += task                 // 没有 noinline 就编译不过
    log("已入队")
}
```

界面上把 `enqueue` / `drainQueue` 做成**可交互队列**：点「入队」只把 `task` 存起来（此时不执行），
点「执行」才逐个 `invoke`——「存储 + 延迟调用」这个 noinline 的核心价值一目了然。

> **踩坑提醒（已实测）**：一个 inline 函数如果**所有** lambda 参数都标了 `noinline`，
> 编译器会告警 “Expected performance impact from inlining is insignificant.”——
> 因为把 lambda 全排除后，内联函数体本身已无收益。本示例特意保留一个普通 inline 的 `log` 参数，
> 既是真实世界的常见写法，也避免了该告警。

---

## 五、选型速查

```
想让类型支持某种符号运算（+ - * [] < in ..）？        → operator + 约定函数名
只是想让 a foo b 这种中缀读法（不要符号语义）？        → infix（如 Modifier.then、LocalX provides v）
想让对象能被 obj() 直接调用 / 做「可调用组件」？       → operator fun invoke
高阶函数满屏 lambda，想零分配 / 用 reified / 非局部返回？ → inline
   ├─ lambda 要被包进「另一层 lambda / 对象」里执行？   → 该参数加 crossinline（禁非局部返回）
   └─ lambda 要被「存起来 / 延迟 / 异步」调用？         → 该参数加 noinline（保留真实对象）
```

---

## 六、实践清单

**必须做**

- [ ] `operator` 只用**约定函数名**（`plus`/`get`/`invoke`…），且语义要符合直觉，别用 `+` 做「删除」这种反直觉操作。
- [ ] `infix` 函数满足三条规则（成员或扩展 + 单参 + 参数无 vararg/默认值），`a foo b` 写在同一行，且读起来通顺。
- [ ] 高频调用、参数是 lambda 的小函数优先 `inline`，以消除分配；需要按类型分派时配 `reified`。
- [ ] 需要把 lambda 存进集合 / 字段、或延迟 / 异步执行时，给该参数加 `noinline`。
- [ ] 需要把 lambda 转发进另一层作用域（如带 receiver 的构建器）执行时，加 `crossinline`。
- [ ] `2 * someDp` 这类 `Int/Float.times(Dp)` 是**扩展**函数，记得 `import androidx.compose.ui.unit.times`。

**必须避免**

- [ ] 对**函数体很大**或**没有 lambda 参数**的函数滥用 `inline`（只会造成代码膨胀）。
- [ ] 让一个 inline 函数的**所有** lambda 参数都 `noinline`（触发 “inlining is insignificant” 告警）。
- [ ] 在 `crossinline` 的 lambda 里写 `return` 去返回外层函数（编译不过）。
- [ ] 以为 `Modifier` 能用 `+` 组合（它用 `infix then`）；也别把 `a then b` 拆成多行写。
- [ ] 照抄旧教程说 `CompositionLocalProvider` 是 inline（1.10.4 里它不是；`current` 才是 `inline val`）。

---

## 七、延伸阅读

- `Modifier.kt`（`ui` sources jar）—— `infix fun then`、`CombinedModifier`、`foldIn/foldOut/any/all`。
- `Dp.kt`（`ui-unit` sources jar）—— `@Stable inline operator fun plus/minus/times/div/unaryMinus/compareTo`、`inline val Int.dp`。
- `EnterExitTransition.kt`（`animation` sources jar）—— `public operator fun plus` 如何把原子进出场组合起来。
- `AnimatedContent.kt`（`animation` sources jar）—— `infix fun EnterTransition.togetherWith` / `ContentTransform.using`（旧名 `with` 已弃用）。
- `CompositionLocal.kt`（`runtime` sources jar）—— `inline val current`、`infix fun provides`、以及 `inline fun withCompositionLocal`。
- `NodeChain.kt` / `NodeCoordinator.kt`（`ui` sources jar）—— `inline fun <reified T>` 如何按类型遍历节点，是 `reified` 的工业级用例。
- Kotlin 官方文档「Operator overloading」「Infix notation」「Inline functions」—— 约定函数名全表、infix 三规则与 inline/crossinline/noinline 规则。
