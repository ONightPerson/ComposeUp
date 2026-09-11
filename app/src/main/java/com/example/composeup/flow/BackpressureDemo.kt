package com.example.composeup.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 背压策略：生产者（每 50ms 一个）远快于消费者（每个处理 150ms）时的四种应对。 */
private enum class BackpressureMode(val label: String, val explain: String) {
    DEFAULT(
        label = "默认（挂起生产者）",
        explain = "消费者处理完一个，生产者才能发下一个——Flow 天生就是背压安全的。" +
            "结果：生产数 == 消费数，一个不丢，但整体节奏被最慢的消费者拖慢。",
    ),
    BUFFER(
        label = "buffer（缓冲 + 丢最旧）",
        explain = "buffer 把上下游解耦到不同协程、各自全速；缓冲区满时按 DROP_OLDEST 丢弃最旧的值。" +
            "结果：生产者不再被拖慢（很快生产完），但消费不过来时会丢数据。",
    ),
    CONFLATE(
        label = "conflate（只留最新）",
        explain = "消费者忙时生产者照常发，但中间值被丢弃、只保留最新的那个。" +
            "结果：消费数远小于生产数，适合「只关心最新状态」（如高频刷新 UI）。",
    ),
    COLLECT_LATEST(
        label = "collectLatest（取消重来）",
        explain = "每来一个新值就取消上一次尚未完成的处理、用新值重新开始。" +
            "结果：只有最后一次处理能跑完，消费数≈1，适合「只处理最新、旧的可作废」（如搜索联想）。",
    ),
}

/**
 * 示例⑤：背压 —— 当「生产快于消费」时的四种策略。
 *
 * 背压（backpressure）= 下游处理不过来时，如何让上游「慢下来 / 缓冲 / 丢弃」。
 * Flow 的一大优点是**默认就背压安全**：`emit` 是挂起函数，消费者没准备好，生产者自然被挂起，
 * 不会像某些响应式库那样默默缓冲到内存爆炸。
 *
 * 但「默认挂起」意味着整体被最慢环节拖慢。当业务上「旧数据可丢、只要最新」时，
 * 就该主动换策略。本示例用同一条高频源（[sensorStream]：每 50ms 一个、共 20 个）
 * 配同一个慢消费者（每个处理 150ms），实时对比四种策略下「生产 vs 消费」的计数差异。
 */
@Composable
fun BackpressureDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(BackpressureMode.DEFAULT) }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var produced by remember { mutableIntStateOf(0) }
    var consumed by remember { mutableIntStateOf(0) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun log(line: String) {
        logs = (listOf(line) + logs).take(50)
    }

    fun start() {
        job?.cancel()
        produced = 0
        consumed = 0
        logs = emptyList()
        running = true
        job = scope.launch {
            log("▶ 开始：模式 = ${mode.label}（生产者 50ms/个 × 20，消费者 150ms/个）")
            // onEach 统计「生产」；下游 collect 里 delay(150) 模拟慢处理并统计「消费」。
            val base = sensorStream(intervalMs = 50, count = 20).onEach { produced++ }
            when (mode) {
                BackpressureMode.DEFAULT ->
                    base.collect { delay(150.milliseconds); consumed++ }

                BackpressureMode.BUFFER ->
                    base.buffer(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                        .collect { delay(150.milliseconds); consumed++ }

                BackpressureMode.CONFLATE ->
                    base.conflate()
                        .collect { delay(150.milliseconds); consumed++ }

                BackpressureMode.COLLECT_LATEST ->
                    base.collectLatest { delay(150.milliseconds); consumed++ }
            }
            running = false
            log("■ 结束：生产 $produced 个 / 消费 $consumed 个")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note("选一种策略后点「开始」，实时观察下方「生产 vs 消费」计数与日志差异。可在运行中切换策略重跑。")
        OptionChips(
            options = BackpressureMode.entries.map { it to it.label },
            selected = mode,
            onSelect = { mode = it },
        )
        ButtonRow {
            DemoButton(text = "开始", enabled = !running) { start() }
            DemoButton(text = "停止", enabled = running) {
                job?.cancel()
                running = false
                log("⏹ 手动停止")
            }
        }

        Readout("已生产：$produced　　已消费：$consumed　　（差值 = 被缓冲/丢弃/取消的量）")
        Note("当前策略解读：${mode.explain}")

        SectionTitle("运行日志（最新在上）")
        LogBox(logs)

        SectionTitle("四种策略写法")
        CodeBlock(
            """
            // 默认：无需任何操作符，emit 会被下游自然挂起（背压安全）
            source.collect { slowProcess(it) }

            // buffer：解耦上下游、各自全速；可指定容量与溢出策略
            source.buffer(capacity = 4, onBufferOverflow = DROP_OLDEST)
                  .collect { slowProcess(it) }

            // conflate：忙时只保留最新值，丢弃中间值
            source.conflate().collect { slowProcess(it) }

            // collectLatest：新值到来即取消上一次处理、重新开始
            source.collectLatest { slowProcess(it) }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• Flow 默认背压安全：不必像 RxJava 那样担心 unbounded buffer 撑爆内存。\n" +
                "• buffer / conflate / collectLatest 的区别，本质是「旧数据怎么处理」：缓冲、丢弃、还是取消重来。\n" +
                "• flowOn 也会引入一个内部缓冲 channel，让上游在别的线程全速生产——切线程的同时也改变了背压表现。\n" +
                "• 选择依据：一个都不能丢 → 默认；只要最新状态 → conflate；只处理最新请求 → collectLatest；" +
                "允许少量积压、削峰 → buffer。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
