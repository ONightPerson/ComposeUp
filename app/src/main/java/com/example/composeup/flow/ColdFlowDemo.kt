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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * 示例①：Flow 基础 —— **冷流**与「构建 → 变换 → 收集」三段式。
 *
 * 一句话心智：
 * > **Flow 是冷流。`flow { }` 里的代码不会自己跑，只有被 `collect` 时才启动；
 * > 而且每 `collect` 一次，就从头发出一遍——就像每次点播都重新拉一次流。**
 *
 * 一条 Flow 的处理分三段：
 *  1. **构建**：`flow { emit(...) }`、`flowOf(...)`、`list.asFlow()` —— 定义「数据从哪来」；
 *  2. **变换（中间操作符）**：`map / filter / onEach / take ...` —— 惰性，返回新流，不触发执行；
 *  3. **收集（终止操作符）**：`collect / toList / reduce / first ...` —— 真正按下「开始」按钮。
 *
 * 本示例用「模拟一次网络分页拉取」把这三段串起来：点「开始拉取」才真正发起（冷流），
 * 数据逐条到达（emit），并用 [LogBox] 把 `onStart / onEach / onCompletion / catch`
 * 这些**发生在时间轴上的回调**如实打印出来，让「看不见的流」变得可见。
 */
@Composable
fun ColdFlowDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // 持有正在进行的收集任务，方便「停止」时取消它（取消协程会连带取消上游 flow 的执行）。
    var job by remember { mutableStateOf<Job?>(null) }
    var collecting by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var terminalResult by remember { mutableStateOf("（点下方按钮计算）") }

    // 最新的记录放最上面，方便一眼看到刚刚发生了什么。
    fun log(line: String) {
        logs = (listOf(line) + logs).take(60)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "点「开始拉取」触发一次 collect：冷流此刻才真正执行，数据逐条 emit 到达；" +
                "再点一次会从头重新拉取（冷流特性）。全程用 onStart/onEach/onCompletion/catch 打点。",
        )
        ButtonRow {
            DemoButton(text = "开始拉取", enabled = !collecting) {
                collecting = true
                logs = emptyList()
                job = scope.launch {
                    log("▶ 调用 collect：冷流从这里才开始执行")
                    articleFeed()
                        // flowOn 只影响【上游】：让 flow{}/emit/delay 在后台线程跑，
                        // 下游的 onEach/onCompletion 等回调仍在收集者（主线程）执行，改 UI 状态才安全。
                        .flowOn(Dispatchers.Default)
                        .onStart { log("· onStart：collect 已启动，准备接收数据") }
                        .onEach { log("· onEach 收到 → $it") }
                        .catch { e -> log("✖ catch 捕获异常：${e.message}") }
                        .onCompletion { cause ->
                            // catch 已吞掉异常，所以这里 cause 通常为 null（正常结束）。
                            log("■ onCompletion：流结束（${if (cause == null) "正常" else "异常"}）")
                            collecting = false
                        }
                        .collect { /* 逐条已由 onEach 打印，这里无需再处理 */ }
                }
            }
            DemoButton(text = "停止（取消协程）", enabled = collecting) {
                job?.cancel()
                collecting = false
                log("⏹ 已 cancel：上游 flow 的执行被协作式取消")
            }
        }
        Readout(if (collecting) "状态：正在收集…" else "状态：空闲")

        SectionTitle("运行日志（最新在上）")
        LogBox(logs)

        SectionTitle("终止操作符：把流「收敛」成一个最终值")
        Note(
            "中间操作符（map/filter…）是惰性的、返回新流；只有终止操作符才真正执行并给出结果。" +
                "点按钮跑一遍 toList / reduce / first，看它们如何把一条流收敛成具体值。",
        )
        ButtonRow {
            DemoButton(text = "跑终止操作符") {
                scope.launch {
                    val list = (1..5).asFlow().toList()                 // 收集成 List
                    val sum = (1..5).asFlow().reduce { a, b -> a + b }  // 归约成单个值
                    val head = listOf("甲", "乙", "丙").asFlow().first() // 只取第一个
                    terminalResult = "toList=$list · reduce 求和=$sum · first=$head"
                }
            }
        }
        Readout(terminalResult)

        SectionTitle("三种构建方式 & 冷流本质")
        CodeBlock(
            """
            // 1) flow { } 构建器：最灵活，可 suspend、可 delay、逐条 emit
            fun articleFeed(): Flow<String> = flow {
                titles.forEach { delay(400); emit(it) }   // 假装网络耗时，一条条发出
            }

            // 2) flowOf(...)：已知固定几个值
            flowOf("A", "B", "C")

            // 3) 集合.asFlow()：把 List/Range 变成流
            (1..100).asFlow()

            // 冷流：定义时不执行，每次 collect 都从头再来一遍
            val feed = articleFeed()   // ← 这行不会发任何请求
            feed.collect { println(it) }   // ← 第一次真正执行
            feed.collect { println(it) }   // ← 又从头执行一次（与「热流」的根本区别）
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• 中间操作符惰性、可链式；终止操作符才触发执行。\n" +
                "• flow { } 里可以调用 suspend 函数（如 delay / 网络 / DB），这是它比回调强的地方。\n" +
                "• flowOn 切上游线程且不影响下游；改 Compose 状态的回调要留在主线程收集。\n" +
                "• catch 只捕获【上游】异常，且要放在 catch 之后的操作符异常它管不到。\n" +
                "• 取消是协作式的：cancel 协程会让上游 flow 在下一个挂起点停止。",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
