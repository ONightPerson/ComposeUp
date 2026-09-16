package com.example.composeup.datastore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.composeup.datastore.data.repository.PreferencesRepository
import com.example.composeup.datastore.ui.viewmodel.PreferencesViewModel

/**
 * 示例①：Preferences DataStore —— 键值对存储（分层架构版）
 */
@Composable
fun PreferencesDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    
    // 手动注入：ViewModel 依赖 Repository，Repository 依赖 Context
    val viewModel: PreferencesViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PreferencesViewModel(PreferencesRepository(context)) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var logText by remember { mutableStateOf("就绪：当前 UI 状态由 ViewModel 驱动，数据来自 Repository 层。") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "Preferences DataStore 专门存储键值对数据。在此架构中，UI 仅负责订阅数据流并发送 Action 给 ViewModel。" +
                "具体的写盘逻辑与线程调度已在 Repository 层封装完毕。",
        )

        SectionTitle("ViewModel 驱动的持久化状态")
        Stage(height = 140.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "用户名：${uiState.username}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "夜间模式：${if (uiState.isDarkMode) "已开启 🌙" else "已关闭 ☀️"}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "通知栏提醒：${if (uiState.enableNotification) "开 ✅" else "关 ❌"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Readout(logText)

        ButtonRow {
            DemoButton(text = "切换夜间模式") {
                viewModel.toggleDarkMode()
                logText = "Action: toggleDarkMode() 发送给 ViewModel。"
            }
            DemoButton(text = "修改用户名为 'Architecture'") {
                viewModel.updateUsername("Architecture")
                logText = "Action: updateUsername('Architecture') 已发送。"
            }
            DemoButton(text = "开关通知提醒") {
                viewModel.toggleNotification()
                logText = "Action: toggleNotification() 已发送。"
            }
        }

        SectionTitle("官方推荐的分层架构 (Recommended Layered Architecture)")
        CodeBlock(
            """
            // 1. Data Layer: Repository 处理原始数据源与业务映射
            class PreferencesRepository(context: Context) { ... }

            // 2. UI Layer: ViewModel 持有 UI State，处理用户输入
            class PreferencesViewModel(repository: PreferencesRepository) : ViewModel() {
                val uiState: StateFlow<UserPreferences> = ...
                fun toggleDarkMode() { viewModelScope.launch { ... } }
            }

            // 3. UI Layer: Composable 仅通过 ViewModel 消费状态
            @Composable
            fun PreferencesScreen(viewModel: PreferencesViewModel) {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            }
            """.trimIndent(),
        )

        SectionTitle("分层架构优势")
        Text(
            text = "• 解耦：UI 不直接感知磁盘文件存在，只感知业务模型。\n" +
                "• 可测试性：可以轻松 Mock Repository 来对 ViewModel 进行单元测试。\n" +
                "• 生命周期：ViewModel 能够跨越配置变更（如屏幕旋转）保持状态，避免数据重复加载。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
