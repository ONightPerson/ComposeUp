package com.example.composeup.sideeffects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 示例②：`rememberCoroutineScope` —— 拿到一个「感知组合生命周期」的作用域，在 composable 之外启动协程。
 *
 * 为什么需要它：`LaunchedEffect` 本身是 composable，只能写在 composable 函数体里；
 * 但按钮的 `onClick` 是**普通回调**（不是 composable），没法在里面写 `LaunchedEffect`。
 * 这时用 `rememberCoroutineScope()` 取得一个绑定到「调用点组合位置」的 `CoroutineScope`，
 * 在回调里 `scope.launch { }`；当该调用点离开组合时，scope 会被**自动取消**。
 *
 * 官方另一处提醒：当你需要**手动控制**一个或多个协程的生命周期（例如用户一交互就取消某个动画）时，也用它。
 *
 * 本示例复刻文章的 Snackbar 场景，并加一个「手动启停计数协程」来体现第二点。
 */
@Composable
fun RememberCoroutineScopeDemo(modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    // 绑定到本组合位置的协程作用域：离开组合时自动取消其中所有协程。
    val scope = rememberCoroutineScope()

    var counter by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Note(
                "下面按钮的 onClick 是普通回调，不能写 LaunchedEffect；用 scope.launch 启动协程调用 suspend 的 showSnackbar。" +
                    "本组合离开时 scope 会自动取消。",
            )
            ButtonRow {
                DemoButton(text = "弹 Snackbar") {
                    scope.launch {
                        // showSnackbar 是 suspend 函数，必须在协程里调用
                        snackbarHostState.showSnackbar("Something happened!")
                    }
                }
                DemoButton(text = "带操作按钮的 Snackbar") {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "已删除 1 条消息",
                            actionLabel = "撤销",
                        )
                        when (result) {
                            SnackbarResult.ActionPerformed ->
                                snackbarHostState.showSnackbar("你点了「撤销」")
                            SnackbarResult.Dismissed -> Unit
                        }
                    }
                }
            }

            SectionTitle("手动控制协程生命周期")
            Note(
                "点「开始」用 scope.launch 起一个每秒 +1 的协程并记住它的 Job；点「取消」调用 job.cancel()。" +
                    "这体现了 rememberCoroutineScope 的第二用途：手动掌控协程的启停（如用户交互即取消动画）。",
            )
            ButtonRow {
                DemoButton(text = "开始计数", enabled = job?.isActive != true) {
                    counter = 0
                    job = scope.launch {
                        while (true) {
                            delay(1000.milliseconds)
                            counter++
                        }
                    }
                }
                DemoButton(text = "取消计数", enabled = job?.isActive == true) {
                    job?.cancel()
                }
            }
            Readout("计数：$counter　协程活跃：${job?.isActive == true}")

            SectionTitle("LaunchedEffect vs rememberCoroutineScope")
            CodeBlock(
                """
                // LaunchedEffect：只能在 composable 体内；进入组合自动启动、离开自动取消
                LaunchedEffect(key) { doSuspendWork() }

                // rememberCoroutineScope：拿到 scope，在【回调】里手动 launch
                val scope = rememberCoroutineScope()
                Button(onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Hi") }  // 回调里也能跑 suspend
                }) { Text("Press me") }
                """.trimIndent(),
            )

            SectionTitle("要点")
            Text(
                text = "• 回调（onClick/onChange 等）里要跑协程 → rememberCoroutineScope；composable 体内自动跑的 → LaunchedEffect。\n" +
                    "• scope 绑定调用点的组合位置，离开组合自动取消，不会泄漏。\n" +
                    "• 需要手动 cancel（如打断动画）时，保存 launch 返回的 Job 再取消。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}
