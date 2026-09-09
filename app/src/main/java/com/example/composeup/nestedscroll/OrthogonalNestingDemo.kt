package com.example.composeup.nestedscroll

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class OrthogonalMode(val label: String, val note: String) {
    ColumnInPager(
        label = "Tab + 列表",
        note = "最常见的结构：HorizontalPager 负责横向翻页，每页内部是一个撑满的 LazyColumn 负责纵向滚动。" +
            "两个方向正交，Compose 的嵌套滚动协议基于屏幕坐标 (x, y) 而不是组件的 orientation，" +
            "所以横向手势只会被 Pager 消费、纵向手势只会被 LazyColumn 消费，斜着滑也能自动判定主方向 —— 一行额外代码都不用写。",
    ),
    PagerInColumn(
        label = "列表内嵌横向卡片",
        note = "反过来：外层是纵向 LazyColumn，中间某个 item 是一个固定高度的 HorizontalPager，" +
            "每页又是一个纵向 LazyColumn。注意内层仍然必须给定确定高度 —— 约束问题和方向无关，" +
            "「可无限滚动的组件」永远拿不到 Infinity 高度约束。",
    ),
    LazyRowInColumn(
        label = "列表内嵌横向行",
        note = "LazyRow 嵌在纵向 LazyColumn 里。横向的懒列表本身就有确定高度（由内容决定），" +
            "因此不存在约束冲突；手势上横向归 LazyRow、纵向归外层 LazyColumn，同样自动分流。",
    ),
}

/**
 * 进阶点 4：正交方向嵌套 —— 为什么「不用管」就是最好的处理。
 *
 * 很多人以为「Pager 里放列表」需要写一堆 NestedScrollConnection，其实完全不需要。
 * 原因在于协议本身是**二维**的：
 *
 *  - `onPreScroll` / `onPostScroll` 收发的是 [androidx.compose.ui.geometry.Offset]（x 和 y 两个分量）；
 *  - 每个可滚动组件只会去消费**自己方向上的那一个分量**，另一个分量原样透传；
 *  - 因此横向 Pager 与纵向 LazyColumn 天然互不干扰。
 *
 * 真正需要手写 NestedScrollConnection 的，永远是**同方向**的父子滚动（见 SameDirectionNestingDemo）。
 */
@Composable
fun OrthogonalNestingDemo(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(OrthogonalMode.ColumnInPager) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrthogonalMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = { mode = candidate },
                    label = { Text(candidate.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        DemoNote(mode.note)

        when (mode) {
            OrthogonalMode.ColumnInPager -> ColumnInPagerSolution(Modifier.fillMaxSize())
            OrthogonalMode.PagerInColumn -> PagerInColumnSolution(Modifier.fillMaxSize())
            OrthogonalMode.LazyRowInColumn -> LazyRowInColumnSolution(Modifier.fillMaxSize())
        }
    }
}

/** Tab / 页面的标题与配色。 */
private val PageTitles = listOf("推荐", "关注", "热点", "视频")

private val PageColors = listOf(
    Color(0xFFB3E5FC),
    Color(0xFFC8E6C9),
    Color(0xFFFFE0B2),
    Color(0xFFF8BBD0),
)

/* ------------------------------------------------------- 模式一：Tab + 列表 */

/**
 * 模式一：PrimaryTabRow 与 HorizontalPager 联动，每页一个撑满的 LazyColumn。
 *
 * 这是「正交嵌套」的标准范式：横向交给 Pager，纵向交给每页的 LazyColumn。
 */
@Composable
private fun ColumnInPagerSolution(modifier: Modifier = Modifier) {
    val titles = PageTitles
    val pagerState = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Box(modifier = Modifier
                .fillMaxSize()
                .background(PageColors[page % PageColors.size])) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(count = 40) { index ->
                        DemoRow(
                            title = "${titles[page]} · 第 $index 项",
                            subtitle = "纵向滑动只影响本页；横向滑动切页",
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------- 模式二：纵向列表内嵌横向 Pager */

/** 模式二：外层纵向 LazyColumn，中间嵌一个固定高度的 HorizontalPager。 */
@Composable
private fun PagerInColumnSolution(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(count = 4) { index ->
            DemoRow(title = "外层列表项 #$index", subtitle = "纵向滑动由外层 LazyColumn 负责")
        }

        item {
            DemoSectionTitle("内嵌 HorizontalPager（高度 260dp）")
            HorizontalPager(
                state = rememberPagerState(pageCount = { PageColors.size }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                pageSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(PageColors[page % PageColors.size]),
                ) {
                    // 内层同样是「可无限滚动」的组件，所以必须给定确定高度，
                    // 这里靠父级 Pager 的 fillMaxHeight 已经拿到了有限约束。
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(count = 25) { index ->
                            DemoRow(
                                title = "第 $page 页 · 第 $index 项",
                                subtitle = "在这一块里纵向滑，只滚内层列表",
                            )
                        }
                    }
                }
            }
        }

        items(count = 12) { index ->
            DemoRow(title = "外层列表项 #${index + 4}", subtitle = "继续往下滚")
        }
    }
}

/* --------------------------------------------- 模式三：纵向列表内嵌横向行 */

/** 模式三：LazyRow 作为纵向 LazyColumn 的一个 item。 */
@Composable
private fun LazyRowInColumnSolution(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(count = 3) { index ->
            DemoRow(title = "外层列表项 #$index")
        }

        item {
            Column {
                Text(
                    text = "横向卡片流（LazyRow）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = 20) { index ->
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .padding(vertical = 4.dp)
                                .background(
                                    color = PageColors[index % PageColors.size],
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("卡片 $index")
                        }
                    }
                }
            }
        }

        items(count = 15) { index ->
            DemoRow(title = "外层列表项 #${index + 3}", subtitle = "在横向卡片上纵向滑，仍然滚外层")
        }
    }
}
