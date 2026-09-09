package com.example.composeup.nestedscroll

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Header 完全展开时的高度。 */
private val ExpandedHeaderHeight = 200.dp

/** Header 完全折叠后仍然保留的标题栏高度。 */
private val CollapsedHeaderHeight = 56.dp

/** 触发「顺势吸附」所需的最小惯性速度（px/s）。 */
private const val SnapVelocityThreshold = 600f

/**
 * 进阶点 1：手写 [NestedScrollConnection]，从零实现一个「可折叠 Header」。
 *
 * 这里完整走通嵌套滚动的四个阶段，是理解 Compose 嵌套滚动协议最直接的例子：
 *
 * ```
 *  手指拖动 delta
 *      │
 *      ├─① onPreScroll(available, source)      自下而上：父级先抢着消费
 *      │        └─ 返回 parentConsumed，子级只剩 available - parentConsumed
 *      ├─   子级（LazyColumn）自己滚动
 *      ├─② onPostScroll(consumed, available)   自下而上：子级吃剩的抛给父级
 *      │
 *  手指抬起，速度 velocity
 *      ├─③ onPreFling(available)               父级先抢走一部分惯性速度
 *      ├─   子级自己做 fling
 *      └─④ onPostFling(consumed, available)    子级 fling 剩下的速度交给父级
 * ```
 *
 * 交互设计（也是主流 App 的手感）：
 *  - 向上滑：Header **先折叠**，折叠到底之后列表才开始滚动 → 用 ① `onPreScroll`
 *  - 向下滑：列表**先滚到顶**，到顶之后 Header 才展开 → 用 ② `onPostScroll`
 *  - 松手时 Header 停在半路 → 用 ④ `onPostFling` 做一次吸附动画
 */
@Composable
fun CollapsingHeaderDemo(modifier: Modifier = Modifier) {
    // 折叠进度：0f = 完全展开，1f = 完全折叠。
    // 用 rememberSaveable 保存「比例」而不是「像素」，旋转屏幕 / 换 DPI 都不会错乱。
    var progress by rememberSaveable { mutableFloatStateOf(0f) }

    // 可折叠行程（px）：progress 每变化 1.0，Header 高度就变化这么多像素。
    // connection 里要在「进度」和「像素」之间来回换算，所以必须先拿到 px。
    val travelPx = with(LocalDensity.current) {
        (ExpandedHeaderHeight - CollapsedHeaderHeight).toPx()
    }

    // 关键：connection 必须 remember 成稳定实例。
    // 每次重组都新建一个 connection，会让嵌套滚动图反复重建，既浪费也可能丢事件。
    val connection = remember(travelPx) {
        CollapsingHeaderConnection(
            travelPx = travelPx,
            readProgress = { progress },
            writeProgress = { progress = it },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 把自己注册进嵌套滚动链：后代（下面的 LazyColumn）滚动时，
            // 事件会一路冒泡到这个 connection。
            .nestedScroll(connection),
    ) {
        CollapsibleHeader(progress = progress)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(count = 60) { index ->
                DemoRow(
                    title = "列表项 #$index",
                    subtitle = "上滑：Header 先折叠 → 列表再滚动；下滑：列表先到顶 → Header 再展开",
                )
            }
        }
    }
}

/**
 * 折叠 Header 的嵌套滚动连接实现。
 *
 * 设计成普通 class（而不是在 @Composable 里写匿名 object），是为了：
 *  - 可以持有 travelPx 做像素换算；
 *  - 通过读写 lambda 访问 Compose 状态，自身不依赖任何 Composable 作用域；
 *  - 便于写单元测试。
 */
private class CollapsingHeaderConnection(
    private val travelPx: Float,
    private val readProgress: () -> Float,
    private val writeProgress: (Float) -> Unit,
) : NestedScrollConnection {

    /**
     * 把「纵向像素增量」转成进度增量并写入状态，返回本次真正消费掉的像素偏移。
     *
     * 注意符号：`available.y` 为负代表向上滑，此时 progress 应该变大（折叠），
     * 所以进度增量是 `-deltaY / travelPx`；返回值则要还原回与 `available.y` 同号的像素量。
     */
    private fun consume(deltaY: Float): Offset {
        val old = readProgress()
        val new = (old - deltaY / travelPx).coerceIn(0f, 1f)
        writeProgress(new)
        val consumedProgress = new - old
        return Offset(x = 0f, y = -consumedProgress * travelPx)
    }

    /** 阶段①：上滑（y < 0）时抢先消费，先把 Header 折叠掉。 */
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // 惯性阶段不在这里处理，交给 onPostFling，避免两套逻辑打架。
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y >= 0f) return Offset.Zero
        return consume(available.y)
    }

    /** 阶段②：列表已经滚到顶、还剩向下的量（y > 0）时，把 Header 展开。 */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y <= 0f) return Offset.Zero
        return consume(available.y)
    }

    /**
     * 阶段③：列表准备做惯性滑动之前先问父级要不要抢速度。
     *
     * 这里故意返回 [Velocity.Zero]（不抢），让列表把惯性完整跑完，
     * 剩余速度再由 ④ `onPostFling` 收尾 —— 这样「快速甩动列表」时依然顺滑。
     * 如果反过来在 onPreFling 里就把速度吃掉，列表会立刻停住，手感很生硬。
     */
    override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

    /**
     * 阶段④：列表 fling 结束后拿到剩余速度，做两件事：
     *  1. 剩余速度足够大 → 顺势把 Header 完全折叠 / 完全展开；
     *  2. Header 停在半路 → 吸附到最近的端点，避免停在尴尬的中间态。
     *
     * 返回 `available` 表示「速度被我全部吃掉」，阻止它继续往更上层冒泡。
     */
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val current = readProgress()
        val target = when {
            available.y < 0f && current < 1f && abs(available.y) > SnapVelocityThreshold -> 1f
            available.y > 0f && current > 0f && abs(available.y) > SnapVelocityThreshold -> 0f
            current in 0.02f..0.98f -> if (current > 0.5f) 1f else 0f
            else -> return Velocity.Zero
        }
        // onPreFling/onPostFling 是 suspend 的，可以直接在里面跑动画。
        // animate 每帧回写进度 → 触发重组 → Header 高度跟着变。
        animate(
            initialValue = current,
            targetValue = target,
            animationSpec = tween(durationMillis = 220),
        ) { value, _ ->
            writeProgress(value)
        }
        return available
    }
}

/** Header 本体：高度随 progress 在 200dp ~ 56dp 之间连续变化。 */
@Composable
private fun CollapsibleHeader(progress: Float, modifier: Modifier = Modifier) {
    val currentHeight: Dp =
        CollapsedHeaderHeight + (ExpandedHeaderHeight - CollapsedHeaderHeight) * (1f - progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            // 高度在逐帧变化，用 clipToBounds 裁掉超出部分，做出「卷起来」的观感
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            )
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = "可折叠 Header",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            // 折叠后这行副标题被裁掉，只剩标题，等价于一个吸顶工具栏
            Text(
                text = "progress = ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
