package com.example.composeup.remember

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.composeup.flow.*

/**
 * 示例②：rememberSaveable —— 跨越配置变更的记忆。
 *
 * remember 存放在内存中，Activity 重建（如旋屏）时会丢失。
 * rememberSaveable 会将状态存入 Bundle，从而在以下场景生存：
 * 1. **配置变更**：屏幕旋转、语言切换、深浅色模式切换。
 * 2. **进程终止后重启**：系统内存不足杀死后台进程，用户返回时恢复现场。
 *
 * 限制：Bundle 大小有限，且存入类型必须是可序列化的（Primitive, String, Parcelable 等）。
 */
@Composable
fun RememberSaveableDemo(modifier: Modifier = Modifier) {
    // 使用 remember：旋屏即丢
    var volatileText by remember { mutableStateOf("") }
    
    // 使用 rememberSaveable：旋屏不丢
    var persistentText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("你可以尝试旋转屏幕（在模拟器按 Ctrl+F11），观察两个输入框的变化。")
        
        SectionTitle("普通 remember (旋屏会重置)")
        TextField(
            value = volatileText,
            onValueChange = { volatileText = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("输入内容，然后旋转屏幕试试") }
        )

        SectionTitle("rememberSaveable (旋屏会保持)")
        TextField(
            value = persistentText,
            onValueChange = { persistentText = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("输入内容，旋转屏幕后依然存在") }
        )

        Stage {
            Column {
                Text("状态预览：", style = MaterialTheme.typography.labelSmall)
                Text("Volatile: $volatileText")
                Text("Persistent: $persistentText")
            }
        }

        SectionTitle("原理")
        Note("rememberSaveable 通过 SavedStateRegistry 接口与系统的 onSaveInstanceState 机制挂钩。")
        CodeBlock("""
            // 内部实现类似于：
            val registry = LocalSavedStateRegistryOwner.current.savedStateRegistry
            // 它会自动把值打包进 Bundle
        """.trimIndent())
        
        SectionTitle("常见用途")
        Text(
            "• 搜索框的关键字\n" +
            "• 列表的滚动位置（LazyListState 内部已实现）\n" +
            "• 用户输入的表单中间态",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
