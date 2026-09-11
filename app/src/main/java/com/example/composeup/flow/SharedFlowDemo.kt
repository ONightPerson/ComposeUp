package com.example.composeup.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 示例④：SharedFlow —— 可配置 replay 缓存的事件热流。
 *
 * StateFlow 与 SharedFlow 都是热流，但定位不同：
 *  - **StateFlow**：必须有初始值、只保留最新值、会去重 —— 适合「UI 状态」；
 *  - **SharedFlow**：可没有初始值、可配置 replay 缓存与缓冲策略、**不去重**、每个订阅者都能收到全部数据 —— 适合「一次性事件」。
 *
 * 本示例两段：
 *  1. **真实场景：一次性事件（Snackbar / 导航 / Toast）**。用 `replay = 0` 的 SharedFlow：
 *     新订阅者**不会**补收旧事件——这正是我们要的（切回界面不该把已消费过的 Snackbar 再弹一次），
 *     也是「事件为什么用 SharedFlow 而不是 StateFlow」的根本原因。
 *  2. **交互对比 replay = 0 / 1 / 2**：先 emit 几个值，再「添加新订阅者」，
 *     直观看到 replay 决定「新订阅者能立刻补收多少历史值」（replayCache）。
 *
 * 发送用 `tryEmit`（非挂起、立即返回是否成功），配合 `extraBufferCapacity` 与
 * `onBufferOverflow = DROP_OLDEST`，即使订阅者一时处理不过来也不会阻塞发送方。
 */
@Composable
fun SharedFlowDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    // ---------- 第一段：一次性事件 ----------
    val events = remember {
        MutableSharedFlow<String>(
            replay = 0,                                  // 事件不重放：新订阅者收不到旧事件
            extraBufferCapacity = 4,                     // 额外缓冲，tryEmit 不易失败
            onBufferOverflow = BufferOverflow.DROP_OLDEST, // 缓冲满时丢最旧的
        )
    }
    var eventLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var eventSeq by remember { mutableIntStateOf(0) }

    // 一个常驻订阅者，模拟 UI 层消费事件（此处仅打日志，真实项目里会弹 Snackbar / 触发导航）。
    LaunchedEffect(Unit) {
        events.collect { e ->
            eventLog = (listOf("🔔 收到事件 → $e（应弹 Snackbar / 导航）") + eventLog).take(40)
        }
    }

    // ---------- 第二段：replay 缓存对比 ----------
    var replay by rememberSaveable { mutableIntStateOf(1) }
    val subJobs = remember { mutableStateListOf<Job>() }
    // replay 改变时用 remember(replay) 重建一条新流，相关计数/日志一并重置。
    val shared = remember(replay) {
        MutableSharedFlow<Int>(
            replay = replay,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    var bLog by remember(replay) { mutableStateOf<List<String>>(emptyList()) }
    var bEmit by remember(replay) { mutableIntStateOf(0) }
    var bSubs by remember(replay) { mutableIntStateOf(0) }

    // 切换 replay 时，取消挂在旧流上的所有订阅者，避免遗留收集协程。
    LaunchedEffect(replay) {
        subJobs.forEach { it.cancel() }
        subJobs.clear()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("① 一次性事件（replay = 0）")
        Note(
            "点按钮发送事件，常驻订阅者立即收到。因为 replay=0，" +
                "「重新进入本页」这类新订阅不会补收历史事件——避免重复弹提示。",
        )
        ButtonRow {
            DemoButton(text = "发送一个事件") {
                eventSeq++
                events.tryEmit("Event#$eventSeq")
            }
            DemoButton(text = "连发 3 个") {
                scope.launch {
                    repeat(3) {
                        eventSeq++
                        events.tryEmit("Event#$eventSeq")
                        delay(60.milliseconds)
                    }
                }
            }
        }
        Readout("当前订阅者数：${events.subscriptionCount.value}")
        LogBox(eventLog, minHeight = 100.dp)

        SectionTitle("② replay 缓存对比：新订阅者能补收多少历史？")
        Note(
            "先选一个 replay 值，点几次「emit」填充缓存，再点「添加新订阅者」——" +
                "观察它是否立刻收到之前的值。replay=0 收不到、replay=1 收到最近 1 个、replay=2 收到最近 2 个。",
        )
        OptionChips(
            options = listOf(0 to "replay=0", 1 to "replay=1", 2 to "replay=2"),
            selected = replay,
            onSelect = { replay = it },
        )
        ButtonRow {
            DemoButton(text = "emit 一个值") {
                bEmit++
                val ok = shared.tryEmit(bEmit)
                bLog = (listOf("发射 $bEmit（tryEmit=$ok）· replayCache=${shared.replayCache}") + bLog).take(40)
            }
            DemoButton(text = "添加新订阅者") {
                bSubs++
                val id = bSubs
                subJobs += scope.launch {
                    shared.collect { v ->
                        bLog = (listOf("　订阅者#$id 收到 $v") + bLog).take(40)
                    }
                }
                bLog = (listOf("＋ 新增订阅者#$id（replay=$replay）") + bLog).take(40)
            }
        }
        Readout("replayCache=${shared.replayCache} · 订阅者数=${shared.subscriptionCount.value}")
        LogBox(bLog)

        SectionTitle("SharedFlow 构造参数")
        CodeBlock(
            """
            MutableSharedFlow<T>(
                replay = 0,                 // 新订阅者能补收的历史值数量（0 = 纯事件，不重放）
                extraBufferCapacity = 0,    // replay 之外的额外缓冲；>0 时 tryEmit 更不易失败
                onBufferOverflow = SUSPEND,  // 缓冲满时：SUSPEND 挂起 / DROP_OLDEST 丢最旧 / DROP_LATEST 丢最新
            )

            // 发事件常用非挂起的 tryEmit（立即返回 Boolean），配合 DROP_OLDEST 不阻塞发送方：
            events.tryEmit(MyEvent)

            // 把冷流变成多订阅者共享的热流：
            source.shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 0)
            """.trimIndent(),
        )

        SectionTitle("StateFlow vs SharedFlow：怎么选")
        Text(
            text = "• 表示「当前是什么状态」（登录态、表单、列表数据）→ StateFlow：有初值、去重、可 .value 同步读；\n" +
                "• 表示「发生了一件事」（弹提示、导航、播放动画）→ SharedFlow(replay=0)：不重放、不去重，避免旧事件被重复消费；\n" +
                "• 需要「新订阅者立刻拿到最近若干历史」→ SharedFlow(replay=N)，N 缓存最近 N 个值；\n" +
                "• 二者都是热流，生产环境都由 ViewModel 持有，用 WhileSubscribed 控制上游启停。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
