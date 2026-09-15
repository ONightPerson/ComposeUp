package com.example.composeup.remember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.composeup.flow.*

/**
 * 示例④：retain —— 在内存中保留状态。
 *
 * `retain` 是一个比 `remember` 生命周期更长的状态记忆函数。
 * 它将值存储在 `RetainedValuesStore` 中，能够在以下场景保持状态：
 * 1. **配置变更**：Activity 销毁并重建时，内存中的对象会被保留。
 * 2. **导航回退**：当目的地进入回退栈（非组合状态）又返回时。
 *
 * 与 `rememberSaveable` 的区别：
 * - `retain` 存储在内存中，不需要序列化，性能更高，适合大型对象。
 * - `retain` 不能跨越进程重启（Process Death），因为进程重启会清空内存。
 */
@Composable
fun RetainDemo(modifier: Modifier = Modifier) {
    // 使用 retain：旋屏不丢，且无需 Serializable
    val retainedState = retain { DemoStateHolder() }
    
    // 使用 remember：旋屏即丢
    val rememberedState = remember { DemoStateHolder() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("尝试旋转屏幕，观察 'Retain' 计数与 'Remember' 计数的变化。")

        Stage {
            Column {
                Text("Retain 计数（已保留）：${retainedState.count.intValue}")
                Text("Remember 计数：${rememberedState.count.intValue}")
            }
        }

        ButtonRow {
            DemoButton("Retain +1") { retainedState.increment() }
            DemoButton("Remember +1") { rememberedState.increment() }
        }

        SectionTitle("retain 的优势")
        Note("它结合了 remember 的简单性和 ViewModel 的持久性。")
        CodeBlock("""
            // 无需继承 ViewModel，直接在 Composable 中保留复杂对象
            val state = retain { MyLargeObject() }
        """.trimIndent())

        SectionTitle("生命周期对比")
        Text(
            "• remember: 生命周期绑定到当前组合（Composition）。\n" +
            "• retain: 生命周期跨越配置变更，绑定到 RetainedValuesStore。\n" +
            "• rememberSaveable: 生命周期跨越进程重启，存储在 Bundle。",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** 演示用的普通状态类（不需要实现任何接口） */
private class DemoStateHolder {
    var count = mutableIntStateOf(0)
    fun increment() { count.intValue++ }
}
