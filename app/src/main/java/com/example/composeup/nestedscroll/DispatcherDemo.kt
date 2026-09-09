package com.example.composeup.nestedscroll

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 小球可移动的轨道半宽。 */
private val TrackHalfWidth = 48.dp

/**
 * 什么都不做的连接。
 *
 * [Modifier.nestedScroll] 的 `connection` 是必填参数：即使某个组件只想「往外派发」而不想
 * 「接收子级事件」，也必须挂一个空连接占位。
 */
private val NoOpConnection = object : NestedScrollConnection {}

/**
 * 进阶点 2：用 [NestedScrollDispatcher] 让「自定义手势组件」参与嵌套滚动。
 *
 * 场景：页面中间嵌了一个自绘的可拖动控件（这里是一个只能在有限轨道里上下移动的小球）。
 * 它**没有** `ScrollState`，因此 Compose 完全不知道它在滚动 ——
 * 如果不做处理，用户在它上面滑动时外层页面会纹丝不动，手感非常割裂。
 *
 * 正确做法是自己扮演一次「派发子级」的角色，把 [NestedScrollConnection] 的四个阶段反过来执行：
 *
 * ```
 *  detectVerticalDragGestures 拿到 delta
 *      ├─ dispatcher.dispatchPreScroll(delta)   → 问父级要不要先吃
 *      ├─ 自己消费（在轨道范围内移动小球）
 *      └─ dispatcher.dispatchPostScroll(剩余)   → 把吃剩的交给父级
 *
 *  手指抬起
 *      ├─ dispatcher.dispatchPreFling(velocity)
 *      └─ dispatcher.dispatchPostFling(剩余速度)
 * ```
 *
 * 外层 `Column(verticalScroll)` 内部的 `scrollable` 修饰符恰好实现了
 * `onPostScroll` / `onPostFling`，所以我们派发出去的「剩余量」会被它直接拿去滚动外层页面。
 */
@Composable
fun DispatcherDemo(modifier: Modifier = Modifier) {
    val outerScrollState = rememberScrollState()
    var lastEvent by rememberSaveable { mutableStateOf("（还没有手势事件）") }

    Column(modifier = modifier.fillMaxSize()) {
        DemoNote(
            "外层是一个真正可滚动的 Column(verticalScroll)；中间的「自定义拖动条」没有任何 ScrollState，" +
                "它完全依靠 NestedScrollDispatcher 把手势增量派发给外层。\n" +
                "小球只能在 ±48dp 的轨道里移动，撞到边界后剩余的量会全部交给外层页面滚动。",
        )
        Text(
            text = lastEvent,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(outerScrollState),
        ) {
            repeat(3) { DemoPlaceholder(it, label = "外层内容（上方）$it") }

            CustomDraggableStrip(
                onEvent = { lastEvent = it },
                modifier = Modifier.fillMaxWidth(),
            )

            repeat(24) { DemoPlaceholder(it, label = "外层内容（下方）$it") }
        }
    }
}

/**
 * 自绘的可拖动控件：自己处理手势、自己消费一部分、剩下的通过 [NestedScrollDispatcher] 派发出去。
 */
@Composable
private fun CustomDraggableStrip(
    modifier: Modifier = Modifier,
    onEvent: (String) -> Unit = {},
) {
    val trackPx = with(LocalDensity.current) { TrackHalfWidth.toPx() }
    // dispatcher 与 connection 一样必须 remember 复用
    val dispatcher = remember { NestedScrollDispatcher() }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    var ballOffset by rememberSaveable { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .height(160.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            // 同时挂 connection（占位）+ dispatcher（派发），这个节点就成为滚动链里的「派发子级」
            .nestedScroll(connection = NoOpConnection, dispatcher = dispatcher)
            .pointerInput(dispatcher, trackPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // 自己接管手势，避免祖先的 scrollable 同时抢走同一次拖动
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        val delta = Offset(x = 0f, y = dragAmount)

                        // ① 先问父级：你要不要预先消费一部分？
                        val parentPreConsumed =
                            dispatcher.dispatchPreScroll(delta, NestedScrollSource.UserInput)
                        val leftForSelf = delta - parentPreConsumed

                        // ② 自己在有限轨道内消费
                        val old = ballOffset
                        val new = (old + leftForSelf.y).coerceIn(-trackPx, trackPx)
                        ballOffset = new
                        val selfConsumed = Offset(x = 0f, y = new - old)

                        // ③ 自己吃剩的抛给父级
                        val leftForParent = leftForSelf - selfConsumed
                        val parentPostConsumed = dispatcher.dispatchPostScroll(
                            consumed = selfConsumed,
                            available = leftForParent,
                            source = NestedScrollSource.UserInput,
                        )

                        onEvent(
                            "drag  Δ=${fmt(dragAmount)}  父预吃=${fmt(parentPreConsumed.y)}  " +
                                "自吃=${fmt(selfConsumed.y)}  父后吃=${fmt(parentPostConsumed.y)}",
                        )
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity()
                        // fling 的两个 dispatch 都是 suspend，必须在协程里调用。
                        // 官方建议用 dispatcher.coroutineScope，这样即使本组件在 fling 途中被销毁，
                        // 动画也能在父级的作用域里跑完，不会突然中断。
                        scope.launch {
                            val parentPreFling = dispatcher.dispatchPreFling(velocity)
                            // 小球本身没有惯性需求，把剩余速度全部交还父级
                            val parentPostFling = dispatcher.dispatchPostFling(
                                consumed = Velocity.Zero,
                                available = velocity - parentPreFling,
                            )
                            onEvent(
                                "fling v=${fmt(velocity.y)}  父预吃=${fmt(parentPreFling.y)}  " +
                                    "父后吃=${fmt(parentPostFling.y)}",
                            )
                        }
                    },
                    onDragCancel = { velocityTracker.resetTracking() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "自定义拖动条：拖动我，或把小球拖到边界后继续拖",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        )
        Box(
            modifier = Modifier
                // 用 offset { } 的 lambda 版本：只改绘制位置，不触发重组/重新测量
                .offset { IntOffset(x = 0, y = ballOffset.roundToInt()) }
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

private fun fmt(value: Float): String = "%+7.1f".format(value)
