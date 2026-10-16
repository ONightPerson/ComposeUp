package com.example.composeup.navigation3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 本包（Jetpack Navigation 3）内各个示例复用的小组件。
 *
 * Navigation 3 的核心是「你持有返回栈，库负责渲染」。这一组示例既要展示 NavDisplay
 * 真实的界面切换，又要把「返回栈此刻长什么样」「哪个装饰器回调被触发了」这些看不见的
 * 内部状态显式画出来，因此沿用 flow 模块的教学容器：
 *  - [CodeBlock]：等宽字体展示关键代码片段；
 *  - [LogBox]：按时间顺序展示运行日志（装饰器 decorate/onPop、结果收发、匹配过程）；
 *  - [BackStackView]：把当前返回栈的每个 key 画成一摞卡片，直观呈现 push/pop；
 *  - [Pane]：带底色与标题的占位块，用来充当 Scene 分栏 / NavEntry 的内容。
 */

/** Hub 话题列表里的一行卡片。 */
@Composable
internal fun Nav3Row(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String = "",
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
 * 代码块：等宽字体 + 深色底，用来贴一小段「Navigation 3 怎么写」的源码。
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
 * 日志框：等宽字体按时间顺序展示一组「运行记录」。
 *
 * Navigation 3 的很多行为发生在幕后（NavEntryDecorator 的 decorate/onPop、ResultEventBus
 * 的 send/receive、DeepLinkMatcher 的 match），把它们 append 进日志再逐行渲染，就能看得见。
 * 约定：最新记录放在最上面。
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
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
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
 * 一份可追加的运行日志。本身就是一个 [List]（直接传给 [LogBox] 渲染），
 * 又可用 `log("...")` 调用语法追加一行（最新插到最前面）。
 *
 * 底层用 [SnapshotStateList]，因此追加后 Compose 会自动重组，日志实时刷新。
 */
internal class DemoLog(
    private val delegate: SnapshotStateList<String> = mutableStateListOf(),
) : List<String> by delegate {
    /** 追加一行，最新在最上面。 */
    operator fun invoke(line: String) {
        delegate.add(0, line)
    }
}

/** 创建一份 [DemoLog]。 */
@Composable
internal fun rememberDemoLog(): DemoLog = remember { DemoLog() }

/**
 * 返回栈可视化：把栈里每个 key 画成一摞卡片，栈顶在最上面。
 * 用来直观呈现 push（add）/ pop（removeLastOrNull）后栈的变化。
 */
@Composable
internal fun BackStackView(
    keys: List<Any>,
    modifier: Modifier = Modifier,
    labelOf: (Any) -> String = { it.toString() },
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "返回栈（栈顶在上，共 ${keys.size} 项）",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (keys.isEmpty()) {
                Text("（空）", style = MaterialTheme.typography.bodySmall)
            }
            keys.reversed().forEachIndexed { index, key ->
                val isTop = index == 0
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTop) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isTop) "▲ 栈顶" else "  栈内",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = labelOf(key),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 带底色与标题的占位块，充当 Scene 分栏或 NavEntry 的内容。
 */
@Composable
internal fun Pane(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

/**
 * 顶部「屏幕区」：给 NavDisplay / Scene 一个固定高度、带描边的展示窗口，
 * 让「界面切换」与下方的控制面板、日志区分开，像在看一台真实的设备屏幕。
 */
@Composable
internal fun ScreenArea(
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(12.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
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

/** 实心按钮的小包装，统一间距与文案样式。onClick 放最后，方便用尾随 lambda 调用。 */
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

/** 描边按钮的小包装。 */
@Composable
internal fun DemoOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text)
    }
}
