package com.example.composeup.flow

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
 * 本包收录的「Flow 全家桶」示例。
 *
 * Flow 是 Kotlin 协程生态里的**异步数据流**标准，也是 Compose 世界里连接「数据层」与「UI 层」的主干道。
 * 这一组示例按「解决什么问题」串成一条真实业务主线——从一次网络请求出发，逐步加上检索、状态管理、
 * 事件通知、高频数据处理，最后落到与界面生命周期安全协作，覆盖 Flow / StateFlow / SharedFlow /
 * 操作符 / 背压 / 生命周期收集六大主题。
 */
private enum class FlowTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    ColdFlow(
        title = "① Flow 基础：冷流与「构建 → 变换 → 收集」三段式",
        summary = "Flow 是冷流：flow { } 里的代码只有在被 collect 时才执行，且每次 collect 都从头再来一遍。" +
            "本示例用「模拟网络分页拉取」串起 flowOf / asFlow / flow 构建器、emit 逐条发出、" +
            "中间操作符与终止操作符、onStart/onEach/onCompletion/catch，以及 flowOn 切换上游线程。",
        keywords = "flow · flowOf · asFlow · emit · collect · onStart/onEach/onCompletion · catch · flowOn",
    ),
    Operators(
        title = "② 操作符：map/filter/combine/zip 与搜索框防抖",
        summary = "操作符把「一条流」变换成「另一条流」，可链式组合。本示例用真实「搜索框」串起 " +
            "debounce 防抖、distinctUntilChanged 去重、filter/map 变换、flatMapLatest 取消过期请求，" +
            "再用 combine/zip 合并两条流，配一份操作符速查表。",
        keywords = "map · filter · transform · take · combine · zip · debounce · distinctUntilChanged · flatMapLatest",
    ),
    StateFlow(
        title = "③ StateFlow：持有「当前状态」的热流",
        summary = "StateFlow 是始终有一个当前值、会去重（conflated + distinctUntilChanged）的热流，天生适合承载 UI 状态。" +
            "本示例用「下载进度」演示 MutableStateFlow / update / .value 同步读、只读暴露 asStateFlow，" +
            "以及 stateIn(WhileSubscribed) 把冷流转成热状态流。",
        keywords = "MutableStateFlow · StateFlow · update · .value · asStateFlow · stateIn · WhileSubscribed",
    ),
    SharedFlow(
        title = "④ SharedFlow：可配置 replay 的事件热流",
        summary = "SharedFlow 是可配置 replay 缓存与缓冲策略、支持多订阅者各收全量数据的热流，适合发一次性事件。" +
            "本示例用「Snackbar / 导航事件」演示 MutableSharedFlow / tryEmit / 订阅者计数，" +
            "并交互对比 replay=0/1/2 对「新订阅者能否补收历史」的影响。",
        keywords = "MutableSharedFlow · SharedFlow · replay · extraBufferCapacity · onBufferOverflow · tryEmit · shareIn",
    ),
    Backpressure(
        title = "⑤ 背压：生产快于消费时的四种策略",
        summary = "当生产者比消费者快，Flow 默认会挂起生产者（天然背压）；也可主动选择缓冲或丢弃。" +
            "本示例用「高频传感器 + 慢消费者」实时对比：默认挂起 / buffer 缓冲 / conflate 丢弃中间值 / " +
            "collectLatest 取消重来，看各自「生产 vs 消费」计数差异。",
        keywords = "背压 · buffer · conflate · collectLatest · flowOn · BufferOverflow",
    ),
    Lifecycle(
        title = "⑥ 生命周期收集：repeatOnLifecycle 与 collectAsStateWithLifecycle",
        summary = "热流不会自己停，若在界面不可见时仍收集就会浪费资源甚至泄漏。本示例演示 Compose 里三种安全收集姿势：" +
            "collectAsStateWithLifecycle（首选）、手动 repeatOnLifecycle(STARTED)、flowWithLifecycle，" +
            "并实时打印生命周期事件——切到后台再回来即可看到收集被自动暂停/恢复。",
        keywords = "repeatOnLifecycle · flowWithLifecycle · collectAsStateWithLifecycle · Lifecycle.State.STARTED",
    ),
}

/**
 * 「Flow 全家桶」示例的入口页。
 *
 * 外层是话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<FlowTopic?>(null) }
    val topics = remember { FlowTopic.entries.toList() }

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
                            text = "Kotlin 协程 · Flow 全家桶",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "Flow 是 Kotlin 协程的异步数据流，也是 Compose 连接「数据层 ↔ UI 层」的主干道。\n" +
                                "这一组示例串成一条真实业务主线：\n" +
                                "· ① 一次网络请求 —— 理解冷流「构建 → 变换 → 收集」；\n" +
                                "· ② 给请求加搜索 —— 用操作符做防抖、去重、取消过期请求；\n" +
                                "· ③ 把结果存成 UI 状态 —— StateFlow 持有「当前值」；\n" +
                                "· ④ 顺带发一次性事件 —— SharedFlow 通知 Snackbar / 导航；\n" +
                                "· ⑤ 数据来得太快 —— 背压四策略对比；\n" +
                                "· ⑥ 界面切到后台 —— 生命周期安全收集，不浪费资源。\n" +
                                "下面 6 个示例逐个拆解，点击任意一项进入可交互演示。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    FlowRow(
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

    // 系统返回键先退回话题列表，而不是直接退出界面
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
                            contentDescription = "返回话题列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            FlowTopic.ColdFlow -> ColdFlowDemo(contentModifier)
            FlowTopic.Operators -> OperatorsDemo(contentModifier)
            FlowTopic.StateFlow -> StateFlowDemo(contentModifier)
            FlowTopic.SharedFlow -> SharedFlowDemo(contentModifier)
            FlowTopic.Backpressure -> BackpressureDemo(contentModifier)
            FlowTopic.Lifecycle -> LifecycleCollectDemo(contentModifier)
        }
    }
}
