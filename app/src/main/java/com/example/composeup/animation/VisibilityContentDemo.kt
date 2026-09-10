package com.example.composeup.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** AnimatedVisibility 的几种进出场预设。 */
private enum class VisibilityPreset(val label: String, val note: String) {
    FadeExpand(
        label = "淡入 + 展开",
        note = "fadeIn() + expandVertically() / shrinkVertically() + fadeOut()：内容一边淡入一边把高度撑开，" +
            "适合「点击展开一段说明文字」。expand/shrink 会真正改变布局尺寸，把下方内容顶下去。",
    ),
    Slide(
        label = "横向滑入",
        note = "slideInHorizontally { it } / slideOutHorizontally { -it }：从右侧滑入、向左滑出。" +
            "slide 属于绘制阶段的位移，不改变父容器尺寸，适合工具条、Snackbar、抽屉。",
    ),
    Scale(
        label = "缩放淡入",
        note = "scaleIn(initialScale = 0.6f) + fadeIn() / scaleOut() + fadeOut()：从中心放大淡入。" +
            "常用于弹窗、气泡、FAB 的点缀式出现。",
    ),
}

/** AnimatedContent 展示的几屏内容。 */
private val Screens = listOf("首页", "发现", "消息", "我的")
private val ScreenColors = listOf(
    Color(0xFF90CAF9),
    Color(0xFFA5D6A7),
    Color(0xFFFFCC80),
    Color(0xFFF48FB1),
)

/**
 * 示例③：组件级动画 —— 框架替你处理「出现 / 消失 / 换内容」。
 *
 * 前两个示例都是「值动画」：框架算值，你负责画。这一类正好相反 ——
 * **框架直接托管了 Composable 的进出场**，你只需描述「怎么进、怎么出」。
 *
 * | 组件 | 解决什么 | 关键参数 |
 * | --- | --- | --- |
 * | [AnimatedVisibility] | 一个组件的**显示 / 隐藏**带过渡 | `enter` / `exit`（EnterTransition / ExitTransition） |
 * | [AnimatedContent] | 状态变了，**整块内容替换**带过渡 | `transitionSpec`（新内容进 + 旧内容出，用 `togetherWith` 编排） |
 * | [Crossfade] | 多个组件之间**交叉淡入淡出** | `animationSpec`（只有 alpha，最简单） |
 *
 * 三者的进出场都由若干「原子过渡」用 `+` 组合而成：
 * `fadeIn/fadeOut`、`slideIn/slideOut*`、`expand/shrink*`、`scaleIn/scaleOut`。
 * 其中 slide / scale 只影响绘制、不改变布局尺寸；expand / shrink 会真正改变尺寸并顶开邻居。
 */
@Composable
fun VisibilityContentDemo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        VisibilitySection()
        AnimatedContentSection()
        CrossfadeSection()
    }
}

/* ------------------------------------------------------- AnimatedVisibility */

@Composable
private fun VisibilitySection() {
    var visible by rememberSaveable { mutableStateOf(true) }
    var preset by rememberSaveable { mutableStateOf(VisibilityPreset.FadeExpand) }

    val (enter, exit) = when (preset) {
        VisibilityPreset.FadeExpand ->
            (fadeIn(tween(300)) + expandVertically(tween(300))) to
                (shrinkVertically(tween(300)) + fadeOut(tween(300)))
        VisibilityPreset.Slide ->
            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) to
                (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)))
        VisibilityPreset.Scale ->
            (scaleIn(tween(300), initialScale = 0.6f) + fadeIn(tween(300))) to
                (scaleOut(tween(300)) + fadeOut(tween(300)))
    }

    DemoSectionTitle("① AnimatedVisibility：显示 / 隐藏带过渡")
    DemoOptionChips(
        options = VisibilityPreset.entries.map { it to it.label },
        selected = preset,
        onSelect = { preset = it },
    )
    DemoButtonRow {
        DemoButton(text = if (visible) "隐藏" else "显示") { visible = !visible }
    }
    DemoNote(preset.note)

    // 用一个固定高度的舞台承载，避免「展开 / 收起」把整页顶来顶去，看不清动画本身。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("我是一段会被优雅地显示 / 隐藏的内容", color = Color.White)
            }
        }
    }
}

/* ---------------------------------------------------------- AnimatedContent */

@Composable
private fun AnimatedContentSection() {
    var index by rememberSaveable { mutableIntStateOf(0) }

    DemoSectionTitle("② AnimatedContent：状态变化时整块内容替换")
    DemoButtonRow {
        DemoButton(text = "下一屏") { index = (index + 1) % Screens.size }
    }
    DemoNote(
        "transitionSpec 用 `togetherWith` 把「新内容怎么进」和「旧内容怎么出」编排在一起。" +
            "本例：新内容从下方滑入并淡入（略延迟），旧内容向上滑出并淡出，形成连贯的翻页感。",
    )

    AnimatedContent(
        targetState = index,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 90)) +
                slideInVertically(tween(280)) { it / 2 })
                .togetherWith(
                    fadeOut(tween(90)) + slideOutVertically(tween(280)) { -it / 2 },
                )
        },
        label = "screenContent",
    ) { targetIndex ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ScreenColors[targetIndex % ScreenColors.size]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Screens[targetIndex],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/* ------------------------------------------------------------------ Crossfade */

@Composable
private fun CrossfadeSection() {
    var index by rememberSaveable { mutableIntStateOf(0) }

    DemoSectionTitle("③ Crossfade：多屏之间交叉淡入淡出")
    DemoButtonRow {
        DemoButton(text = "切换") { index = (index + 1) % Screens.size }
    }
    DemoNote(
        "Crossfade 是 AnimatedContent 的简化版：只淡入淡出、不做位移。" +
            "同一时刻新旧两屏叠加，旧的 alpha 从 1→0、新的从 0→1。适合底部导航切页这种「不想要花哨位移」的场景。",
    )

    Crossfade(
        targetState = index,
        animationSpec = tween(durationMillis = 400),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        label = "crossfadeScreens",
    ) { targetIndex ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ScreenColors[targetIndex % ScreenColors.size]),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Screens[targetIndex],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
