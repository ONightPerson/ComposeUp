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
import com.example.composeup.datastore.data.repository.RoomRepository
import com.example.composeup.datastore.ui.viewmodel.RoomViewModel

/**
 * 示例③：Room Database —— 关系型数据库（分层架构版）
 */
@Composable
fun RoomDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    
    val viewModel: RoomViewModel = viewModel(
        factory = remember(context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoomViewModel(RoomRepository(context)) as T
                }
            }
        }
    )

    val items by viewModel.productList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var logText by remember { mutableStateOf("就绪：当前正在通过 ViewModel 观察 Room + KSP 响应式流。") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "Room 是 Android 官方推荐的 SQLite 封装库。在此分层架构中，" +
                "Repository 屏蔽了 DAO 的具体实现，ViewModel 负责处理搜索过滤逻辑并暴露干净的 State 流。",
        )

        SectionTitle("离线 Room 关系型数据检索（ViewModel 驱动）")
        Stage(height = 180.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "当前数据库商品条数：${items.size} 条",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "过滤器关键词：${searchQuery.ifEmpty { "（未设置，显示全部）" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (items.isEmpty()) {
                        Text("（数据库为空，请点击下方按钮插入）", style = MaterialTheme.typography.bodySmall)
                    } else {
                        items.take(3).forEach { item ->
                            Text(
                                text = "• [ID:${item.id}] ${item.name} - 价格: ¥${item.price} (分类: ${item.category})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (items.size > 3) {
                            Text("...等更多 ${items.size} 条记录", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Readout(logText)

        ButtonRow {
            DemoButton(text = "添加商品: 手机") {
                viewModel.addProduct(1, "华为手机", 5999, "数码")
                logText = "Action: addProduct(...) 已发送给 ViewModel。"
            }
            DemoButton(text = "添加商品: 电脑") {
                viewModel.addProduct(2, "MacBook", 9999, "数码")
                logText = "Action: addProduct(...) 已发送。"
            }
            DemoButton(text = "仅筛选'数码'类") {
                viewModel.updateSearchQuery("数码")
                logText = "Action: updateSearchQuery('数码')。UI 将通过 flatMapLatest 自动感知并切换流。"
            }
            DemoButton(text = "重置全部") {
                viewModel.updateSearchQuery("")
                viewModel.clearAll()
                logText = "Action: clearAll() 已发送。数据库清空后，所有观察者的 Flow 均会实时收到空列表。"
            }
        }

        SectionTitle("Room 分层架构设计")
        CodeBlock(
            """
            // 1. DAO & Entity (Data Layer)
            @Dao interface ProductDao { ... }

            // 2. Repository (Data Layer)
            class RoomRepository(context: Context) {
                fun getProductsFlow(cat: String) = dao.getProducts(cat)
            }

            // 3. ViewModel (UI Layer)
            class RoomViewModel(repo: RoomRepository) : ViewModel() {
                private val _query = MutableStateFlow("")
                val productList = _query.flatMapLatest { repo.getProductsFlow(it) }
            }
            """.trimIndent(),
        )

        SectionTitle("重要避坑指南")
        Text(
            text = "• 响应式：Dao 返回 `Flow<List<T>>` 是大杀器，任何一处表变更，所有正在亮屏观察该 Flow 的 UI 都会自动全量推送更新。\n" +
                "• 搜索过滤：推荐在 ViewModel 中使用 `flatMapLatest` 或 `combine` 对搜索词和数据库流进行动态关联，而不是每次搜索都去手动 call 数据库。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
