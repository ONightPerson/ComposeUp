package com.example.composeup.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlin.math.abs

/**
 * 示例①：operator —— 运算符重载。
 *
 * 一句话：`operator` 修饰的函数让「自定义类型」也能用上 `+ - * / [] < > in ..` 这些符号，
 * 编译器会把符号翻译成对应的函数调用（`a + b` → `a.plus(b)`，`a[i]` → `a.get(i)`）。
 *
 * Compose 里到处都是它：
 *  - `Dp`：`16.dp + 8.dp`、`2 * 12.dp`、`-4.dp`、`a > b` —— 全是 `@Stable inline operator fun`；
 *  - `EnterTransition` / `ExitTransition`：`fadeIn() + slideInVertically()` —— `operator fun plus`；
 *  - `DpOffset`：`offsetA + offsetB`。
 * 反例：`Modifier` 组合两个修饰符用的是 `infix fun then`（`a then b`），**没有**重载 `+`。
 * 自定义类型：把「一段间距」包装起来，然后给它装上一整套运算符约定。
 * 目的不是取代 Dp，而是演示「任何类型都能通过 operator 获得自然的符号语法」。
 */
@Immutable
private class Spacing(val value: Dp) {

    /** `a + b`：两段间距相加。 */
    operator fun plus(other: Spacing) = Spacing(value + other.value)

    /** `a - b`：相减。 */
    operator fun minus(other: Spacing) = Spacing(value - other.value)

    /** `a * n`：放大 n 倍（Dp.times(Int) 也是 operator）。 */
    operator fun times(factor: Int) = Spacing(value * factor)

    /** `-a`：取负（unaryMinus 约定）。 */
    operator fun unaryMinus() = Spacing(-value)

    /** `a > b` / `a <= b`：只要提供 compareTo，一整套比较符号都能用。 */
    operator fun compareTo(other: Spacing) = value.compareTo(other.value)

    /** `a[i]`：取下标——这里定义为「i 份间距」。get 约定支持任意参数类型。 */
    operator fun get(index: Int): Dp = value * index

    /** `x in a`：contains 约定——判断某个长度是否落在这段间距内（0..value）。 */
    operator fun contains(other: Dp): Boolean = other >= 0.dp && other <= value

    override fun toString() = "Spacing(${value})"
}

@Composable
fun OperatorDemo(modifier: Modifier = Modifier) {
    // 一个可调的「基准单位」，改动它，下面所有靠运算符算出来的尺寸都会跟着变，
    // 直观说明「符号运算的结果真的在驱动布局」。
    var unit by rememberSaveable { mutableIntStateOf(16) }

    val base = unit.dp                 // Dp
    val gap = 4.dp

    // —— 真实 Dp 上的运算符（全部 inline operator，零额外分配）——
    val plusResult = base + gap        // Dp.plus(Dp)
    val timesLeft = 2 * base           // Int.times(Dp)
    val timesRight = base * 2          // Dp.times(Int)
    val negated = -gap                 // Dp.unaryMinus()
    val divided = base / 2             // Dp.div(Int)
    val isBigger = base > gap          // Dp.compareTo -> Boolean

    // —— 自定义 Spacing 上的运算符约定 ——
    val spacing = remember(base) { Spacing(base) }
    val small = remember(gap) { Spacing(gap) }
    val spacingSum = spacing + small          // plus
    val spacingDouble = spacing * 2           // times
    val spacingNeg = -spacing                 // unaryMinus
    val thirdShare = spacing[3]               // get -> 3 份间距
    val contains12 = 12.dp in spacing         // contains
    val spacingCompare = spacing > small      // compareTo

    val lines = remember(base, gap) {
        listOf(
            "【真实 Dp】base = $base, gap = $gap",
            "  base + gap      = $plusResult      // operator fun plus",
            "  2 * base        = $timesLeft      // Int.times(Dp)",
            "  base * 2        = $timesRight      // Dp.times(Int)",
            "  -gap            = $negated      // operator fun unaryMinus",
            "  base / 2        = $divided      // operator fun div",
            "  base > gap      = $isBigger      // operator fun compareTo",
            "",
            "【自定义 Spacing】$spacing, $small",
            "  spacing + small = $spacingSum      // plus",
            "  spacing * 2     = $spacingDouble      // times",
            "  -spacing        = $spacingNeg      // unaryMinus",
            "  spacing[3]      = $thirdShare      // get：3 份间距",
            "  12.dp in spacing= $contains12      // contains",
            "  spacing > small = $spacingCompare      // compareTo",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① operator：用符号操作自定义类型")
        Note(
            "下面所有尺寸都由运算符算出。点按钮改变「基准单位」，" +
                "方块大小随之变化——说明 +、*、[] 这些符号的结果真的在驱动布局。",
        )

        ButtonRow {
            DemoButton(text = "基准 -4dp", onClick = { unit = (unit - 4).coerceAtLeast(4) })
            DemoButton(text = "基准 +4dp", onClick = { unit = (unit + 4).coerceAtMost(48) })
        }

        Stage(height = 160.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // base + gap
                OperatorBox(size = plusResult, label = "base+gap", color = MaterialTheme.colorScheme.primary)
                // 2 * base
                OperatorBox(size = timesLeft, label = "2*base", color = MaterialTheme.colorScheme.tertiary)
                // spacing[3]（get 约定）
                OperatorBox(size = thirdShare.coerceAtMost(64.dp), label = "spacing[3]", color = MaterialTheme.colorScheme.secondary)
            }
        }

        SectionTitle("② 运行结果（把每个符号还原成函数调用）")
        LogBox(lines = lines)

        SectionTitle("③ 自定义类型如何声明这些运算符")
        CodeBlock(
            """
            class Spacing(val value: Dp) {
                operator fun plus(other: Spacing) = Spacing(value + other.value)   // a + b
                operator fun times(factor: Int)   = Spacing(value * factor)         // a * n
                operator fun unaryMinus()         = Spacing(-value)                 // -a
                operator fun compareTo(other: Spacing) = value.compareTo(other.value) // a > b
                operator fun get(index: Int): Dp  = value * index                   // a[i]
                operator fun contains(other: Dp)  = other in 0.dp..value            // x in a
            }
            """.trimIndent(),
        )
        Note(
            "约定清单：plus→+，minus→-，times→*，div→/，unaryMinus→-a，compareTo→< > <= >=，" +
                "get/set→a[i]，contains→in，rangeTo→a..b，invoke→a()（见示例③）。",
        )

        SectionTitle("④ 对比：Modifier 用 infix then（不是 operator +）→ 详见示例②")
        CodeBlock(
            """
            // Modifier 组合（then 是 infix fun，可省略点号和括号）：
            val m = Modifier.padding(8.dp) then Modifier.size(40.dp)
            // 等价于 Modifier.padding(8.dp).then(Modifier.size(40.dp))

            // 而 EnterTransition 组合用的才是 operator +：
            // fadeIn() + slideInVertically()   // operator fun plus
            """.trimIndent(),
        )
        val baseBox = Modifier
            .size(base + 24.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
        // infix 调用：`a then b` 必须写在同一行——换行以 then 开头会被编译器当成上一条语句已结束
        val demoModifier = baseBox then Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = demoModifier, contentAlignment = Alignment.Center) {
                Text("infix then", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 一个带标签的彩色方块，尺寸完全由传入的 Dp 决定。 */
@Composable
private fun OperatorBox(size: Dp, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(abs(size.value).dp.coerceIn(8.dp, 96.dp))
                .background(color, RoundedCornerShape(8.dp)),
        )
        Text(
            text = "$label\n${size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
