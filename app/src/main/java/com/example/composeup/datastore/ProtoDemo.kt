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

private enum class SerializerType {
    Kotlinx, Gson, Moshi, Protobuf
}

/**
 * 示例②：Typed DataStore —— 强类型对象存储（多 Serializer 实现版）
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

    var selectedType by remember { mutableStateOf(SerializerType.Kotlinx) }

    val kotlinxState by viewModel.kotlinxState.collectAsStateWithLifecycle()
    val gsonState by viewModel.gsonState.collectAsStateWithLifecycle()
    val moshiState by viewModel.moshiState.collectAsStateWithLifecycle()
    val trueProtoState by viewModel.trueProtoState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "Typed DataStore (Proto DataStore) 支持多种序列化实现。你可以选择原生的 Protobuf，" +
                "也可以使用 JSON 库（如 Kotlinx, Gson, Moshi）配合自定义 Serializer 来实现强类型存储。",
        )

        SectionTitle("选择序列化器实现方案")
        OptionChips(
            options = SerializerType.entries.map { it to it.name },
            selected = selectedType,
            onSelect = { selectedType = it }
        )

        SectionTitle("当前实现：${selectedType.name}")
        Stage(height = 150.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                when (selectedType) {
                    SerializerType.Kotlinx -> {
                        ProfileDisplay(level = kotlinxState.level, ip = kotlinxState.lastLoginIp)
                    }
                    SerializerType.Gson -> {
                        ProfileDisplay(level = gsonState.level, ip = gsonState.lastLoginIp)
                    }
                    SerializerType.Moshi -> {
                        ProfileDisplay(level = moshiState.level, ip = moshiState.lastLoginIp)
                    }
                    SerializerType.Protobuf -> {
                        ProfileDisplay(level = "PROTO", ip = trueProtoState.username) // 使用 username 字段演示
                        Text(text = "(字段映射: Protobuf.username -> Display.IP)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        ButtonRow {
            DemoButton(text = "更新 Level (VIP)") {
                when (selectedType) {
                    SerializerType.Kotlinx -> viewModel.updateKotlinxLevel("VIP")
                    SerializerType.Gson -> viewModel.updateGsonLevel("VIP")
                    SerializerType.Moshi -> viewModel.updateMoshiLevel("VIP")
                    SerializerType.Protobuf -> viewModel.updateProtoUsername("VIP")
                }
            }
            DemoButton(text = "重置为 Regular") {
                when (selectedType) {
                    SerializerType.Kotlinx -> viewModel.updateKotlinxLevel("REGULAR")
                    SerializerType.Gson -> viewModel.updateGsonLevel("REGULAR")
                    SerializerType.Moshi -> viewModel.updateMoshiLevel("REGULAR")
                    SerializerType.Protobuf -> viewModel.updateProtoUsername("Default_Proto")
                }
            }
        }

        SectionTitle("多方案 Serializer 实现对比")
        CodeBlock(
            when (selectedType) {
                SerializerType.Kotlinx -> """
                    // Kotlinx.serialization 实现
                    object KotlinxSerializer : Serializer<UserProfile> {
                        override suspend fun readFrom(input: InputStream) = 
                            Json.decodeFromString<UserProfile>(input.readBytes().decodeToString())
                    }
                """.trimIndent()
                SerializerType.Gson -> """
                    // Gson 实现
                    object GsonSerializer : Serializer<UserProfile> {
                        override suspend fun readFrom(input: InputStream) = 
                            gson.fromJson(input.readBytes().decodeToString(), UserProfile::class.java)
                    }
                """.trimIndent()
                SerializerType.Moshi -> """
                    // Moshi 实现
                    object MoshiSerializer : Serializer<UserProfile> {
                        override suspend fun readFrom(input: InputStream) = 
                            moshiAdapter.fromJson(input.readBytes().decodeToString())
                    }
                """.trimIndent()
                SerializerType.Protobuf -> """
                    // 原生 Protobuf 实现 (官方标准)
                    object ProtoSerializer : Serializer<UserSettingsProto> {
                        override suspend fun readFrom(input: InputStream) = 
                            UserSettingsProto.parseFrom(input)
                    }
                """.trimIndent()
            }
        )

        SectionTitle("总结")
        Text(
            text = "• 原生 Protobuf 是性能最高、最节省空间的方案，也是 DataStore 命名的由来。\n" +
                "• JSON 方案（Gson/Moshi/Kotlinx）更具可读性，适合调试和对体积不敏感的场景。\n" +
                "• 无论何种方案，只要实现了 `androidx.datastore.core.Serializer<T>`，就能享受 DataStore 的事务和异步流特性。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProfileDisplay(level: String, ip: String) {
    Text(
        text = "账户级别：$level",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = "内容载荷：$ip",
        style = MaterialTheme.typography.bodyLarge,
    )
}
