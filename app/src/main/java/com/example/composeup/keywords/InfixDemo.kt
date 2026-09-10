package com.example.composeup.keywords

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 示例②：infix —— 中缀调用。
 *
 * 一句话：`infix` 修饰的函数可以写成 `a foo b` 的中缀形式，省掉点号和括号，
 * 本质仍是 `a.foo(b)` 的普通函数调用——**它没有引入任何新的运算语义，只是换了个更像自然语言的写法**。
 *
 * 三条硬性规则（编译器强制）：
 *  1. 必须是**成员函数**或**扩展函数**（要有接收者）——不能是无接收者的顶层函数；
 *  2. 只能有**一个**参数；
 *  3. 该参数**不能**是 vararg，也**不能**有默认值。
 *
 * Compose 里高频出现（均已在本项目依赖的源码中核实）：
 *  - `Modifier.then`（成员 infix）：`modifierA then modifierB` 组合修饰符；
 *  - `CompositionLocal.provides`（成员 infix）：`LocalAccent provides color`；
 *  - `EnterTransition.togetherWith`（**扩展** infix）：`fadeIn() togetherWith fadeOut()` 造 ContentTransform
 *    （旧名 `with` 已弃用并改名 `togetherWith`）；还有 `ContentTransform.using`（扩展 infix）。
 *  - infix 可与 inline 叠加：内部就有 `inline infix fun NodeKind.or`。
 */
private infix fun <T> T.shouldBe(expected: T): Boolean = this == expected

/** 一个 CompositionLocal，用 infix 的 provides 提供强调色。current 是 inline val。 */
private val LocalAccent = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun InfixDemo(modifier: Modifier = Modifier) {
    var usePrimary by rememberSaveable { mutableStateOf(true) }

    val customLines = remember {
        // 链式 infix：从左到右结合，等价于 (fadeIn() togetherWith fadeOut())
        val transform = fadeIn() togetherWith fadeOut()
        listOf(
            "自定义：infix fun <T> T.shouldBe(expected: T) = this == expected",
            "  3 shouldBe 3           -> ${3 shouldBe 3}",
            "  \"hi\" shouldBe \"ho\"     -> ${"hi" shouldBe "ho"}",
            "",
            "标准库里天天在用的 infix（其实都是函数）：",
            "  \"a\" to 1               -> ${"a" to 1}",
            "  10 downTo 8            -> ${(10 downTo 8).toList()}",
            "  1 until 4              -> ${(1 until 4).toList()}",
            "  (0..6) step 2          -> ${((0..6) step 2).toList()}",
            "  listOf(1,2) zip listOf(\"a\",\"b\") -> ${listOf(1, 2) zip listOf("a", "b")}",
            "",
            "Compose 扩展 infix：fadeIn() togetherWith fadeOut()",
            "  结果类型 -> ${transform::class.simpleName}   // 造出一个 ContentTransform",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① infix：把 a.foo(b) 写成 a foo b")
        Note(
            "infix 只是「省略点号和括号」的语法糖，没有新的运算语义。三条硬性规则：" +
                "① 必须是成员函数或扩展函数（要有接收者）；② 只能有一个参数；" +
                "③ 参数不能是 vararg、不能有默认值。",
        )
        CodeBlock(
            """
            infix fun <T> T.shouldBe(expected: T) = this == expected   // 扩展 + 单参 → 合法
            3 shouldBe 3            // == 3.shouldBe(3) == true

            // 反例（都编译不过）：
            // infix fun pair(a: Int, b: Int)   // ✗ 无接收者的顶层函数 + 两个参数
            // infix fun Int.pow(n: Int = 2)    // ✗ 参数带默认值
            """.trimIndent(),
        )

        SectionTitle("② 自定义 infix / 标准库里的 infix / Compose 扩展 infix")
        LogBox(lines = customLines)

        SectionTitle("③ Compose 三个高频 infix（成员 vs 扩展）")
        CodeBlock(
            """
            // 成员 infix（定义在类型内部）：
            infix fun Modifier.then(other: Modifier): Modifier          // modifierA then modifierB
            infix fun <T> CompositionLocal<T>.provides(v: T): ProvidedValue<T>  // LocalX provides v

            // 扩展 infix（定义在类型外部）：
            infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform
            infix fun ContentTransform.using(size: SizeTransform?): ContentTransform
            // fadeIn() togetherWith fadeOut() using SizeTransform()  ← 链式从左到右结合
            """.trimIndent(),
        )

        SectionTitle("④ 实时：LocalAccent provides color（成员 infix）")
        Note(
            "下面用 infix 的 provides 往子树里塞一个强调色，子组件用 .current 读取。点按钮切换提供的值：",
        )
        ButtonRow {
            DemoButton(text = "provides primary", onClick = { usePrimary = true })
            DemoButton(text = "provides tertiary", onClick = { usePrimary = false })
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // ↓↓↓ 这一行的 provides 就是 infix 调用：LocalAccent provides (颜色)
            CompositionLocalProvider(
                LocalAccent provides
                    if (usePrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
            ) {
                val accent = LocalAccent.current
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("current", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SectionTitle("⑤ 实时：modifierA then modifierB（成员 infix）")
        Note(
            "Modifier 组合没有重载 +，用的是 infix then。注意：`a then b` **必须写在同一行**——" +
                "换行以 then 开头会被编译器当成上一条语句已结束（Unresolved reference 'then'）。",
        )
        val left = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
        // infix then 写在同一行：left then Modifier.padding(...)
        val combined = left then Modifier.padding(12.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = combined, contentAlignment = Alignment.Center) {
                Text("then", style = MaterialTheme.typography.labelSmall)
            }
        }

        SectionTitle("⑥ infix vs operator vs 普通调用")
        LogBox(
            lines = listOf(
                "operator  ：用符号     a + b      → a.plus(b)      （符号语义）",
                "infix     ：用单词     a foo b    → a.foo(b)       （中缀语义，仅省点号括号）",
                "普通调用  ：用点号     a.foo(b)                     （默认写法）",
                "绑定强度  ：点号/后缀 > infix；多个 infix 链式从左到右：a f b g c == (a f b) g c",
                "可叠加    ：infix 可与 inline 并存（如内部 inline infix fun NodeKind.or）",
            ),
        )
        Note(
            "小结：operator 让类型「会算符号」，infix 让调用「像读句子」。两者都只是可读性糖，" +
                "Compose 用它们把 Modifier / CompositionLocal / 动画编排写得像声明式 DSL。",
        )
    }
}
