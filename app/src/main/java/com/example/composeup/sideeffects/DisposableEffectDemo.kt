package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 示例④：`DisposableEffect` —— 需要「清理」的副作用。
 *
 * 有些副作用在 key 变化、或 composable 离开组合时必须**善后**（注销监听器、取消订阅、释放资源），
 * 否则就会泄漏。`DisposableEffect` 要求 block 的**最后一句**必须是 `onDispose { }`（否则编译报错）。
 * 当 key 变化时，会先执行旧的 `onDispose`，再用新 key 重新执行 block（dispose → reset）。
 *
 * 官方例子：基于生命周期事件上报埋点——用 `DisposableEffect` 注册 `LifecycleEventObserver`，
 * `onDispose` 里注销：
 * ```
 * val currentOnStart by rememberUpdatedState(onStart)   // 回调不作为 key，用 rememberUpdatedState 包
 * val currentOnStop by rememberUpdatedState(onStop)
 * DisposableEffect(lifecycleOwner) {                     // lifecycleOwner 变 → dispose 并重置
 *     val observer = LifecycleEventObserver { _, event ->
 *         if (event == ON_START) currentOnStart() else if (event == ON_STOP) currentOnStop()
 *     }
 *     lifecycleOwner.lifecycle.addObserver(observer)
 *     onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
 * }
 * ```
 *
 * 【怎么观察】进入本页后按 **Home 键切后台再回来**：日志会打出 ON_STOP / ON_START；
 * 离开本页（返回话题列表）时会触发 onDispose 注销。点「重新注册」改变 key，可看到「先注销旧的、再注册新的」。
 */
@Composable
fun DisposableEffectDemo(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    fun log(line: String) { logs = (listOf(line) + logs).take(40) }

    // (A) 复刻官方：生命周期观察者埋点。
    LifecycleAnalyticsEffect(
        lifecycleOwner = lifecycleOwner,
        onStart = { log("ON_START → 上报『进入前台』埋点") },
        onStop = { log("ON_STOP → 上报『退到后台』埋点") },
    )

    // (B) 用一个可改的 key 直观展示「key 变化 → 先 onDispose 再重新注册」。
    var registrationId by rememberSaveable { mutableIntStateOf(1) }
    DisposableEffect(registrationId) {
        log("＋ 注册 observer #$registrationId")
        onDispose { log("－ 注销 observer #$registrationId（onDispose）") }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "按 Home 键切后台再回来观察 ON_STOP/ON_START；点「重新注册」改变 key，看日志里「先注销旧的、再注册新的」；" +
                "返回上一页（离开组合）会触发 onDispose 注销。",
        )
        ButtonRow {
            DemoButton(text = "重新注册（改变 key）") { registrationId++ }
            DemoButton(text = "清空日志") { logs = emptyList() }
        }
        SectionTitle("生命周期 / 注册日志（最新在上）")
        LogBox(logs)

        SectionTitle("官方写法")
        CodeBlock(
            """
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) currentOnStart()
                    else if (event == Lifecycle.Event.ON_STOP) currentOnStop()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {                                   // 必须是 block 的最后一句
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            """.trimIndent(),
        )

        SectionTitle("要点")
        Text(
            text = "• 需要清理的副作用一律用 DisposableEffect；`onDispose { }` 必须是 block 最后一句，否则编译报错。\n" +
                "• key 变化时：先执行旧的 onDispose，再用新 key 重新执行 block（dispose → reset）。\n" +
                "• 用到的 lifecycleOwner 之类会变的值要作为 key 传入；onStart/onStop 这类回调用 rememberUpdatedState 包，不作为 key。\n" +
                "• 空的 onDispose 是坏味道——若无需清理，说明该用别的 effect（如 SideEffect / LaunchedEffect）。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** 复刻官方 HomeScreen 里的埋点副作用：注册/注销 LifecycleEventObserver。 */
@Composable
private fun LifecycleAnalyticsEffect(
    lifecycleOwner: LifecycleOwner,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    // 安全地持有最新回调：重组带来新 lambda 时更新引用，但不作为 DisposableEffect 的 key。
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnStop by rememberUpdatedState(onStop)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                currentOnStart()
            } else if (event == Lifecycle.Event.ON_STOP) {
                currentOnStop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
