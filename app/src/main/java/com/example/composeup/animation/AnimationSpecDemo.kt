package com.example.composeup.animation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 示例⑤：AnimationSpec —— 动画「怎么动」的规格大全，以及无限动画。
 *
 * 前面几个示例都在用 `tween(...)`。`AnimationSpec` 才是决定动画「性格」的地方：
 * 时长、缓动曲线、是否回弹、关键帧、重复方式。所有 `animate*AsState` / `Transition.animate*` /
 * `AnimatedVisibility` 等 API 都接受一个 spec。
 *
 * 四种「有限」规格：
 *
 * | 规格 | 特点 | 何时用 |
 * | --- | --- | --- |
 * | `tween(durationMillis, delayMillis, easing)` | 固定时长 + 缓动曲线，最直观 | 大多数进/出场、颜色、透明度 |
 * | `spring(dampingRatio, stiffness, visibilityThreshold)` | 物理弹簧，**没有固定时长**，可回弹 | 跟手、可打断、需要「弹性」的位移与尺寸 |
 * | `keyframes { ... }` | 手动指定「某时刻到某值」，逐段设缓动 | 有精确编排需求（如先快后顿挫） |
 * | `snap(delayMillis)` | 瞬间跳到终值，等于「没有动画」 | 需要立即到位、或在特定条件下关闭动画 |
 *
 * 缓动曲线（`Easing`）决定 tween 的速度变化：
 * [LinearEasing] 匀速、[FastOutSlowInEasing] 两头慢中间快（最常用）、
 * [LinearOutSlowInEasing] 结尾减速、[FastOutLinearInEasing] 起步加速。
 *
 * 无限动画有两条路：
 * - `repeatable` / `infiniteRepeatable` + `RepeatMode.Restart|Reverse` —— 包一层让有限动画重复；
 * - [rememberInfiniteTransition] —— 常驻动画，`animateFloat` / `animateColor` 直接产出无限循环的值。
 */
@Composable
fun AnimationSpecDemo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DemoSectionTitle("四种有限规格同场竞速")
        SpecRace()
        DemoSectionTitle("无限动画（rememberInfiniteTransition）")
        InfiniteShowcase()
    }
}

/* --------------------------------------------------------------- 规格竞速 */

@Composable
private fun SpecRace() {
    var go by rememberSaveable { mutableStateOf(false) }
    val target = if (go) 1f else 0f

    // 同一个目标值，喂给四种不同的 spec，直观对比它们的「性格」。
    val tweenState = animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = "raceTween")
    val springState = animateFloatAsState(
        target,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "raceSpring",
    )
    val keyframesState = animateFloatAsState(
        target,
        keyframes {
            durationMillis = 700
            // 关键帧给的是「绝对值」：200ms 冲到 70%，450ms 回抽到 50%，末段自动补到 target。
            // 因此 keyframes 最适合「起点→终点已知」的编排；反向播放会按同一脚本走一遍。
            0.7f at 200 using FastOutLinearInEasing
            0.5f at 450
        },
        label = "raceKeyframes",
    )
    val snapState = animateFloatAsState(target, snap(delayMillis = 350), label = "raceSnap")

    DemoButtonRow {
        DemoButton(text = if (go) "复位" else "起跑") { go = !go }
    }
    DemoNote(
        "四条轨道同一时刻出发：tween 平滑、spring 冲过头再回弹、keyframes 按脚本顿挫、snap 延迟后瞬间到位。",
    )

    // 用 BoxWithConstraints 量出轨道的真实像素宽度，把 0~1 的进度换算成位移。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val density = LocalDensity.current
        val travelPx = with(density) { maxWidth.toPx() } - with(density) { 40.dp.toPx() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RacerRow("tween(700, FastOutSlowIn)", tweenState, travelPx)
            RacerRow("spring(MediumBouncy, Low)", springState, travelPx)
            RacerRow("keyframes(顿挫)", keyframesState, travelPx)
            RacerRow("snap(delay 350)", snapState, travelPx)
        }
    }
}

@Composable
private fun RacerRow(label: String, fraction: State<Float>, travelPx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                // 在 offset 的 lambda 内读取 fraction.value：只在布局阶段读，动画期间不触发重组。
                .offset { IntOffset((fraction.value * travelPx).roundToInt(), 0) }
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/* ------------------------------------------------------------- 无限动画 */

@Composable
private fun InfiniteShowcase() {
    val infinite = rememberInfiniteTransition(label = "showcase")

    // 匀速旋转：Restart 模式，每轮从 0° 重新开始。
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinRotation",
    )
    // 呼吸缩放：Reverse 模式，到终点后原路返回，来回呼吸。
    val scale by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    // 颜色循环：同样用 Reverse 在两个颜色间来回过渡。
    val color by infinite.animateColor(
        initialValue = MaterialTheme.colorScheme.primary,
        targetValue = MaterialTheme.colorScheme.tertiary,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cycleColor",
    )

    DemoNote(
        "左：匀速旋转的加载指示；中：来回呼吸缩放的圆；右：在两色间循环渐变的方块。" +
            "它们都用 rememberInfiniteTransition，一旦进入组合就持续运行、离开组合自动停止。",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondary),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(color),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color),
        )
    }
    DemoNote(
        "经验法则：\n" +
            "• 旋转、加载动画、呼吸灯这类「常驻循环」→ rememberInfiniteTransition。\n" +
            "• 有限动画想循环几次 → repeatable(iterations = n)；想无限循环 → infiniteRepeatable。\n" +
            "• RepeatMode.Restart 每轮从头开始，RepeatMode.Reverse 每轮反向播放（更适合呼吸 / 摆动）。\n" +
            "• 无限动画只在可见时运行，离开组合会自动取消，不必手动管理生命周期。",
    )
}
