package com.example.composeup.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * 示例②：操作符 —— 把「一条流」变换成「另一条流」，并用真实**搜索框**串起来。
 *
 * 操作符分两类：
 *  - **中间操作符**（惰性，返回新流）：`map / filter / transform / take / debounce /
 *    distinctUntilChanged / combine / zip / flatMapLatest …`
 *  - **终止操作符**（触发执行）：`collect / toList / first / reduce …`（见示例①）
 *
 * 本示例的「搜索框」几乎覆盖了日常最常用的组合拳，也是真实项目里搜索功能的标准写法：
 * > 用户输入 → **debounce** 防抖（停止输入 300ms 才继续，过滤连击）
 * > → **distinctUntilChanged** 去重（内容没变不重复检索）
 * > → **flatMapLatest**（新关键词到来时**取消**上一次还在进行的检索，只保留最新结果）
 * > → collect 渲染结果列表。
 *
 * 下半部分再用 **combine** 合并两条独立变化的流（价格 × 汇率），体会它「各取最新值」的语义。
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun OperatorsDemo(modifier: Modifier = Modifier) {
    // 用 MutableStateFlow 承载输入，方便把「文本变化」当成一条流来接操作符。
    val queryFlow = remember { MutableStateFlow("") }
    val query by queryFlow.collectAsStateWithLifecycle()
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchStatus by remember { mutableStateOf("等待输入…") }

    // 搜索管线：界面进入组合时启动，离开时自动取消（LaunchedEffect 的生命周期）。
    LaunchedEffect(Unit) {
        queryFlow
            .debounce(300.milliseconds)                         // 停止输入 300ms 才往下走
            .distinctUntilChanged()                // 与上一次相同则跳过
            .onEach { q ->
                searchStatus = if (q.isBlank()) "空查询：展示推荐" else "检索中：$q …"
            }
            .flatMapLatest { q -> searchArticles(q) } // 关键：取消过期检索，只跟最新关键词
            .collect { list ->
                results = list
                searchStatus = "命中 ${list.size} 条"
            }
    }

    // combine 演示：两条独立节奏的流，combine 每次取「各自最新值」组合输出。
    var combineOut by remember { mutableStateOf("等待两条流各自发出首个值…") }
    LaunchedEffect(Unit) {
        val prices = flow {
            var p = 100
            while (true) {
                delay(700.milliseconds); p += Random.nextInt(-5, 6); emit(p)
            }
        }
        val rates = flow {
            var r = 7.00
            while (true) {
                delay(1100.milliseconds); r += Random.nextDouble(-0.10, 0.10); emit(r)
            }
        }
//        prices.combineTransform(rates) { p, r ->
//            emit("价格 $p × 汇率 ${"%.2f".format(r)} = ${"%.2f".format(p * r)}")
//        }.collect { combineOut = it }
//        combine(prices, rates) {
//                p, r -> "价格 $p × 汇率 ${"%.2f".format(r)} = ${"%.2f".format(p * r)}"
//        }.collect { combineOut = it }
        prices.combine(rates) { p, r ->
            "价格 $p × 汇率 ${"%.2f".format(r)} = ${"%.2f".format(p * r)}"
        }.collect { combineOut = it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle("实时搜索：debounce + distinctUntilChanged + flatMapLatest")
        Note(
            "在下方输入关键词（如「Compose」「Flow」「状态」）。快速连打时不会每次都检索——" +
                    "debounce 等你停顿 300ms；输入没变化不重复检索；新关键词会取消上一次的过期检索。",
        )
        OutlinedTextField(
            value = query,
            onValueChange = { queryFlow.value = it },
            label = { Text("搜索文章") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Readout(searchStatus)
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (results.isEmpty()) {
                Text("（无匹配结果）", style = MaterialTheme.typography.bodySmall)
            }
            results.forEach { item ->
                Text(
                    text = "· $item",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        SectionTitle("combine：两条流各取最新值")
        Note("价格流每 700ms 变一次、汇率流每 1100ms 变一次，combine 在任一流更新时都用两者最新值重算。")
        Readout(combineOut)

        SectionTitle("操作符速查表")
        CodeBlock(
            """
            // —— 变换 ——
            map { it * 2 }                  // 一对一变换
            filter { it > 0 }               // 过滤
            transform { emit(...) }         // 最灵活：可变 0/1/多 个输出
            scan(0) { acc, v -> acc + v }   // 累积（含初始值，逐个发出）
            take(5) / drop(2)               // 截取

            // —— 合并多条流 ——
            a.combine(b) { x, y -> ... }    // 任一更新即输出「各自最新值」（UI 状态聚合首选）
            a.zip(b) { x, y -> ... }        // 一一配对：等两边都到齐才输出，用完即止
            merge(a, b)                      // 合并成一条，谁先来发谁

            // —— 时间 / 去重 ——
            debounce(300)                   // 停顿指定时间才放行（搜索框）
            distinctUntilChanged()          // 相邻重复值只发一次
            delayEach(100)                  // 每个值之间加延迟

            // —— 展平高阶流（Flow<Flow<T>> → Flow<T>）——
            flatMapLatest { q -> search(q) }// 只保留最新内层流，取消旧的（搜索/切换详情）
            flatMapConcat { ... }           // 顺序拼接，一个接一个
            flatMapMerge { ... }            // 并发收集内层流
            """.trimIndent(),
        )

        SectionTitle("combine vs zip：最容易混的一对")
        Text(
            text = "• combine：任一条流发新值，就用「两边各自的最新值」重新组合，适合聚合多个 UI 状态源；\n" +
                    "• zip：严格按顺序一一配对，第 n 个只和第 n 个组合，某条流结束则整体结束，适合「成对」数据；\n" +
                    "• flatMapLatest 是搜索/详情页的灵魂：它保证「只处理最新输入」，旧请求被自动取消，不堆积、不闪烁。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
