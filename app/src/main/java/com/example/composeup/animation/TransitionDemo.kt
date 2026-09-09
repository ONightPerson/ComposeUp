package com.example.composeup.animation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 状态机的三个状态。`updateTransition` 会驱动所有子动画在状态之间平滑过渡。 */
private enum class CardState { Collapsed, Expanded, Highlighted }

/**
 * 示例②：`updateTransition` —— 一次状态变化，驱动一组同步的动画。
 *
 * 当「同一个状态切换」需要同时改变多个属性时，与其写一堆各自为政的 `animate*AsState`，
 * 不如用 [updateTransition] 建立一个**状态机**：
 *
 * 1. 你先声明一个目标状态（本例是 [CardState] 枚举）；
 * 2. `updateTransition(targetState)` 返回一个 `Transition<CardState>`；
 * 3. 在这个 Transition 上挂任意多个子动画（`animateColor` / `animateDp` / `animateFloat` …），
 *    每个子动画只需提供「某个状态 → 某个值」的映射 `targetValueByState`。
 *
 * 相比一堆 `animate*AsState`，它的好处是：
 *
 * - **同步**：所有子动画共享同一个「运行 / 结束」生命周期，天然对齐，不会出现某个属性慢半拍。
 * - **可分段定制**：`transitionSpec` 能针对「从哪个状态到哪个状态」用不同规格
 *   （本例：进入 Expanded 用回弹 spring，其余用普通 tween）。
 * - **可观测**：Transition 暴露 `currentState` / `targetState` / `isRunning`，
 *   可以据此驱动 UI（例如动画进行中禁用按钮）。
 */
@Composable
fun TransitionDemo(modifier: Modifier = Modifier) {
    var cardState by rememberSaveable { mutableStateOf(CardState.Collapsed) }

    // 建立状态机。label 用于在 Android Studio 的动画检查器里区分不同 Transition。
    val transition = updateTransition(targetState = cardState, label = "cardTransition")

    // 子动画 1：颜色。用 transitionSpec 针对「进入 Highlighted」用更快的补间。
    val color by transition.animateColor(
        transitionSpec = {
            if (targetState == CardState.Highlighted) tween(durationMillis = 200)
            else tween(durationMillis = 500)
        },
        label = "cardColor",
    ) { state ->
        when (state) {
            CardState.Collapsed -> MaterialTheme.colorScheme.primary
            CardState.Expanded -> MaterialTheme.colorScheme.secondary
            CardState.Highlighted -> MaterialTheme.colorScheme.tertiary
        }
    }

    // 子动画 2：尺寸。进入 Expanded 时用回弹弹簧，视觉上「弹」到位；其余用普通弹簧。
    val size by transition.animateDp(
        transitionSpec = {
            if (targetState == CardState.Expanded) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            } else {
                spring(stiffness = Spring.StiffnessMedium)
            }
        },
        label = "cardSize",
    ) { state ->
        when (state) {
            CardState.Collapsed -> 96.dp
            CardState.Expanded -> 176.dp
            CardState.Highlighted -> 132.dp
        }
    }

    // 子动画 3：旋转角（Float）。
    val rotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 500) },
        label = "cardRotation",
    ) { state ->
        when (state) {
            CardState.Collapsed -> 0f
            CardState.Expanded -> 90f
            CardState.Highlighted -> 180f
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DemoNote(
            "点击按钮在 收起 → 展开 → 高亮 三个状态间循环。颜色 / 尺寸 / 旋转由同一个 " +
                "updateTransition 状态机统一驱动，进入「展开」时尺寸会用回弹弹簧。",
        )
        DemoButtonRow {
            DemoButton(text = "切换到下一状态") {
                val all = CardState.entries
                cardState = all[(cardState.ordinal + 1) % all.size]
            }
        }

        DemoStage(height = 260.dp) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(RoundedCornerShape(20.dp))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = cardState.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Transition 的可观测性：动画进行中 isRunning 为 true，可用来禁用交互、显示 loading 等。
        DemoReadout(
            "currentState = ${transition.currentState} · targetState = ${transition.targetState} · " +
                "isRunning = ${transition.isRunning}",
        )
        DemoReadout("size = ${size.value} · rotation = ${rotation.toInt()}°")

        DemoSectionTitle("和一堆 animate*AsState 相比，什么时候该用 Transition？")
        DemoNote(
            "• 只有 1~2 个属性、彼此独立 → 直接用 animate*AsState 更轻量。\n" +
            "• 一个状态切换牵动 3 个以上属性、且要求它们节奏一致 / 分段定制 → 用 updateTransition。\n" +
            "• 需要知道「动画整体是否跑完」→ 只有 Transition 提供 isRunning / currentState。\n\n" +
            "补充：Transition 上还有 animateContentSize()，可让状态切换时容器尺寸也一起补间；" +
                "另有 Transition.Crossfade / Transition.AnimatedContent / Transition.SlideInSlideOut " +
                "等扩展，把状态机直接接到组件级过渡上（见示例③）。",
        )
    }
}
