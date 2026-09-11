package com.example.composeup.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 本包（Flow 全家桶）内各示例复用的小组件。
 *
 * 这是一个「讲机制」的教学模块：Flow 的收发、背压、生命周期都是「看不见的时间行为」，
 * 因此除了普通卡片 / 说明，还额外需要两类容器把「随时间发生的事」显式画出来：
 *  - [CodeBlock]：等宽字体展示关键代码片段；
 *  - [LogBox]：等宽字体按时间顺序展示「每一次 emit / collect / 生命周期事件」的运行日志，
 *    让冷流、热流、缓冲、丢弃这些抽象行为变成屏幕上可滚动的一行行记录。
 */

/** Hub 话题列表里的一行卡片。 */
@Composable
internal fun FlowRow(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 小节标题。 */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 灰色说明文字。 */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * 代码块：等宽字体 + 深色底，用来贴一小段「Flow 怎么写」的源码。
 * 内容可能较宽，套一层横向滚动，避免长行被截断。
 */
@Composable
internal fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = code.trimIndent(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * 日志框：等宽字体按时间顺序展示一组「运行记录」字符串。
 *
 * Flow 的核心行为都发生在时间轴上（何时 emit、何时被 collect、缓冲是否溢出、
 * 生命周期是否暂停收集），无法用静态 UI 表达，于是把每一次事件 append 进日志再逐行渲染。
 * 约定：最新的记录放在最上面，方便一眼看到刚刚发生了什么。
 */
@Composable
internal fun LogBox(
    lines: List<String>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 120.dp,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .height(minHeight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    "（暂无输出）",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 演示舞台：固定高度、内容居中的容器，用来承载被 Flow「驱动」的真实元素，
 * 加一圈描边让进度 / 数值变化有参照物。
 */
@Composable
internal fun Stage(
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 一排操作按钮 / Chip，横向排布，放不下自动换行。content lambda 放最后，方便尾随调用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 普通按钮的小包装，统一间距与文案样式。onClick 放最后，方便用尾随 lambda 调用。 */
@Composable
internal fun DemoButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text)
    }
}

/**
 * 一组单选 Chip，用来切换「背压策略 / replay 配置」等枚举选项。
 *
 * @param options  可选项，`first` 用于比较、`second` 用于展示。
 * @param selected 当前选中值。
 * @param onSelect 点击某项时回调其值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

/** 只读的数值展示行，用来把流的中间量（生产数、消费数、当前值）实时打印出来。 */
@Composable
internal fun Readout(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
