package com.example.composeup.remember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.retain.RetainObserver
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.composeup.flow.*

/**
 * 示例⑤：Observers —— 生命周期观察者。
 *
 * 当我们需要在对象进入或离开 Composable 树时执行特定逻辑时，
 * 可以让对象实现观察者接口。
 *
 * 1. **RememberObserver**：配合 `remember` 使用。
 *    - onRemembered: 进入 UI 树。
 *    - onForgotten: 离开 UI 树。
 *    - onAbandoned: 记忆未成功完成。
 *
 * 2. **RetainObserver**：配合 `retain` 使用，专为跨配置变更的 Retain 机制设计。
 *    - onRetained: 对象首次被 retain 捕获并存入 Store。
 *    - onEnteredComposition: 进入 UI 树。
 *    - onExitedComposition: 离开 UI 树。
 *    - onRetired: 对象从 Store 中彻底注销，释放重型资源的最佳时机。
 *    - onUnused: 对象被创建但未成功应用到 Composition 中。
 */
@Composable
fun ObserverDemo(modifier: Modifier = Modifier) {
    val logs = remember { mutableStateListOf<String>() }
    var showRemembered by remember { mutableStateOf(true) }
    var showRetained by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("切换开关触发显示/隐藏，观察日志回调。")

        SectionTitle("RememberObserver 演示")
        ButtonRow {
            DemoButton(if (showRemembered) "隐藏组件" else "显示组件") {
                showRemembered = !showRemembered
            }
        }
        if (showRemembered) {
            val observer = remember { LifecycleLogger("Remember", logs) }
            Stage(height = 60.dp) {
                Text("RememberObserver ID: ${observer.id}")
            }
        }

        SectionTitle("RetainObserver 演示")
        ButtonRow {
            DemoButton(if (showRetained) "从 UI 移除" else "挂载到 UI") {
                showRetained = !showRetained
            }
        }
        if (showRetained) {
            val observer = retain { RetainLifecycleLogger("Retain", logs) }
            Stage(height = 60.dp) {
                Text("RetainObserver ID: ${observer.id}")
            }
        }

        SectionTitle("生命周期日志")
        LogBox(lines = logs.toList())

        SectionTitle("RetainObserver 的独特之处")
        Text(
            "• 当你『从 UI 移除』一个 Retain 对象时，它会触发 onExitedComposition。\n" +
            "• 但由于它是被 retain 的，它仍然存活在内存中。\n" +
            "• 如果你重新『挂载到 UI』，它会再次触发 onEnteredComposition，而不需要重新创建对象。\n" +
            "• 当本 DemoHub 被销毁或页面彻底退出时，才会触发 onRetired。",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private class LifecycleLogger(val tag: String, val logs: MutableList<String>) : RememberObserver {
    val id = (100..999).random()

    override fun onRemembered() {
        logs.add(0, "[$tag] #$id: onRemembered (进入 UI)")
    }

    override fun onForgotten() {
        logs.add(0, "[$tag] #$id: onForgotten (离开 UI)")
    }

    override fun onAbandoned() {
        logs.add(0, "[$tag] #$id: onAbandoned")
    }
}

private class RetainLifecycleLogger(val tag: String, val logs: MutableList<String>) : RetainObserver {
    val id = (1000..9999).random()

    override fun onRetained() {
        logs.add(0, "[$tag] #$id: onRetained (首次存入 Store)")
    }

    override fun onEnteredComposition() {
        logs.add(0, "[$tag] #$id: onEnteredComposition (进入 UI)")
    }

    override fun onExitedComposition() {
        logs.add(0, "[$tag] #$id: onExitedComposition (离开 UI，但仍保留在内存)")
    }

    override fun onRetired() {
        logs.add(0, "[$tag] #$id: onRetired (！！！彻底退休！！！)")
    }

    override fun onUnused() {
        logs.add(0, "[$tag] #$id: onUnused")
    }
}
