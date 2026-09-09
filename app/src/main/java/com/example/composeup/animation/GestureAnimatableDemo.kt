package com.example.composeup.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 示例⑥：`Animatable` —— 手势驱动的、可打断的底层动画。
 *
 * 前面 5 个示例的动画都是「目标驱动」：给一个 target，框架自己补间过去。但真实手势场景
 * （拖拽、滑动关闭、惯性 fling）有三个它们满足不了的诉求：
 *
 * 1. **跟手**：拖动时值要每一帧精确等于手指位移，不能有「补间延迟」。
 * 2. **带速度**：松手时要拿手指的瞬时速度做惯性滑动（fling），而不是慢悠悠补间到某个点。
 * 3. **可打断**：惯性滑动途中，用户再次按下要能立刻接管；点「归位」要能马上改变去向。
 *
 * [Animatable] 就是为此而生的最底层 API：它是一个**可变的、持有当前值和速度的动画状态**，
 * 提供三个 `suspend` 操作，且它们共用一把互斥锁（mutatorMutex）——
 * **后发起的调用会自动取消前一个正在跑的动画**，这正是「可打断」的实现原理。
 *
 * | 操作 | 用途 |
 * | --- | --- |
 * | `snapTo(value)` | 瞬间设值，无动画。拖动时逐帧调用，实现「跟手」 |
 * | `animateTo(target, spec)` | 补间到目标，可带 spring 回弹；目标会被 bounds 夹紧 |
 * | `animateDecay(velocity, decaySpec)` | 用初速度做衰减惯性滑动，撞到 bounds 自动停 |
 *
 * 配合 `updateBounds(lower, upper)`，值和目标都会被限制在区间内，惯性滑动也会在边界处停下，
 * 不用自己写「越界后拉回」的逻辑。
 */
@Composable
fun GestureAnimatableDemo(modifier: Modifier = Modifier) {
    var status by remember { mutableStateOf("拖动小球，或点下面的按钮") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DemoNote(
            "小球可在轨道内左右拖动。拖动时 snapTo 跟手；松手后用 VelocityTracker 的速度做 " +
                "animateDecay 惯性滑动，撞到边界自动停。惯性途中再次拖动 / 点按钮，动画会被立刻打断接管。",
        )

        // 用 BoxWithConstraints 量出轨道真实宽度，算出小球中心可移动的 ±travelPx。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val density = LocalDensity.current
            val travelPx = with(density) { (maxWidth.toPx() - 56.dp.toPx()) / 2f }
            DraggableBall(
                travelPx = travelPx,
                onStatus = { status = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        DemoSectionTitle("为什么手势动画不能用 animateFloatAsState？")
        DemoNote(
            "• animateFloatAsState 是「目标驱动」：你改 target，它补间过去。拖动时手指每帧给的是" +
                "**位移增量**而非目标，用它会产生「追不上手指」的黏滞感。\n" +
            "• 它不暴露速度，松手无法接惯性 fling。\n" +
            "• 它的补间不可被手势从中间无缝接管。\n\n" +
            "Animatable 则把「当前值 + 当前速度」都交到你手里：snapTo 负责跟手、animateDecay 负责惯性、" +
                "animateTo 负责归位，三者靠同一把互斥锁天然可打断。Material3 的 Slider、Switch、" +
                "SwipeToDismiss、AnchoredDraggable 底层全是它。",
        )
    }
}

/**
 * 可拖动的小球：内部持有 [Animatable]，处理手势、惯性与归位。
 * 交互按钮也放在这里，才能直接操作同一个 Animatable 实例。
 */
@Composable
private fun DraggableBall(
    travelPx: Float,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animatable 是一个有状态对象，必须 remember 复用；初值 0f = 轨道正中。
    val offsetX = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val decaySpec = remember { exponentialDecay<Float>(frictionMultiplier = 1f) }

    // 轨道宽度变化时（旋转屏幕等）同步更新边界，之后所有动画都会自动被夹在 ±travelPx 内。
    LaunchedEffect(travelPx) { offsetX.updateBounds(-travelPx, travelPx) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 轨道：固定高度，小球在其中居中，靠 offset 左右移动。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    // 用 offset 的 lambda 版本：每帧只在布局阶段读值，不触发重组。
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(travelPx, decaySpec) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                // 按下即打断正在跑的惯性 / 归位动画（新的 snapTo 会取消前一个动画）。
                                onStatus("onDragStart：接管，打断进行中的动画")
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                // 跟手：逐帧把增量叠加到当前值。bounds 会自动夹紧越界部分。
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            },
                            onDragEnd = {
                                val vx = velocityTracker.calculateVelocity().x
                                onStatus("onDragEnd：v=%.0f px/s → animateDecay 惯性滑动".format(vx))
                                // animateDecay 是 Animatable 的成员挂起函数，撞到 bounds 会自动停。
                                scope.launch { offsetX.animateDecay(vx, decaySpec) }
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                onStatus("onDragCancel：手势取消")
                            },
                        )
                    },
            )
        }

        // 实时读数：值、速度、是否正在运行。
        DemoReadout(
            "offsetX = ${offsetX.value.roundToInt()} px · velocity = ${offsetX.velocity.roundToInt()} px/s · " +
                "isRunning = ${offsetX.isRunning}",
        )

        DemoButtonRow {
            DemoButton(text = "归位") {
                // spring 可回弹；若此刻惯性还没停，这个 animateTo 会立即打断它。
                scope.launch {
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    )
                }
                onStatus("点击「归位」：animateTo(0) 打断当前动画并回弹到中点")
            }
            DemoButton(text = "推到右端") {
                scope.launch { offsetX.animateTo(travelPx, spring(stiffness = Spring.StiffnessMedium)) }
                onStatus("点击「推到右端」：animateTo(+travel)，目标被 bounds 夹在右边界")
            }
        }
    }
}
