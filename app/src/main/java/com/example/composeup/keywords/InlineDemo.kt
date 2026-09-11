package com.example.composeup.keywords

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 示例④：inline —— 内联函数，Compose 的「零开销抽象」。
 *
 * 一句话：`inline` 让编译器把函数体**直接展开到调用点**，而不是生成一次真正的函数调用。
 * 对高阶函数（参数是 lambda）而言，这带来三个关键能力：
 *  1. **消除 lambda 对象分配**：普通高阶函数每次调用都会 new 一个 Function 对象；
 *     inline 后 lambda 被就地展开，没有额外对象、没有虚调用。Compose 满屏 lambda 却不卡，靠的就是它。
 *  2. **reified 具体化泛型**：只有 inline 函数能用 `reified T`，在函数体里拿到**真实类型**
 *     （`T::class`、`is T`），因为展开后类型信息还在，没有被擦除。
 *  3. **非局部返回**：inline 函数里的 lambda 可以直接 `return` 它所在的外层函数。
 *
 * Compose 里的真实例子（均已在本项目依赖的 1.10.4 源码中核实）：
 *  - `Dp`：`inline operator fun plus/times/...`、`inline val Int.dp` —— 尺寸运算零分配；
 *  - `CompositionLocal.current`：`inline val`（配合 @Composable getter）；
 *  - 内部节点查找：`NodeChain` / `NodeCoordinator` 用 `inline fun <reified T>` 按类型遍历节点；
 *  - `withCompositionLocal` / `withCompositionLocals`：`inline fun`。
 *  - 注意：`CompositionLocalProvider` 在 1.10.4 **不是** inline（旧教程常误传）。
 */
private inline fun <reified T> describe(value: T): String =
    "${T::class.simpleName} = $value"

/**
 * 非局部返回：`forEach` 是标准库的 inline 函数，
 * 所以它 lambda 里的 `return` 返回的是**外层** firstPositive，而不是 forEach。
 */
private fun firstPositive(numbers: List<Int>): Int {
    numbers.forEach { if (it > 0) return it } // ← 非局部返回，得益于 forEach 是 inline
    return -1
}

/** inline 包装：measure 就地展开 block，不为 block 分配函数对象。 */
private inline fun measure(label: String, block: () -> Any?): String =
    "$label = ${block()}"

/** 一个 CompositionLocal：默认英文问候。current 是 inline val。 */
private val LocalGreeting = staticCompositionLocalOf { "Hello" }

/** 子组件：读取 LocalGreeting.current（inline val），无需层层传参。 */
@Composable
private fun GreetingReader() {
    Text("  ↳ 子组件读到 LocalGreeting.current = 「${LocalGreeting.current}」")
}

@Composable
fun InlineDemo(modifier: Modifier = Modifier) {
    var chinese by rememberSaveable { mutableStateOf(false) }

    val reifiedLines = remember {
        listOf(
            $$"inline fun <reified T> describe(v: T) = \"${T::class.simpleName} = $v\"",
            "  describe(42)           -> ${describe(42)}",
            "  describe(\"文字\")        -> ${describe("文字")}",
            "  describe(3.14)         -> ${describe(3.14)}",
            "  describe(listOf(1, 2)) -> ${describe(listOf(1, 2))}",
            "  // 没有 inline+reified，T 会被擦除成 Object，拿不到 Int/String/Double",
        )
    }

    val nonLocalLines = remember {
        listOf(
            "fun firstPositive(nums: List<Int>): Int {",
            "    nums.forEach { if (it > 0) return it }   // forEach 是 inline",
            "    return -1",
            "}",
            "  firstPositive([-1,-2,7,3]) -> ${firstPositive(listOf(-1, -2, 7, 3))}   // return 直接跳出外层函数",
            "  firstPositive([-1,-2])     -> ${firstPositive(listOf(-1, -2))}",
            "",
            measure("measure(\"内联包装\")") { 6 * 7 },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① inline 解决什么")
        Note(
            "Compose 是「lambda 密集型」框架：content、onClick、各种 Spec/Builder 全是 lambda。" +
                "若每个高阶函数都生成 Function 对象并做虚调用，GC 压力和调用开销都会很大。" +
                "把这些函数标成 inline，编译器就地展开 lambda —— 零分配、零虚调用，" +
                "这就是 Compose「优雅写法」背后的性能保障。",
        )
        CodeBlock(
            """
            // 普通高阶函数：每次调用 new 一个 Function0 对象 + 一次虚调用
            fun run(block: () -> Unit) { block() }

            // inline：block 被就地展开，没有对象、没有虚调用
            inline fun run(block: () -> Unit) { block() }
            """.trimIndent(),
        )

        SectionTitle("② reified：inline 专属的具体化泛型")
        LogBox(lines = reifiedLines)

        SectionTitle("③ 非局部返回")
        LogBox(lines = nonLocalLines)

        SectionTitle("④ Compose 里的 inline（1.10.4 已核实）")
        CodeBlock(
            """
            // ui-unit：尺寸运算全是 inline operator，零分配
            @Stable inline operator fun Dp.plus(other: Dp) = Dp(value + other.value)
            inline val Int.dp: Dp

            // runtime：CompositionLocal 的读取是 inline val
            val current: T inline get() = ...      // LocalXxx.current

            // ui 内部：按类型查找节点，靠 inline + reified
            inline fun <reified T> NodeChain.headToTail(type: NodeKind<T>, block: (T) -> Unit)
            """.trimIndent(),
        )
        Note(
            "下面用 CompositionLocal 演示：父层 provides 一个值，子组件用 .current（inline val）直接读取，" +
                "不必把参数一层层往下传。点按钮切换提供的值：",
        )
        ButtonRow {
            DemoButton(text = "提供 Hello", onClick = { chinese = false })
            DemoButton(text = "提供 你好", onClick = { chinese = true })
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            CompositionLocalProvider(LocalGreeting provides if (chinese) "你好" else "Hello") {
                GreetingReader()
            }
            Text(
                "  （CompositionLocalProvider 本身不是 inline；provides 是 infix；current 才是 inline val）",
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }

        SectionTitle("⑤ 代价与边界")
        Note(
            "· 代码膨胀：函数体越大，展开后字节码越多，别对大函数滥用 inline；\n" +
                "· 不能递归 inline，也不能把 inline 的 lambda 存进字段/集合再异步调用；\n" +
                "· 当 lambda 需要被「存起来 / 转发到别的上下文」时，就得用 noinline / crossinline " +
                "——这正是示例⑤要讲的内容。",
        )
    }
}
