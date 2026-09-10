package com.example.composeup.keywords

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 示例③：invoke —— 调用约定。
 *
 * 一句话：给类型声明 `operator fun invoke(...)`，它的**实例**就能像函数一样被调用：
 * `obj(args)` 会被编译器翻译成 `obj.invoke(args)`。
 *
 * 这在 Compose 里是「隐形支柱」：
 *  - 你写下的每一个 `content: @Composable () -> Unit` 插槽，接收方内部都是用 `content()`
 *    来执行的——那正是函数类型的 `invoke`；
 *  - 很多「工厂 / 组件」被做成可调用对象，`MyComponent(...)` 看着像函数，其实是 `invoke`；
 *  - 一个类可以同时声明多个不同签名的 `invoke`，形成「可调用对象的重载」。
 */

/**
 * 可调用对象：密码校验器。
 * 声明两个 `invoke` 重载，于是 `validator(input)` 和 `validator(input, minDigits)` 都能用。
 */
private class PasswordValidator(private val minLength: Int) {

    /** `validator(input)`：只校验长度。 */
    operator fun invoke(input: String): Boolean = input.length >= minLength

    /** `validator(input, minDigits)`：既校验长度又校验至少含多少个数字（重载）。 */
    operator fun invoke(input: String, minDigits: Int): Boolean =
        invoke(input) && input.count { it.isDigit() } >= minDigits
}

/**
 * 可调用的 @Composable 对象：把一个「按钮组件」封装成对象，用 `chip { ... }` 直接调用。
 * 注意 invoke 上的 @Composable——它能发射 UI，因此只能在组合中被调用。
 */
private class ActionChip(private val label: String) {
    @Composable
    operator fun invoke(onClick: () -> Unit) {
        androidx.compose.material3.Button(onClick = onClick) { Text(label) }
    }
}

/**
 * 自定义「插槽」组件：把传进来的 content 反复 invoke 若干次。
 * `content()` 就是 `content.invoke()`——这正是 Compose 所有 content 插槽的执行方式。
 */
@Composable
private fun Repeat(times: Int, content: @Composable () -> Unit) {
    Column {
        repeat(times) { content() } // 同一个插槽被 invoke 多次
    }
}

@Composable
fun InvokeDemo(modifier: Modifier = Modifier) {
    val validator = remember { PasswordValidator(minLength = 6) }
    var password by rememberSaveable { mutableStateOf("compose1") }
    var times by rememberSaveable { mutableIntStateOf(3) }
    var taps by rememberSaveable { mutableIntStateOf(0) }

    // 把 ActionChip 做成可调用对象，之后用 chip { ... } 调用它
    val chip = remember { ActionChip("点我 +1") }

    val overloadLines = remember {
        listOf(
            "val v = PasswordValidator(minLength = 6)",
            "  v(\"abc\")        -> ${validator("abc")}     // invoke(String)",
            "  v(\"abcdef\")     -> ${validator("abcdef")}     // invoke(String)",
            "  v(\"ab12\", 2)    -> ${validator("ab12", 2)}     // invoke(String, Int) 重载",
            "  v(\"abcdef\", 1)  -> ${validator("abcdef", 1)}     // 长度够但数字不足",
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① invoke：让对象像函数一样被调用")
        CodeBlock(
            """
            class PasswordValidator(private val minLength: Int) {
                operator fun invoke(input: String) = input.length >= minLength
                operator fun invoke(input: String, minDigits: Int) =   // 重载
                    invoke(input) && input.count { it.isDigit() } >= minDigits
            }
            val v = PasswordValidator(6)
            v("abcdef")        // == v.invoke("abcdef")
            """.trimIndent(),
        )

        SectionTitle("② 实时调用：输入即触发 validator(text)")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（至少 6 位）") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // 下面这行 validator(password) 就是 validator.invoke(password)
        val ok = validator(password)
        val okWithDigit = validator(password, 1)
        LogBox(
            lines = listOf(
                "validator(password)      -> $ok       // invoke(String)",
                "validator(password, 1)   -> $okWithDigit       // invoke(String, Int)",
                if (ok) "✓ 长度达标" else "✗ 长度不足 6 位",
            ),
        )

        SectionTitle("③ 重载 invoke 的静态结果")
        LogBox(lines = overloadLines)

        SectionTitle("④ Compose 的核心：content() 就是 content.invoke()")
        Note(
            "每个 Composable 的 content 插槽（如 Column { ... } 的花括号）本质是一个 " +
                "@Composable () -> Unit 函数对象；接收方内部用 content() 执行它。" +
                "下面的 Repeat 就是把同一个插槽 invoke 了 times 次：",
        )
        CodeBlock(
            """
            @Composable
            fun Repeat(times: Int, content: @Composable () -> Unit) {
                Column { repeat(times) { content() } }   // content() == content.invoke()
            }
            """.trimIndent(),
        )
        ButtonRow {
            DemoButton(text = "times -1", onClick = { times = (times - 1).coerceAtLeast(0) })
            DemoButton(text = "times +1", onClick = { times = (times + 1).coerceAtMost(8) })
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("times = $times，插槽被 invoke $times 次：", style = MaterialTheme.typography.labelMedium)
            Repeat(times = times) {
                Text("· 我被调用了一次", modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        SectionTitle("⑤ 可调用的 @Composable 对象：chip { ... }")
        Note(
            "ActionChip 声明了 @Composable operator fun invoke，于是能像组件一样 chip { ... } 调用。" +
                "很多库用这招把「带内部状态的组件」封装成可调用对象。已点击 $taps 次：",
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            chip { taps++ } // == chip.invoke { taps++ }
        }
    }
}
