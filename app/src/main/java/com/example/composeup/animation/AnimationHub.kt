package com.example.composeup.animation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 本包收录的动画进阶示例。 */
private enum class AnimationTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    ValueState(
        title = "① 值动画：animate*AsState",
        summary = "最常用的一类。把一个状态值的变化自动补间成动画：Float / Color / Dp / Offset 各有对应 API。" +
            "配合 graphicsLayer 只改绘制阶段，位移缩放旋转不触发重组。",
        keywords = "animateFloatAsState · animateColorAsState · animateDpAsState · animateOffsetAsState · graphicsLayer",
    ),
    Transition(
        title = "② updateTransition：一次状态变化驱动多个值",
        summary = "用一个状态机（枚举）统一驱动颜色、尺寸、旋转等多个动画，保证它们同步、可整体定制 transitionSpec，" +
            "还能读到 currentState / targetState / isRunning。",
        keywords = "updateTransition · Transition.animateColor · animateDp · transitionSpec · isRunning",
    ),
    VisibilityContent(
        title = "③ 组件级：AnimatedVisibility / AnimatedContent / Crossfade",
        summary = "框架替你处理「出现 / 消失 / 内容替换」的进出场动画。可选多种 enter/exit 组合，" +
            "AnimatedContent 用 transitionSpec + togetherWith 定制新旧内容的过渡编排。",
        keywords = "AnimatedVisibility · AnimatedContent · Crossfade · fadeIn/slideIn/expandIn · togetherWith",
    ),
    SizeAndList(
        title = "④ animateContentSize + 列表增删动画",
        summary = "内容尺寸变化时自动补间（可展开卡片）；LazyColumn 里用 Modifier.animateItem 让" +
            " 新增 / 删除 / 重排都有淡入淡出与位移过渡（必须提供 key）。",
        keywords = "animateContentSize · LazyItemScope.animateItem · mutableStateListOf · key",
    ),
    Specs(
        title = "⑤ AnimationSpec：规格大全与无限动画",
        summary = "tween / spring / keyframes / snap 四种有限规格的直观对比，Easing 曲线，" +
            "repeatable / infiniteRepeatable 重复，以及 rememberInfiniteTransition 做常驻动画。",
        keywords = "tween · spring · keyframes · snap · Easing · infiniteRepeatable · rememberInfiniteTransition",
    ),
    Gesture(
        title = "⑥ Animatable：手势驱动的可打断动画",
        summary = "最底层、控制力最强的 API。拖动时 snapTo 跟手，松手用 velocity + animateDecay 惯性滑动，" +
            "再 animateTo 归位；动画可被下一次手势随时打断，手感顺滑。",
        keywords = "Animatable · snapTo · animateDecay · animateTo · VelocityTracker · exponentialDecay",
    ),
}

/**
 * 动画进阶示例的入口页。
 *
 * 外层是一个话题列表，点进去看具体示例；示例内部用系统返回键或左上角箭头回到列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<AnimationTopic?>(null) }
    val topics = remember { AnimationTopic.entries.toList() }

    val current = selected
    if (current == null) {
        Surface(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Compose 进阶 · 动画",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        DemoNote(
                            "Compose 的动画按「谁来管理状态」可分为三层：值动画（你自己拿值去渲染）、" +
                                "组件级 API（框架替你处理进出场）、以及底层的 Animatable（手势驱动、可打断）。\n" +
                                "下面 6 个示例从最常用到最底层逐层展开，点击任意一项进入可交互的示例。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    DemoRow(
                        title = topic.title,
                        subtitle = topic.summary + "\n\n" + topic.keywords,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { selected = topic },
                    )
                }
            }
        }
        return
    }

    // 系统返回键先退回话题列表，而不是直接退出界面
    BackHandler { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { selected = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回话题列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            AnimationTopic.ValueState -> ValueAnimationDemo(contentModifier)
            AnimationTopic.Transition -> TransitionDemo(contentModifier)
            AnimationTopic.VisibilityContent -> VisibilityContentDemo(contentModifier)
            AnimationTopic.SizeAndList -> SizeAndListDemo(contentModifier)
            AnimationTopic.Specs -> AnimationSpecDemo(contentModifier)
            AnimationTopic.Gesture -> GestureAnimatableDemo(contentModifier)
        }
    }
}
