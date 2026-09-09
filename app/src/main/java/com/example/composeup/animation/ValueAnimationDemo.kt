package com.example.composeup.animation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 示例①：值动画 —— `animate*AsState` 家族。
 *
 * 这是 Compose 里最常用、也最容易上手的一类动画。核心思想只有一句话：
 *
 * > **你持有一个「目标值」状态，`animate*AsState` 会返回一个「当前正在补间的值」状态；
 * > 每次目标值改变，返回值都会自动从旧值平滑过渡到新值。**
 *
 * 它属于「值动画」：框架只负责把数值算出来，**如何把值画到屏幕上完全由你决定**。
 * 因此它最灵活，也最需要你自己注意性能（见下方关于 graphicsLayer 的说明）。
 *
 * 针对不同数据类型有各自的专用 API，好处是内置了合理的「可见性阈值」，
 * 变化小到肉眼不可见时会提前结束动画，避免无谓的逐帧计算：
 *
 * | API | 动画的值 | 典型用途 |
 * | --- | --- | --- |
 * | [animateFloatAsState] | `Float` | 透明度、旋转角、缩放比、进度 |
 * | [animateColorAsState] | `Color` | 背景色、文字色、图标着色 |
 * | [animateDpAsState] | `Dp` | 尺寸、圆角、内外边距 |
 * | [animateOffsetAsState] | `Offset` | 平面位移（x + y 一起动） |
 *
 * 本示例用一个开关同时驱动以上四种，直观展示「一个布尔状态 → 多个属性一起补间」。
 */
@Composable
fun ValueAnimationDemo(modifier: Modifier = Modifier) {
    // 唯一的数据源：一个布尔开关。下面所有动画都由它的翻转触发。
    var expanded by rememberSaveable { mutableStateOf(false) }

    // 1) 尺寸：Dp 动画。展开时变大，收起时变小。
    val size by animateDpAsState(
        targetValue = if (expanded) 160.dp else 80.dp,
        animationSpec = tween(durationMillis = 450),
        label = "boxSize",
    )
    // 2) 圆角：同样是 Dp 动画，与尺寸配合做出「方块 ↔ 圆球」的观感。
    val corner by animateDpAsState(
        targetValue = if (expanded) 80.dp else 16.dp,
        animationSpec = tween(durationMillis = 450),
        label = "boxCorner",
    )
    // 3) 颜色：Color 动画。
    val color by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 450),
        label = "boxColor",
    )
    // 4) 旋转与透明度：Float 动画。注意这两个值最终通过 graphicsLayer 应用（见下）。
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "boxRotation",
    )
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 0.6f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "boxAlpha",
    )
    // 5) 位移：Offset 动画，让一个小圆点在舞台里从左上滑到右下。
    val travelPx = with(LocalDensity.current) { 60.dp.toPx() }
    val dotOffset by animateOffsetAsState(
        targetValue = if (expanded) Offset(travelPx, travelPx) else Offset(-travelPx, -travelPx),
        animationSpec = tween(durationMillis = 450),
        label = "dotOffset",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DemoNote(
            "点击按钮翻转 expanded 状态。方块的尺寸 / 圆角 / 颜色、以及旋转 / 透明度 / 位移，" +
                "全部由这一个布尔值驱动，各自补间。",
        )
        DemoButtonRow {
            DemoButton(text = if (expanded) "收起" else "展开") { expanded = !expanded }
        }

        DemoStage(height = 240.dp) {
            // 小圆点：用 offset 的 lambda 版本 —— 只在布局阶段读取，位置变化不触发重组。
            Box(
                modifier = Modifier
                    .offset { IntOffset(dotOffset.x.roundToInt(), dotOffset.y.roundToInt()) }
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
            // 主体方块：尺寸 / 圆角 / 颜色是「会影响布局或需要重建路径」的属性，直接改 Modifier；
            // 而旋转 / 透明度这类「只影响绘制」的属性，放进 graphicsLayer 的 lambda 里，
            // 这样每一帧的插值只走绘制阶段，不会引发重组与重新测量，帧率更稳。
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(corner))
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (expanded) "展开" else "收起",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // 实时打印几个补间中的中间值，可以看到它们是连续变化而非跳变。
        DemoReadout(
            "size = ${size.value.roundToInt()}dp · corner = ${corner.value.roundToInt()}dp · " +
                "rotation = ${rotation.roundToInt()}° · alpha = ${"%.2f".format(alpha)}",
        )
        DemoReadout(
            "dotOffset = (${dotOffset.x.roundToInt()}, ${dotOffset.y.roundToInt()}) px",
        )

        DemoSectionTitle("为什么区分「改 Modifier」与「graphicsLayer」？")
        DemoNote(
            "• 尺寸(size)、圆角(shape)、颜色这类属性改变后，需要重新测量 / 布局 / 重建绘制指令，" +
                "只能走 Modifier 链，每帧都可能触发重组。\n" +
                "• 位移、缩放、旋转、透明度属于「图形层变换」，放进 graphicsLayer { } 或 offset { } 的 " +
                "lambda 后，插值只在绘制阶段生效，不触发重组和重新测量，是高频动画（尤其手势跟手）的首选。\n\n" +
                "经验法则：能用 graphicsLayer / offset lambda 表达的位移与变换，就不要去改布局尺寸。",
        )
    }
}
