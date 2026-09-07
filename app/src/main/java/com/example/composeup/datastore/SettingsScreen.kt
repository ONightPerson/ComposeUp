package com.example.composeup.datastore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * DataStore 的「响应式 UI」应用场景（Compose 层）。
 *
 * 核心演示：DataStore 的读是 Flow，把它 collect 成 Compose 状态后，
 * 只要写入（edit）改变了磁盘上的值，Flow 就发出新数据，界面【自动重组】——
 * 这正是 DataStore 相比 SharedPreferences 最直观的优势（无需手动读写、无需监听回调）。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 演示用：直接 remember 一个 Repository，并传 applicationContext 避免泄漏 Activity。
    // 真实项目里 Repository 应由 ViewModel 持有、通过依赖注入提供。
    val repository = remember { SettingsRepository(context.applicationContext) }

    // 写操作是 suspend，需要协程作用域；rememberCoroutineScope 会随该组合销毁而自动取消。
    val scope = rememberCoroutineScope()

    // 读操作：把 Flow<SettingsUiState> 收集成状态，值一变就触发重组。
    // 生产环境更推荐 collectAsStateWithLifecycle（需额外依赖 lifecycle-runtime-compose），
    // 它在界面不可见时会自动停止收集，更省资源。
    val uiState by repository.settingsUiState.collectAsState(initial = SettingsUiState())

    Column(
        modifier = modifier
            .padding(16.dp)
            .background(color = if (uiState.isDarkMode) Color.DarkGray else Color.LightGray)
    ) {
        Text("用户名：${uiState.username}", fontSize = uiState.fontSize.sp)
        Text("字体大小：${uiState.fontSize}")
        Text("启动次数：${uiState.launchCount}")

        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("深色模式")
            Switch(
                checked = uiState.isDarkMode,
                // 切换即写入 DataStore，持久化到磁盘；重启 App 后开关状态依然保留
                onCheckedChange = { checked ->
                    scope.launch { repository.setDarkMode(checked) }
                },
            )
        }

        Button(onClick = { scope.launch { repository.setFontSize(uiState.fontSize + 2) } }) {
            Text("增大字体")
        }

        Button(onClick = { scope.launch { repository.increaseLaunchCount() } }) {
            Text("启动次数 +1")
        }

        Button(onClick = { scope.launch { repository.setUsername("张三") } }) {
            Text("设置用户名为张三")
        }

        Button(onClick = { scope.launch { repository.clearAll() } }) {
            Text("恢复默认（清空）")
        }
    }
}

/*
 * 如何在 App 里真正跑起来（任选其一）：
 *   1) 在 MainActivity 的 setContent 里，把 ComposeUpApp() 换成 SettingsScreen()；
 *   2) 或在 ComposeUpApp 里加一个入口按钮，点击后显示 SettingsScreen。
 *
 * 运行在真机/模拟器上验证持久化：切换开关、改字体、加启动次数后，
 * 杀掉进程重新打开——所有值都还在，这就是 DataStore 的持久化效果。
 */
