package com.example.composeup.nestedscroll

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** 同方向嵌套滚动的四种解法。 */
private enum class Fix(val label: String, val note: String) {
    MergeIntoOneList(
        label = "解法一 · 合并成一个 LazyColumn",
        note = "【官方推荐】根本不存在嵌套滚动：把原本外层 Column 的头部、吸顶栏、列表项，" +
            "全部塞进同一个 LazyColumn 的 item { } / stickyHeader { } / items { } 里。" +
            "懒加载、回收、吸顶全都由框架处理，性能和手感最好。",
    ),
    FixedHeight(
        label = "解法二 · 内层固定高度",
        note = "给内层 LazyColumn 一个确定的高度（这里是 240dp），它就拿到了「有限的最大高度约束」，" +
            "可以正常测量；内外各自滚动。内层滚到边界后，剩余增量会通过 onPostScroll 冒泡给外层继续滚 —— " +
            "这正是嵌套滚动协议默认就帮你做好的联动。",
    ),
    PlainColumn(
        label = "解法三 · 内层退化为普通 Column",
        note = "彻底放弃内层的懒加载，用 Column + forEach 把内容全部展开，只由外层统一滚动。" +
            "适合条目数量可控（几十条）的场景；条目上千时不要用，会一次性组合全部内容，" +
            "首帧卡顿且内存暴涨。",
    ),
    Isolate(
        label = "解法四 · 阻断冒泡",
        note = "有时恰恰不希望「内层滚到底后带动外层」（例如内嵌地图、内嵌代码块）。" +
            "做法是在内层与外层之间插入一个自定义 NestedScrollConnection，" +
            "在 onPostScroll / onPostFling 里把剩余量原样返回、假装自己全部消费掉，" +
            "外层就再也收不到事件了。用下面的开关对比开/关的手感差异。",
    ),
}

/**
 * 进阶点 3：同方向嵌套滚动的「坑」与四种解法。
 *
 * ## 坑长什么样
 *
 * 下面这段代码看起来人畜无害，运行时却会直接抛异常：
 *
 * ```kotlin
 * Column(Modifier.verticalScroll(rememberScrollState())) {   // 外层：纵向可滚动
 *     Text("头部说明")
 *     LazyColumn { items(100) { Text("第 $it 项") } }         // 内层：纵向懒列表 ← 崩溃
 *     Text("底部说明")
 * }
 * ```
 *
 * ```
 * java.lang.IllegalStateException: Vertically scrollable component was measured with an infinite
 * maximum height constraints, which makes it unscrollable. If you use verticalScroll() modifier on
 * a parent element or a Box, consider providing height to the child element. If you use LazyColumn
 * inside a scrollable container, consider replacing that container with LazyColumn itself and
 * turning its scrollable children into items of LazyColumn.
 * ```
 *
 * ## 为什么会崩
 *
 * `verticalScroll` 的本质是：**把子级的最大高度约束改成 Infinity，让子级按内容真实高度测量，
 * 然后自己负责裁剪和偏移**。而 `LazyColumn` 需要一个有限高度才能算出「可视区域内放得下几项」，
 * 拿到 Infinity 之后它无法工作，于是主动抛错提醒你别这么写。
 *
 * 换句话说：这不是「嵌套滚动协议」的问题，而是「测量约束」的问题。
 * 嵌套滚动协议（NestedScrollConnection）解决的是**手势如何协调**；
 * 而这里首先得解决**布局如何成立**。四种解法就是从这两个角度出发的。
 */
