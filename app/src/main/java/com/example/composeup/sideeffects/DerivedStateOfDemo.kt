package com.example.composeup.sideeffects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 示例⑦：`derivedStateOf` —— 把一个/多个状态派生成「变化更少」的状态，减少无谓重组。
 *
 * 场景：某个状态变化得非常频繁（如滚动位置 `firstVisibleItemIndex` 会 0,1,2,3… 连续变），
 * 但 UI 其实只需要在它**跨越某个阈值**时才更新（如「是否 > 0」只在 0↔1 之间翻转）。
 * `derivedStateOf` 创建一个新 State，只有当**计算结果**变化时才通知重组——行为类似 Flow 的 `distinctUntilChanged()`。
 *
 * 官方例子 MessageList：
 * ```
 * val listState = rememberLazyListState()
 * LazyColumn(state = listState) { ... }
 * val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
 * AnimatedVisibility(visible = showButton) { ScrollToTopButton() }
 * ```
 *
 * ⚠️ 官方 Caution：`derivedStateOf` 本身有开销，**只在「输入变化远多于所需重组」时**才用；
 * 把两个普通状态拼在一起（如姓名拼接）用它纯属浪费——直接算即可（见下方错误用法）。
 */
@Composable
fun DerivedStateOfDemo(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = remember { List(60) { "消息 #$it" } }

    // 只有 firstVisibleItemIndex 在 0↔非0 之间翻转时，showButton 才变化 → 才重组。
    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Note(
            "向下滚动列表：firstVisibleItemIndex 连续变化，但 showButton 只在跨过 0 时翻转——" +
                "「回到顶部」按钮因此只在需要时出现，避免了每次滚动都重组。",
        )
        Readout(
            "firstVisibleItemIndex = ${listState.firstVisibleItemIndex}（高频变） " +
                "· showButton = $showButton（derivedStateOf，低频变）",
        )

        MessageListBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            messages = messages,
            listState = listState,
            showButton = showButton,
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
        )

        SectionTitle("正确 vs 错误用法")
        CodeBlock(
            """
            // ✅ 正确：输入(firstVisibleItemIndex)变化远多于所需重组(是否>0)
            val showButton by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 }
            }

            // ❌ 错误：fullName 本就要跟 firstName/lastName 一样频繁更新，
            //    没有「多余重组」可省，用 derivedStateOf 纯属开销
            val fullNameBad by remember { derivedStateOf { "${'$'}firstName ${'$'}lastName" } }
            val fullNameCorrect = "${'$'}firstName ${'$'}lastName"   // 直接算即可
            """.trimIndent(),
        )
        Text(
            text = "• 判据：输入变化频率 ≫ UI 需要的更新频率，且你只关心「结果是否变化」时，才用 derivedStateOf。\n" +
                "• 它类似 distinctUntilChanged：结果不变就不通知重组。\n" +
                "• 记住用 `remember { derivedStateOf { } }` 持有，否则每次重组都新建、失去意义。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * 列表 + 「回到顶部」浮动按钮。
 * 单独抽成一个 composable：其内部没有 ColumnScope 接收者，`AnimatedVisibility` 会解析到顶层版本，
 * 而不是 ColumnScope 的扩展版本（否则在 Box 里用隐式接收者调用会报错）。
 */
@Composable
private fun MessageListBox(
    modifier: Modifier = Modifier,
    messages: List<String>,
    listState: LazyListState,
    showButton: Boolean,
    onScrollToTop: () -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = messages.size) { i ->
                Text(
                    text = messages[i],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        // 复刻官方：仅当 showButton 为真时显示「回到顶部」浮动按钮。
        AnimatedVisibility(
            visible = showButton,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FloatingActionButton(onClick = onScrollToTop) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        }
    }
}
