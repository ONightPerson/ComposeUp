package com.example.composeup.compositionlocal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 4. 埋点统计装饰器示例 */

interface AppTracker {
    fun logEvent(event: String, params: Map<String, String> = emptyMap())
    
    // 扩展方法，用于创建带上下文的追踪器
    fun withContext(vararg newParams: Pair<String, String>): AppTracker
}

private class BaseTracker(
    private val onLog: (String) -> Unit,
    private val context: Map<String, String> = emptyMap()
) : AppTracker {
    override fun logEvent(event: String, params: Map<String, String>) {
        val allParams = context + params
        onLog("Event: $event, Context: $allParams")
    }

    override fun withContext(vararg newParams: Pair<String, String>): AppTracker {
        return BaseTracker(onLog, context + newParams.toMap())
    }
}

val LocalTracker = staticCompositionLocalOf<AppTracker> {
    BaseTracker(onLog = { println("Default Log: $it") })
}

@Composable
fun AnalyticsDemo(modifier: Modifier = Modifier) {
    val logs = remember { mutableStateListOf<String>() }
    val baseTracker = remember { BaseTracker(onLog = { logs.add(0, it) }) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Note("本例展示装饰器模式：嵌套 UI 会自动将自己的 Section 信息注入到上层的埋点上下文中。")
        
        CompositionLocalProvider(LocalTracker provides baseTracker) {
            AnalyticsSection(name = "首页容器") {
                Column {
                    TrackedButton(label = "容器内的按钮")
                    
                    Spacer(Modifier.height(16.dp))
                    
                    AnalyticsSection(name = "侧边栏模块") {
                        TrackedButton(label = "模块内的按钮")
                    }
                }
            }
        }

        SectionTitle("埋点日志 (最新在上)")
        LogBox(lines = logs)
    }
}

@Composable
private fun AnalyticsSection(name: String, content: @Composable () -> Unit) {
    val parentTracker = LocalTracker.current
    val nestedTracker = remember(parentTracker, name) {
        parentTracker.withContext("section" to name)
    }
    
    CompositionLocalProvider(LocalTracker provides nestedTracker) {
        content()
    }
}

@Composable
private fun TrackedButton(label: String) {
    val tracker = LocalTracker.current
    DemoButton(text = label) {
        tracker.logEvent("Click", mapOf("label" to label))
    }
}

/** 模拟日志展示框 */
@Composable
private fun LogBox(lines: List<String>) {
    Stage(height = 180.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (lines.isEmpty()) Text("暂无日志")
            lines.take(5).forEach { Text(text = it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
