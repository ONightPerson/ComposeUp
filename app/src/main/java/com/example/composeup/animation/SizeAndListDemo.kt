package com.example.composeup.animation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 示例④：尺寸动画与列表增删动画。
 *
 * 这一节讲两个「和布局尺寸打交道」的动画：
 *
 * 1. [Modifier.animateContentSize]：当组件的内容尺寸发生变化（比如展开 / 收起一段文字），
 *    自动把尺寸的变化补间出来。你只管改内容，尺寸过渡交给它。
 *
 * 2. [androidx.compose.foundation.lazy.LazyItemScope.animateItem]：给 `LazyColumn` / `LazyRow` 的
 *    列表项加上「出现淡入、消失淡出、位置移动补间」三种动画。
 *
 * ⚠️ 关键认知（也是本节和「嵌套滚动」那节的呼应）：**不要把 LazyColumn 放进一个纵向
 * `verticalScroll` 的 Column 里** —— 无限高度约束会让懒列表直接抛异常。所以本页用
 * `Modifier.weight(1f)` 给列表一块确定的高度，而不是整页滚动。
 */
@Composable
fun SizeAndListDemo(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        DemoNote(
            "上半部分：点击卡片展开 / 收起详情，尺寸变化由 animateContentSize 自动补间。\n" +
                "下半部分：对列表做新增 / 删除 / 打乱，每一项的出现、消失、位移都有过渡。",
        )
        ExpandableCard()
        DemoSectionTitle("列表增删动画（LazyItemScope.animateItem）")
        AnimatedList(Modifier.weight(1f))
    }
}

/* ------------------------------------------------------ animateContentSize */

@Composable
private fun ExpandableCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // animateContentSize 会拦截尺寸变化并补间。用回弹弹簧让「展开」有一点弹性。
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "收起详情" else "点击展开详情",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (expanded) {
                Text(
                    text = "这段文字是「新增内容」。卡片的高度因为它的出现而变大，" +
                        "animateContentSize 会把这次高度变化补间成一段平滑的展开动画，" +
                        "而不是瞬间跳变。收起时同理。它底层就是 clipToBounds + 一个尺寸插值 Modifier，" +
                        "所以既能动画又不会在过渡期间把内容画到边界外。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/* ----------------------------------------------------------- 列表增删动画 */

@Composable
private fun AnimatedList(modifier: Modifier = Modifier) {
    // 用 mutableStateListOf 保存「自增 id」，删除 / 打乱时以 id 作为 key，动画才能正确识别是哪一项。
    val items = rememberSaveable(
        saver = listSaver<SnapshotStateList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf(1, 2, 3, 4, 5) }
    var nextId by rememberSaveable { mutableIntStateOf(6) }

    DemoButtonRow {
        DemoButton(text = "末尾新增") {
            items.add(nextId++)
        }
        DemoButton(text = "开头新增") {
            items.add(0, nextId++)
        }
        DemoButton(text = "删除首项") {
            if (items.isNotEmpty()) items.removeAt(0)
        }
        DemoButton(text = "打乱顺序") {
            items.shuffle()
        }
    }
    DemoNote(
        "animateItem 的三个规格：fadeInSpec（新增淡入）、fadeOutSpec（删除淡出）、placementSpec（位移补间）。" +
            "前提是每个 item 都提供了稳定且唯一的 key —— 否则 Compose 无法把「移动后的一项」和「移动前的一项」对应起来。",
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(items = items, key = { it }) { id ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    // 这一行就是列表项动画的全部：默认 spring，淡入淡出 + 位移补间一次到位。
                    .animateItem()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "  条目 #$id",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}
