package com.example.composeup.nestedscroll

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

/** 本包收录的进阶示例。 */
private enum class NestedScrollTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    CollapsingHeader(
        title = "① 手写 NestedScrollConnection：可折叠 Header",
        summary = "完整实现 onPreScroll / onPostScroll / onPreFling / onPostFling 四个阶段，" +
            "做出「上滑先折叠 Header、下滑列表到顶再展开、松手自动吸附」的手感。",
        keywords = "NestedScrollConnection · onPreScroll · onPostScroll · onPostFling · animate",
    ),
    Dispatcher(
        title = "② NestedScrollDispatcher：自绘组件向外派发",
        summary = "一个没有 ScrollState 的自绘拖动条，如何用 dispatcher 把 pre/post 滚动与 fling " +
            "事件派发给外层页面，让外层跟着一起滚。",
        keywords = "NestedScrollDispatcher · dispatchPreScroll · dispatchPostScroll · VelocityTracker",
    ),
    SameDirection(
        title = "③ 同方向嵌套滚动：崩溃原因与四种解法",
        summary = "为什么 Column(verticalScroll) { LazyColumn { } } 会直接抛异常，" +
            "以及合并成一个 LazyColumn / 内层固定高度 / 内层退化为 Column / 阻断冒泡 四种应对方式。",
        keywords = "verticalScroll · LazyColumn · stickyHeader · userScrollEnabled · 滚动隔离",
    ),
    Orthogonal(
        title = "④ 正交方向嵌套：Pager 与列表为什么不用管",
        summary = "嵌套滚动协议基于屏幕坐标 (x, y)，横向 Pager 与纵向 LazyColumn 各消费一个分量，" +
            "天然互不干扰。含 Tab+列表、列表内嵌 Pager、列表内嵌 LazyRow 三种常见结构。",
        keywords = "HorizontalPager · LazyRow · PrimaryTabRow · 二维 Offset",
    ),
    Material3(
        title = "⑤ Material3 组件内置的嵌套滚动协作",
        summary = "TopAppBar 的三种 scrollBehavior、ModalBottomSheet 与内部列表的优先级、" +
            "PullToRefreshBox 只在列表到顶时才响应 —— 这些都是框架替你写好的 NestedScrollConnection。",
        keywords = "TopAppBarDefaults · scrollBehavior · ModalBottomSheet · PullToRefreshBox",
    ),
}

/**
 * 嵌套滑动进阶示例的入口页。
 *
 * 外层是一个话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedScrollHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<NestedScrollTopic?>(null) }
    val topics = remember { NestedScrollTopic.entries.toList() }

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
                            text = "Compose 进阶 · 页面嵌套滑动",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        DemoNote(
                            "Compose 的嵌套滚动是一套「二维、双向、四阶段」的事件协议。" +
                                "下面 5 个示例从手写协议到框架内置能力，逐层展开。\n" +
                                "点击任意一项进入可交互的示例。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    DemoRow(
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
            NestedScrollTopic.CollapsingHeader -> CollapsingHeaderDemo(contentModifier)
            NestedScrollTopic.Dispatcher -> DispatcherDemo(contentModifier)
            NestedScrollTopic.SameDirection -> SameDirectionNestingDemo(contentModifier)
            NestedScrollTopic.Orthogonal -> OrthogonalNestingDemo(contentModifier)
            NestedScrollTopic.Material3 -> Material3CoopDemo(contentModifier)
        }
    }
}
