package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 示例③：`rememberUpdatedState` —— 让 effect「不重启」也能引用到最新的值。
 *
 * `LaunchedEffect` 会在 key 变化时重启。但有些 effect 里跑的是**长生命周期、重启代价高**的操作
 * （比如一段计时），你不希望某个值一变就把它整个重启。这时用 `rememberUpdatedState` 把该值包一层：
 * effect 依旧只在启动时创建一次，但通过包装后的引用**总能读到最新值**。
 *
 * 官方例子是 `LandingScreen(onTimeout)`：
 * ```
 * val currentOnTimeout by rememberUpdatedState(onTimeout)   // 始终指向最新的 onTimeout
 * LaunchedEffect(true) {                                     // 常量 key：只随调用点生命周期，重组不重启
 *     delay(SplashWaitTimeMillis)
 *     currentOnTimeout()                                     // 用最新回调，而非启动时捕获的旧回调
 * }
 * ```
 *
 * 本示例把「正确 vs 错误」并排做出来：计时期间不断改变 `onTimeout` 携带的内容，
 * - 正确：用 `rememberUpdatedState` → 超时触发的是**最新**回调；
 * - 错误：直接用被 effect 捕获的旧回调 → 触发的是**启动那一刻**的旧内容（stale closure）。
 */
@Composable
fun RememberUpdatedStateDemo(modifier: Modifier = Modifier) {
    var version by remember { mutableIntStateOf(0) }
    var sessionId by rememberSaveable { mutableIntStateOf(0) }
    var useCorrect by rememberSaveable { mutableStateOf(true) }
    var result by remember { mutableStateOf("（等待超时触发）") }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    fun log(line: String) { logs = (listOf(line) + logs).take(40) }

    // snapshot 是「普通值」，每次重组按当前 version 重新计算——lambda 会按值捕获它。
    val snapshot = "内容#$version"

    LandingScreen(
        sessionId = sessionId,
        useCorrect = useCorrect,
        waitMillis = 4000L,
        onLog = ::log,
        // 每次重组都创建一个新的 lambda，捕获此刻的 snapshot
        onTimeout = { result = "超时触发！回调携带：$snapshot" },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Note(
                "步骤：① 选「正确/错误」；② 点「开始计时」后，在 4 秒内多点几次「改变回调内容」；" +
                    "③ 等超时触发，看 result 携带的是最新内容还是旧内容。",
            )
            OptionChips(
                options = listOf(
                    true to "正确：rememberUpdatedState",
                    false to "错误：直接用捕获的旧回调",
                ),
                selected = useCorrect,
                onSelect = { useCorrect = it; sessionId++; log("切换模式 → ${if (it) "正确" else "错误"}（重启 effect）") },
            )
            ButtonRow {
                DemoButton(text = "开始计时") {
                    sessionId++
                    result = "（等待超时触发）"
                    log("▷ sessionId=$sessionId，开始 4 秒计时")
                }
                DemoButton(text = "改变回调内容 (version++)") {
                    version++
                    log("· 重组：onTimeout 现携带「内容#$version」（effect 不应重启）")
                }
            }
            Readout("当前 snapshot = $snapshot")
            SectionTitle("触发结果")
            Text(
                text = result,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SectionTitle("日志（最新在上）")
            LogBox(logs)

            SectionTitle("要点")
            Text(
                text = "• 长生命周期 effect（如计时、订阅）不想因某个值变化而重启时，用 rememberUpdatedState 包住该值。\n" +
                    "• 常量 key（true/Unit）表示 effect 只随「调用点生命周期」，重组不重启——像 while(true) 一样要谨慎。\n" +
                    "• 错误写法会形成 stale closure：effect 里用的是启动那一刻捕获的旧值/旧回调。\n" +
                    "• 经验法则：effect 里用到的变量，要么作为 key 传入（变化即重启），要么用 rememberUpdatedState 包住（变化不重启）。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 复刻官方 LandingScreen：一段固定时长的计时，超时后回调。
 * effect 不因 [onTimeout] 变化而重启（key 是 sessionId/useCorrect，与 onTimeout 无关）。
 */
@Composable
private fun LandingScreen(
    sessionId: Int,
    useCorrect: Boolean,
    waitMillis: Long,
    onTimeout: () -> Unit,
    onLog: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    // 始终指向最新的 onTimeout；即使 LandingScreen 重组，effect 也不会重启。
    val currentOnTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(sessionId, useCorrect) {
        onLog("▶ effect 启动（计时 $waitMillis ms，期间重组不会重启它）")
        delay(waitMillis)
        if (useCorrect) {
            onLog("✓ 超时：调用 currentOnTimeout()（最新回调）")
            currentOnTimeout()
        } else {
            onLog("✗ 超时：直接调用被捕获的 onTimeout（启动时的旧回调）")
            onTimeout()
        }
    }

    content()
}
