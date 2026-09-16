package com.example.composeup.datastore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 本包收录的「Android 本地持久化与存储」场景演练中心。
 *
 * 仿照 Flow 包的架构，本模块将 Android 现行的三大持久化数据架构
 * （Preferences DataStore、Proto DataStore、Room Database）进行集中式演示与选型对比，
 * 帮助开发者理清「在什么业务逻辑下应该选用何种存储机制」。
 */
private enum class StorageTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    Preferences(
        title = "① Preferences DataStore：轻量键值对存储",
        summary = "代替传统的 SharedPreferences。基于 Flow 与协程，完全避开了主线程 I/O 阻塞造成的卡顿 (ANR)。" +
            "采用强类型的 Key-Value 映射，适合轻量设置项、开关配置及非结构化标识存储。",
        keywords = "Key-Value · stringPreferencesKey · edit { } · Flow · 替代 SharedPreferences",
    ),
    Proto(
        title = "② Proto DataStore：强类型结构化对象存储",
        summary = "利用 Protocol Buffers (protobuf) 定义的 schema 序列化生成的实体对象来直接读写配置。" +
            "具备绝对的编译期类型安全与极高的读写序列化二进制效率，适合多维、有关联或嵌套对象的应用设置项管理。",
        keywords = ".proto 架构 · Serializer<T> · Type-Safety · 二进制高效序列化 · 嵌套对象",
    ),
    Room(
        title = "③ Room Database：全功能对象关系型数据库",
        summary = "基于原生的 SQLite 数据库的高级ORM封装层。通过编译期完备的 SQL 语法校验，支持多表联合查询 (JOIN)、" +
            "高维度条件过滤筛选、分页加载与全文检索，是海量具有关联性、复杂多维结构化数据的离线存储唯一解。",
        keywords = "@Entity · @Dao · @Database · SQL 编译期校验 · 多表联合查询 · 局部局部精确局部刷",
    ),
}

/**
 * 「本地存储全家桶」示例的入口页。
 *
 * 外层是持久化话题列表，点击看选型场景与交互模拟；内层支持返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatastoreHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<StorageTopic?>(null) }
    val topics = remember { StorageTopic.entries.toList() }

    val current = selected
    if (current == null) {
        Surface(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Android 本地数据持久化选型中心",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "Jetpack 架构组件为本地持久化提供了完整的分层方案。\n" +
                                "选型黄金法则是「根据数据结构复杂度及数据体量」进行划分：\n" +
                                "· ① 简单配置/独立开关项 —— 选 Preferences DataStore（极其轻量快捷）；\n" +
                                "· ② 复杂多维、有层级嵌套的配置模型 —— 选 Proto DataStore（安全、对象化）；\n" +
                                "· ③ 海量、有关联、具有频繁精确查询/局部修改诉求的数据 —— 选 Room Database。\n" +
                                "点击下方各项，进入查看核心场景及核心代码设计架构。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    StorageRow(
                        title = topic.title,
                        subtitle = topic.summary + "\n\n" + topic.keywords,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { selected = topic },
                    )
                }
            }
        }
        return
    }

    // 系统返回键处理
    BackHandler { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { selected = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回存储选型列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val itemModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            StorageTopic.Preferences -> PreferencesDemo(itemModifier)
            StorageTopic.Proto -> ProtoDemo(itemModifier)
            StorageTopic.Room -> RoomDemo(itemModifier)
        }
    }
}
