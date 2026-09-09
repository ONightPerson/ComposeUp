package com.example.composeup.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 本包内各示例复用的小组件，避免每个 Demo 都重复写一遍卡片 / 说明 / 选择器。 */

/** 一行卡片，Hub 列表与示例说明都会用到。 */
@Composable
internal fun DemoRow(
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
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
internal fun DemoSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 灰色说明文字。 */
@Composable
internal fun DemoNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 动画舞台：一个固定高度、内容居中的容器，用来承载被动画驱动的元素。
 * 加了一圈虚线感的描边，让「位移 / 缩放 / 旋转」有参照物。
 */
@Composable
internal fun DemoStage(
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
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

/** 一排操作按钮，横向排布，放不下自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DemoButtonRow(
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

/** 普通按钮的小包装，统一间距与文案样式。onClick 放在最后，方便用尾随 lambda 调用。 */
@Composable
internal fun DemoButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

/**
 * 一组单选 Chip，用来切换「动画规格 / 过渡方式」等枚举选项。
 *
 * @param options   可选项，`key` 用于比较、`label` 用于展示。
 * @param selected  当前选中的 key。
 * @param onSelect  点击某项时回调其 key。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> DemoOptionChips(
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

/** 只读的数值展示行，用来把动画中间量（速度、进度、状态机名）实时打印出来。 */
@Composable
internal fun DemoReadout(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
