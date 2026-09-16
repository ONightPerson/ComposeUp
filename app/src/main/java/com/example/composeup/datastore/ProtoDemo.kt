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
import com.example.composeup.datastore.data.repository.ProtoRepository
import com.example.composeup.datastore.ui.viewmodel.ProtoViewModel

/**
 * 示例②：Proto DataStore —— 结构化对象存储（分层架构版）
 */
@Composable
fun ProtoDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    
    val viewModel: ProtoViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProtoViewModel(ProtoRepository(context)) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var logText by remember { mutableStateOf("就绪：当前正在通过 ViewModel 观察磁盘强类型对象流。") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "Proto DataStore 能够以强类型对象形式在流中直接对结构体进行管理。在此分层架构中，" +
                "Repository 负责 Serializer 的具体实现与文件路径管理，ViewModel 负责状态转换。",
        )

        SectionTitle("真实强类型对象状态（ViewModel 驱动）")
        Stage(height = 150.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "账户级别：${uiState.level} (强类型)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "最近登录：${uiState.lastLoginIp}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "标签列表：${uiState.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Readout(logText)

        ButtonRow {
            DemoButton(text = "升级为 VIP") {
                viewModel.updateLevel("VIP")
                logText = "Action: updateLevel('VIP') 提交给 ViewModel。"
            }
            DemoButton(text = "添加标签 'Layered'") {
                viewModel.addTag("Layered")
                logText = "Action: addTag('Layered') 已发送。"
            }
            DemoButton(text = "重置档案") {
                viewModel.resetProfile()
                logText = "Action: resetProfile() 已发送。"
            }
        }

        SectionTitle("Proto DataStore 架构要点")
        CodeBlock(
            """
            // 1. Data Model (Serializable)
            @Serializable data class UserProfile(...)

            // 2. Repository: 封装 DataStoreFactory 与 Serializer
            class ProtoRepository(context: Context) {
                private val dataStore = DataStoreFactory.create(
                    serializer = UserProfileSerializer, ...
                )
            }

            // 3. ViewModel: 将 DataStore Flow 转为 StateFlow
            class ProtoViewModel(repo: ProtoRepository) : ViewModel() {
                val uiState = repo.userProfileFlow.stateIn(...)
            }
            """.trimIndent(),
        )

        SectionTitle("避坑与高级特性")
        Text(
            text = "• 强类型拦截：Repository 层通过 kotlinx.serialization 保证了读写时字段类型的严丝合缝。\n" +
                "• 效率：相比 Preferences 的键值对，Proto 在处理包含集合、嵌套对象的复杂模型时，具备极高的二进制序列化效率。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