@Composable
fun SameDirectionNestingDemo(modifier: Modifier = Modifier) {
    var fix by rememberSaveable { mutableStateOf(Fix.MergeIntoOneList) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Fix.entries.forEach { candidate ->
                FilterChip(
                    selected = fix == candidate,
                    onClick = { fix = candidate },
                    label = { Text(candidate.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        DemoNote(fix.note)

        when (fix) {
            Fix.MergeIntoOneList -> MergeIntoOneListSolution(Modifier.fillMaxSize())
            Fix.FixedHeight -> FixedHeightSolution(Modifier.fillMaxSize())
            Fix.PlainColumn -> PlainColumnSolution(Modifier.fillMaxSize())
            Fix.Isolate -> IsolateSolution(Modifier.fillMaxSize())
        }
    }
}

/* ------------------------------------------------------------------ 解法一 */

/**
 * 解法一：把「外层头部 + 吸顶栏 + 列表」合并进同一个 [LazyColumn]。
 *
 * 关键 API：
 *  - `item { }`        —— 单个非重复项（Banner、说明卡片）
 *  - `stickyHeader { }`—— 吸顶分组标题，滚过之后钉在顶部，直到下一个 stickyHeader 顶替它
 *  - `items(count) { }`—— 重复项
 */
@Composable
private fun MergeIntoOneListSolution(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
            ) {
                Text("原本是外层 Column 的头部 Banner", fontWeight = FontWeight.Bold)
                Text(
                    "现在它只是 LazyColumn 的第 0 个 item，跟着列表一起滚出去。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        stickyHeader {
            GroupTitle("分组 A（吸顶）")
        }
        items(count = 20) { index ->
            DemoRow(title = "A - $index", subtitle = "同一个 LazyColumn 内的普通 item")
        }

        stickyHeader {
            GroupTitle("分组 B（吸顶）")
        }
        items(count = 20) { index ->
            DemoRow(title = "B - $index", subtitle = "继续往下滚，看 A 被 B 顶替")
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseOnSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/* ------------------------------------------------------------------ 解法二 */

/** 解法二：外层照常 verticalScroll，内层 LazyColumn 给定确定高度。 */
@Composable
private fun FixedHeightSolution(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        repeat(3) { DemoPlaceholder(it, label = "外层内容（上方）$it") }

        DemoSectionTitle("内层 LazyColumn · height(240.dp)")
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)) {
            items(count = 30) { index ->
                DemoRow(
                    title = "内层第 $index 项",
                    subtitle = "把内层滚到底再继续拖，外层会接着滚（嵌套滚动自动联动）",
                )
            }
        }

        repeat(12) { DemoPlaceholder(it, label = "外层内容（下方）$it") }
    }
}

/* ------------------------------------------------------------------ 解法三 */

/**
 * 解法三：内层不用 LazyColumn，改成普通 [Column] + forEach。
 *
 * 注意 `LazyColumn(userScrollEnabled = false)` **并不能**解决这个问题 ——
 * 崩溃发生在测量阶段，与手势开关无关。必须让内层不再是「可无限滚动的组件」。
 */
@Composable
private fun PlainColumnSolution(modifier: Modifier = Modifier) {
    val innerItems = remember { List(24) { it } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        repeat(3) { DemoPlaceholder(it, label = "外层内容（上方）$it") }

        DemoSectionTitle("内层普通 Column · 由外层统一滚动")
        Column(modifier = Modifier.fillMaxWidth()) {
            innerItems.forEach { index ->
                DemoRow(title = "内层第 $index 项", subtitle = "没有懒加载，但布局约束是合法的")
            }
        }

        repeat(12) { DemoPlaceholder(it, label = "外层内容（下方）$it") }
    }
}

/* ------------------------------------------------------------------ 解法四 */

/**
 * 「滚动隔离」连接：把子级没吃完的量原样返回，等价于告诉上层「我全吃了，你别动」。
 *
 * 只需实现 post 阶段即可 —— pre 阶段返回 `Offset.Zero`（默认实现）表示「我不抢，让子级先滚」。
 */
private class ScrollEaterConnection : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** 解法四：在内层 LazyColumn 上挂一个「吞噬连接」，阻断事件向外层冒泡。 */
@Composable
private fun IsolateSolution(modifier: Modifier = Modifier) {
    var isolate by rememberSaveable { mutableStateOf(true) }
    // 连接对象必须 remember 复用，否则每次重组都会重建嵌套滚动图
    val eater = remember { ScrollEaterConnection() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = isolate, onCheckedChange = { isolate = it })
            Text(if (isolate) "隔离开启：内层滚到底，外层不动" else "隔离关闭：内层滚到底，外层接着滚")
        }

        repeat(3) { DemoPlaceholder(it, label = "外层内容（上方）$it") }

        DemoSectionTitle("内层 LazyColumn · height(280.dp)")
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                // 条件式地插入隔离层：修饰符顺序很重要，nestedScroll 必须挂在
                // LazyColumn 自身（即其内部 scrollable 节点的祖先）才能拦到它派发的事件。
                .then(if (isolate) Modifier.nestedScroll(eater) else Modifier),
        ) {
            items(count = 40) { index ->
                DemoRow(
                    title = "内层第 $index 项",
                    subtitle = "滚到最后一项后继续拖，观察外层是否跟着动",
                )
            }
        }

        repeat(16) { DemoPlaceholder(it, label = "外层内容（下方）$it") }
    }
}
