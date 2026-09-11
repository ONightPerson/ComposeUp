package com.example.composeup.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.compose.currentStateAsState
import kotlin.time.Duration.Companion.milliseconds

/** 每 500ms +1 的冷流，模拟「持续推送的数据源」（时钟 / 定位 / 行情）。 */
private fun tickFlow(): Flow<Int> = flow {
    var i = 0
    while (true) {
        delay(500.milliseconds)
        emit(++i)
    }
}

/**
 * 示例⑥：生命周期收集 —— 让「热流 / 持续数据源」只在界面可见时才收集。
 *
 * 问题：像 `tickFlow()` 这种持续发出的流，如果用朴素的 `collectAsState` 收集，
 * 即使 App 切到后台、界面不可见，收集协程**仍在跑**——白白耗电、耗流量、耗 CPU，
 * 严重时还会因为持有回调导致泄漏。
 *
 * 正确做法是让收集与生命周期挂钩，界面低于某个状态（通常 `STARTED`）就**自动取消**收集、
 * 回到该状态再**自动重启**。Compose 里有三种姿势，本示例同时跑起来做对比：
 *
 *  1. `collectAsStateWithLifecycle(initial)` —— **Compose 首选**，一行搞定，内部就是 repeatOnLifecycle；
 *  2. `lifecycle.repeatOnLifecycle(STARTED) { flow.collect { } }` —— 通用（View/Compose 皆可）的手动写法；
 *  3. `flowWithLifecycle(lifecycle, STARTED)` —— 把「生命周期门控」作为一个操作符挂在流上，再照常收集。
 *
 * 【怎么观察】进入本页后按 **Home 键切到后台**、停留几秒，再切回来：
 *  - 「安全计数」和「repeatOnLifecycle 计数」在后台会**停住**，回前台继续；
 *  - 「朴素 collectAsState 计数」在后台**仍持续增长**——这正是要避免的浪费；
 *  - 下方「生命周期事件日志」会打出 ON_PAUSE / ON_STOP / ON_START / ON_RESUME。
 */
@Composable
fun LifecycleCollectDemo(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // (1) Compose 首选：生命周期安全的收集。界面不可见时自动停止。
    val safeTick by remember { tickFlow() }.collectAsStateWithLifecycle(initialValue = 0)

    // (2) 对照组：朴素 collectAsState —— 不感知生命周期，后台仍会持续收集。
    val naiveTick by remember { tickFlow() }.collectAsState(initial = 0)

    // (3) 手动 repeatOnLifecycle：进入 STARTED 启动收集，低于 STARTED 取消，回来再重启。
    var manualTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 这个 block 会在每次达到 STARTED 时重新启动、跌破时取消，所以要放「幂等」的收集逻辑。
            tickFlow().collect { manualTick = it }
        }
    }

    // 生命周期事件日志：让「收集何时被暂停/恢复」看得见。
    var lcLog by remember { mutableStateOf<List<String>>(emptyList()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lcLog = (listOf("生命周期事件：$event") + lcLog).take(20)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "三个计数都来自「每 500ms +1」的流。按 Home 键切后台停留几秒再回来，对比三者的差异。",
        )

        Stage(height = 170.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "安全计数（WithLifecycle）：$safeTick",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "repeatOnLifecycle 计数：$manualTick",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "朴素 collectAsState 计数：$naiveTick",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "当前生命周期：${lifecycleOwner.lifecycle.currentStateAsState().value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Note(
            "预期：后台期间「安全计数 / repeatOnLifecycle 计数」停住，" +
                "「朴素计数」继续涨——回到前台后安全计数从暂停处继续（会明显落后于朴素计数）。",
        )

        SectionTitle("生命周期事件日志（最新在上）")
        LogBox(lcLog, minHeight = 110.dp)

        SectionTitle("三种安全收集写法")
        CodeBlock(
            """
            // (1) Compose 首选：一行搞定，内部即 repeatOnLifecycle(STARTED)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val v by flow.collectAsStateWithLifecycle(initial = 0)

            // (2) 通用手动写法（View / Compose 都行）
            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    // 每次回到 STARTED 都会重新执行这个 block —— 放幂等的收集逻辑
                    viewModel.uiState.collect { render(it) }
                }
            }

            // (3) 作为操作符挂在流上，再照常收集
            viewModel.uiState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { render(it) }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• 别用裸 `collectAsState` / `GlobalScope` 收集持续流：界面不可见时它们仍在跑，浪费资源。\n" +
                "• Compose 里优先 `collectAsStateWithLifecycle`；需要更细控制（多处收集、非 UI 层）用 `repeatOnLifecycle`。\n" +
                "• `repeatOnLifecycle` 的 block 会被反复启停，内部要放**幂等**逻辑，别在里面做「只做一次」的副作用。\n" +
                "• 默认门控状态是 `STARTED`（可见即收集）；需要更激进省电可用 `RESUMED`（有焦点才收集）。\n" +
                "• 冷流每次重启都会从头执行；若不想重复初始化，用 `stateIn(WhileSubscribed)` 把它转成热流（见示例③）。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
