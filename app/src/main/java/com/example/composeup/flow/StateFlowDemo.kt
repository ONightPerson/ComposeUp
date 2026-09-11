package com.example.composeup.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 示例③：StateFlow —— 持有「当前状态」的热流，UI 状态管理的主力。
 *
 * 与普通冷流不同，StateFlow 有三个天生特性，正好对上「UI 状态」的诉求：
 *  1. **永远有一个当前值**：构造时就要给初始值，任何时候 `.value` 都能同步读到；
 *  2. **conflated + 去重**：只保留最新值，且新值与旧值 `equals` 相等时**不会**再发（自带 distinctUntilChanged）；
 *  3. **热流**：不依赖 collect 才存在，多个订阅者共享同一份状态。
 *
 * 本示例用「文件下载进度」这条真实业务串起：
 *  - `MutableStateFlow` 作可写私有源，`asStateFlow()` 只读暴露（防止外部乱改）；
 *  - `update { }` 原子地基于旧值算新值（并发安全，比 `.value =` 更适合读改写）；
 *  - `.value` 同步快照读；
 *  - `stateIn(scope, WhileSubscribed(5000), 初始值)` 把一条**冷流**升级成**热 StateFlow**，
 *    并做到「有人看才跑、没人看 5 秒后自动停」，避免后台空转。
 */
@Composable
fun StateFlowDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // 演示用：直接 remember 一个状态持有者。真实项目里它应是 ViewModel，
    // 由 viewModelScope 驱动，从而在配置变更/进程存活期间保持状态。
    val model = remember { DownloadModel(scope) }

    // 把 StateFlow 收集成 Compose 状态：值一变即触发重组（生命周期安全版见示例⑥）。
    val state by model.state.collectAsStateWithLifecycle()
    val elapsed by model.elapsedSeconds.collectAsStateWithLifecycle()
    var snapshot by remember { mutableStateOf("（点按钮同步读 .value）") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "点「开始下载」：进度由 MutableStateFlow 驱动，UI 自动重组。" +
                "「运行秒数」是一条冷流经 stateIn(WhileSubscribed) 变成的热 StateFlow——" +
                "离开本页 5 秒后它会自动停止计时（回到本页又恢复）。",
        )

        Stage(height = 150.dp) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = "${state.progress}%",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(text = "状态：${state.status}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "运行秒数（stateIn）：${elapsed}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        ButtonRow {
            DemoButton(text = "开始下载", enabled = !state.running) { model.start() }
            DemoButton(text = "暂停", enabled = state.running) { model.pause() }
            DemoButton(text = "重置") { model.reset() }
        }
        ButtonRow {
            DemoButton(text = "同步读 .value 快照") {
                // StateFlow 可在任意地方（非挂起、非协程）同步读当前值——冷流做不到。
                val s = model.state.value
                snapshot = "state.value → status=${s.status}, progress=${s.progress}, running=${s.running}"
            }
        }
        Readout(snapshot)

        SectionTitle("状态持有者的标准写法")
        CodeBlock(
            """
            class DownloadModel(private val scope: CoroutineScope) {
                // 私有可写源 + 只读暴露：外部只能读、不能改，状态修改收口到本类
                private val _state = MutableStateFlow(DownloadState())
                val state: StateFlow<DownloadState> = _state.asStateFlow()

                // 冷流 → 热 StateFlow：有人订阅才跑，最后一个订阅者离开 5s 后自动停
                val elapsedSeconds: StateFlow<Int> = ticker()
                    .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

                fun start() = scope.launch {
                    _state.update { it.copy(status = "下载中", running = true) }
                    for (p in 1..100) { delay(50); _state.update { it.copy(progress = p) } }
                    _state.update { it.copy(status = "完成", running = false) }
                }
            }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• 用 `update { }` 而非 `.value =` 做「读-改-写」：前者 CAS 原子，后者并发下可能丢更新。\n" +
                "• StateFlow 会去重：给相同值不会触发下游，所以 data class 的 equals 要正确。\n" +
                "• 承载 UI 状态首选 StateFlow（有初始值、可同步读）；一次性事件用 SharedFlow（见示例④）。\n" +
                "• SharingStarted 三档：Eagerly（立即且永不停）/ Lazily（首次订阅后永不停）/ " +
                "WhileSubscribed（无人订阅即停，最省资源，UI 状态常用）。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** 下载进度 UI 状态：不可变 data class，改状态即「基于旧值算出新值」。 */
private data class DownloadState(
    val status: String = "空闲",
    val progress: Int = 0, // 0..100
    val running: Boolean = false,
)

/** 每秒 +1 的冷流，用来演示 stateIn 把它转成热 StateFlow。 */
private fun ticker() = flow {
    var s = 0
    while (true) {
        delay(1000.milliseconds)
        emit(++s)
    }
}

/**
 * 下载状态持有者：演示 MutableStateFlow 的读写、只读暴露、以及 stateIn。
 * 真实项目里这等价于一个 ViewModel。
 */
private class DownloadModel(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    // WhileSubscribed(5000)：有订阅者才运行；最后一个订阅者离开 5 秒后停止上游。
    val elapsedSeconds: StateFlow<Int> =
        ticker().stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    private var job: Job? = null

    fun start() {
        if (_state.value.running) return
        job = scope.launch {
            _state.update { it.copy(status = "下载中", running = true) }
            val from = _state.value.progress
            for (p in (from + 1)..100) {
                delay(50.milliseconds)
                _state.update { it.copy(progress = p) }
            }
            _state.update { it.copy(status = "完成", running = false) }
        }
    }

    fun pause() {
        job?.cancel()
        _state.update { it.copy(status = "已暂停", running = false) }
    }

    fun reset() {
        job?.cancel()
        _state.value = DownloadState() // 直接赋一个全新初值
    }
}
