package com.example.composeup.nestedscroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class M3Coop(val label: String, val note: String) {
    AppBar(
        label = "TopAppBar 三种 scrollBehavior",
        note = "Material3 已经把「折叠工具栏」封装好了：只要把 scrollBehavior.nestedScrollConnection " +
            "挂到内容容器上，TopAppBar 就会自动跟着列表滚动做位移。三种预设对应三种手感，" +
            "底层实现与 CollapsingHeaderDemo 里手写的 connection 完全同构。",
    ),
    BottomSheet(
        label = "ModalBottomSheet + 内部列表",
        note = "Sheet 里放一个 LazyColumn：向下拖时列表先滚回顶部，滚到顶之后剩余增量才交给 Sheet 下滑关闭。" +
            "这套「先内后外」的优先级是框架内部用 NestedScrollConnection 实现的，业务代码一行都不用写。",
    ),
    PullToRefresh(
        label = "PullToRefreshBox + 列表",
        note = "下拉刷新指示器同样是一个 NestedScrollConnection：只有当子列表已经处于顶部、" +
            "并且还在继续向下拉时，它才会用 onPostScroll 消费剩余位移来拉出指示器；" +
            "列表没到顶时它返回 Offset.Zero，完全不打扰正常滚动。",
    ),
}

/**
 * 进阶点 5：Material3 组件自带的嵌套滚动协作。
 *
 * 前面几个示例都在「手写」协议，这一节说明**大多数业务场景根本不用手写** ——
 * Material3 / foundation 的主流组件都已经实现了 [androidx.compose.ui.input.nestedscroll.NestedScrollConnection]，
 * 只要按官方推荐的组合方式拼装，协调行为就自动生效。
 */
@Composable
fun Material3CoopDemo(modifier: Modifier = Modifier) {
    var coop by rememberSaveable { mutableStateOf(M3Coop.AppBar) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            M3Coop.entries.forEach { candidate ->
                FilterChip(
                    selected = coop == candidate,
                    onClick = { coop = candidate },
                    label = { Text(candidate.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        DemoNote(coop.note)

        // key(coop) 让切换子示例时彻底重建内部状态（尤其是 TopAppBarState 的偏移量）
        key(coop) {
            when (coop) {
                M3Coop.AppBar -> AppBarCoopDemo(Modifier.fillMaxSize())
                M3Coop.BottomSheet -> BottomSheetCoopDemo(Modifier.fillMaxSize())
                M3Coop.PullToRefresh -> PullToRefreshCoopDemo(Modifier.fillMaxSize())
            }
        }
    }
}

/* --------------------------------------------------------- TopAppBar 行为 */

private enum class AppBarBehavior(val label: String) {
    Pinned("pinnedScrollBehavior"),
    EnterAlways("enterAlwaysScrollBehavior"),
    ExitUntilCollapsed("exitUntilCollapsedScrollBehavior"),
}

/**
 * 三种 [TopAppBarDefaults] 预设行为对比：
 *  - `pinnedScrollBehavior`              工具栏永远钉在顶部，不参与滚动；
 *  - `enterAlwaysScrollBehavior`         一往上滑就立刻收起，一往下滑就立刻出现；
 *  - `exitUntilCollapsedScrollBehavior`  往上滑时从 expandedHeight 收缩到默认高度后停住，往下滑到顶才重新展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBarCoopDemo(modifier: Modifier = Modifier) {
    var behavior by rememberSaveable { mutableStateOf(AppBarBehavior.ExitUntilCollapsed) }
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = when (behavior) {
        AppBarBehavior.Pinned -> TopAppBarDefaults.pinnedScrollBehavior(appBarState)
        AppBarBehavior.EnterAlways -> TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
        AppBarBehavior.ExitUntilCollapsed ->
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    }

    Scaffold(
        // 关键的一行：把 behavior 的连接挂到内容根节点上。
        // 之后后代 LazyColumn 派发出来的每一次 pre/post scroll，都会先经过它。
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(behavior.label, fontWeight = FontWeight.Bold)
                        Text(
                            "heightOffset = ${appBarState.heightOffset.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                expandedHeight = 140.dp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // innerPadding 已经包含了 TopAppBar 的高度，给整个内容区统一避让
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppBarBehavior.entries.forEach { candidate ->
                    FilterChip(
                        selected = behavior == candidate,
                        onClick = { behavior = candidate },
                        label = { Text(candidate.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(count = 50) { index ->
                    DemoRow(
                        title = "内容 #$index",
                        subtitle = "上下滑动，观察顶部 AppBar 的行为差异",
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------- ModalBottomSheet */

/**
 * Sheet 内放 LazyColumn，演示框架内置的「先内后外」嵌套滚动优先级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetCoopDemo(modifier: Modifier = Modifier) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { showSheet = true }) { Text("打开 ModalBottomSheet") }
        DemoNote(
            "打开后：\n" +
                "1. 在列表上向上拖 → 只有列表滚动，Sheet 不动；\n" +
                "2. 列表滚到顶后继续向下拖 → Sheet 才开始下滑；\n" +
                "3. 拖过阈值松手 → Sheet 关闭。\n" +
                "这三段行为全部由 SheetState 内部的 NestedScrollConnection 完成。",
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                text = "Sheet 内的 LazyColumn",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(count = 40) { index ->
                    DemoRow(title = "Sheet 列表项 #$index")
                }
            }
        }
    }
}

/* -------------------------------------------------------- PullToRefresh */

/** 下拉刷新：指示器本质上也是一个 NestedScrollConnection。 */
@Composable
private fun PullToRefreshCoopDemo(modifier: Modifier = Modifier) {
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshCount by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                delay(1_200)
                refreshCount++
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                DemoRow(
                    title = "已刷新 $refreshCount 次",
                    subtitle = "把列表滚到顶后继续下拉，才会出现刷新指示器；" +
                        "列表没到顶时下拉只是在正常滚动。",
                )
            }
            items(count = 40) { index ->
                DemoRow(title = "内容 #$index")
            }
        }
    }
}
