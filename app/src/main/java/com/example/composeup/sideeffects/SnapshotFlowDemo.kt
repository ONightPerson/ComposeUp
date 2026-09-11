package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * 示例⑧：`snapshotFlow` —— 把 Compose 的 `State` 转成一条**冷 Flow**。
 *
 * `snapshotFlow { }` 在被 collect 时运行它的 block，把其中读取到的 State 结果发出；
 * 之后每当 block 里读到的某个 State **变化、且新值≠上次发出的值**时，再发出新值
 * （行为类似 `distinctUntilChanged`）。这样就能对 Compose 状态用上 Flow 的全部操作符
 * （map / filter / debounce / combine …），并在 `LaunchedEffect` 里安全地收集。
 *
 * 官方例子：记录「用户滚动越过列表首项」这一事件到分析服务。
 * ```
 * LaunchedEffect(listState) {
 *     snapshotFlow { listState.firstVisibleItemIndex }
 *         .map { index -> index > 0 }
 *         .distinctUntilChanged()
 *         .filter { it == true }
 *         .collect { MyAnalyticsService.sendScrolledPastFirstItemEvent() }
 * }
 * ```
 *
 * 本示例把埋点写进共享的 [FakeAnalytics]，滚动列表即可看到「越过首项」事件只在 0↔非0 翻转时上报一次。
 */
@Composable
fun SnapshotFlowDemo(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val items = remember { List(60) { "列表项 #$it" } }
    var currentIndex by remember { mutableIntStateOf(0) }

    // 把 State(firstVisibleItemIndex) 转成 Flow，借用操作符「只在越过首项时」上报一次埋点。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { index -> index > 0 }
            .distinctUntilChanged()
            .filter { it == true }
            .collect {
                FakeAnalytics.logEvent("滚动越过首项（scrolledPastFirstItem）")
            }
    }
    // 另一条 snapshotFlow：单纯把 index 同步到一个可读状态，用于展示（对比：它每次变化都发）。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { currentIndex = it }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Note(
            "滚动列表：snapshotFlow 把 firstVisibleItemIndex 转成 Flow。经 map/distinctUntilChanged/filter 后，" +
                "只有在「从首项滚下去」的那一刻上报一次埋点；下方日志即 FakeAnalytics 收到的事件。",
        )
        Readout("firstVisibleItemIndex = $currentIndex · 越线事件数 = ${FakeAnalytics.events.size}")
        ButtonRow {
            DemoButton(text = "清空埋点") { FakeAnalytics.clear() }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = items.size) { i ->
                    Text(
                        text = items[i],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        SectionTitle("FakeAnalytics 收到的埋点事件（最新在上）")
        LogBox(FakeAnalytics.events.toList(), minHeight = 100.dp)

        SectionTitle("官方写法")
        CodeBlock(
            """
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }   // State → 冷 Flow
                    .map { it > 0 }
                    .distinctUntilChanged()                        // 只在翻转时通过
                    .filter { it == true }
                    .collect { MyAnalytics.sendScrolledPastFirstItemEvent() }
            }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• snapshotFlow 把 Compose State 变成 Flow，从而能用 map/filter/debounce/combine 等操作符（见 flow 模块）。\n" +
                "• 它是冷流：被 collect 时才跑 block；仅当读到的 State 变化且新值≠旧值时才发（内建 distinctUntilChanged 语义）。\n" +
                "• 必须在 LaunchedEffect（或 rememberCoroutineScope）里收集，随组合生命周期自动取消。\n" +
                "• 与 derivedStateOf 的区别：derivedStateOf 产出「State」给 UI 读；snapshotFlow 产出「Flow」给协程消费。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
