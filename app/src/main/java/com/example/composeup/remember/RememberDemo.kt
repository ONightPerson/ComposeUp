package com.example.composeup.remember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.composeup.flow.ButtonRow
import com.example.composeup.flow.CodeBlock
import com.example.composeup.flow.DemoButton
import com.example.composeup.flow.LogBox
import com.example.composeup.flow.Note
import com.example.composeup.flow.SectionTitle
import com.example.composeup.flow.Stage
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * 示例①：remember & keys —— 基础记忆与依赖变更。
 *
 * remember 是 Compose 的地基，它把一个值存入 Composition 树的 SlotTable 中。
 * 核心知识点：
 * 1. **重组生存期**：remember 的值在重组期间保持不变，除非该 Composable 从树中移除。
 * 2. **Keys（键）**：remember(key1, key2) 当 key 发生变化时，会重新计算 lambda 并存入新值。
 * 3. **rememberUpdatedState**：在 Effect 中引用频繁变动的值时，防止闭包截获旧值，
 *    常用于 Handler 或 Timer 的回调中。
 */
@Composable
fun RememberDemo(modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    var keySource by remember { mutableStateOf("A") }

    // 演示：不带 key 的 remember，只计算一次
    val staticRandom = remember { Random.nextInt(100) }
    
    // 演示：带 key 的 remember，keySource 变了它就重新计算
    val dynamicRandom = remember(keySource) { Random.nextInt(100) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("点击「增加计数」触发重组，观察随机值的变化。")
        
        Stage {
            Column {
                Text("计数器（触发重组）：$count")
                Text("静态随机值（无 Key）：$staticRandom")
                Text("动态随机值（Key=$keySource）：$dynamicRandom")
            }
        }

        ButtonRow {
            DemoButton("增加计数") { count++ }
            DemoButton("改变 Key") { 
                keySource = if (keySource == "A") "B" else "A" 
            }
        }

        SectionTitle("remember(key) 的作用")
        Note("当 keySource 改变时，下方的 lambda 会被重新执行。这常用于根据输入重新初始化资源。")
        CodeBlock("""
            val data = remember(userId) { 
                loadUserData(userId) 
            }
        """.trimIndent())

        SectionTitle("rememberUpdatedState")
        Note("用于在 LaunchedEffect 等异步流程中，总是能拿到最新的 UI 状态值，而不需要重启 Effect。")
        
        var timerValue by remember { mutableIntStateOf(0) }
        val latestTimerValue by rememberUpdatedState(timerValue)
        
        val logs = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            logs.add("Effect 启动")
            // 每 2 秒打印一次计时器数值
            while(true) {
                kotlinx.coroutines.delay(2000.milliseconds)
                // 如果直接用 timerValue，因为闭包捕获，这里拿到的永远是初始值 0
                // 使用 rememberUpdatedState 包装后的 latestTimerValue 保证拿到最新值
                logs.add(0, "读取到最新值: ${latestTimerValue}")
            }
        }

        Stage(height = 100.dp) {
            Text("计时器数值: $timerValue", style = MaterialTheme.typography.headlineMedium)
        }
        ButtonRow {
            DemoButton("计时器 +1") { timerValue++ }
        }
        LogBox(logs)
    }
}
