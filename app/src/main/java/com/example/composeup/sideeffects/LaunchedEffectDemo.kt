package com.example.composeup.sideeffects

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 示例①：`LaunchedEffect` —— 在 composable 作用域里运行 suspend 函数。
 *
 * 官方定义：当 `LaunchedEffect` **进入组合**时，用传入的 block 启动一个协程；
 * 当它**离开组合**时协程被取消；若用**不同的 key** 重组，则取消旧协程、用新 block 重启一个新协程。
 *
 * 本示例复刻文章里的「按可配置频率闪烁 alpha」动画：
 * ```
 * var pulseRateMs by remember { mutableLongStateOf(3000L) }
 * val alpha = remember { Animatable(1f) }
 * LaunchedEffect(pulseRateMs) {          // pulseRateMs 变化 → 重启 effect
 *     while (isActive) {
 *         delay(pulseRateMs)
 *         alpha.animateTo(0f)
 *         alpha.animateTo(1f)
 *     }
 * }
 * ```
 * 切换下方频率（key 改变）时，可在日志里看到旧 effect 被取消、新 effect 重启——这正是「Restarting effects」。
 */
@Composable
fun LaunchedEffectDemo(modifier: Modifier = Modifier) {
    var pulseRateMs by rememberSaveable { mutableLongStateOf(1500L) }
    val alpha = remember { Animatable(1f) }
    var pulses by remember { mutableIntStateOf(0) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    fun log(line: String) { logs = (listOf(line) + logs).take(40) }

    // key = pulseRateMs：一旦频率改变，旧协程取消、新协程重启。
    LaunchedEffect(pulseRateMs) {
        log("▶ LaunchedEffect 启动（key pulseRateMs=$pulseRateMs）")
        try {
            while (isActive) {
                delay(pulseRateMs)          // suspend：等待一个脉冲周期
                alpha.animateTo(0f)         // suspend：渐隐
                alpha.animateTo(1f)         // suspend：渐显
                pulses++
                log("· 完成第 $pulses 次脉冲（delay+animateTo 都是 suspend）")
            }
        } finally {
            // 离开组合或 key 改变都会走到这里（协程被取消）。
            log("■ LaunchedEffect 取消（离开组合 / key 改变）")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "方块在按 pulseRateMs 的节奏闪烁。切换频率会【重启】LaunchedEffect（旧协程取消、新协程开始），" +
                "离开本页时协程也会被自动取消——这就是「进入组合启动、离开组合取消、key 变化重启」。",
        )
        OptionChips(
            options = listOf(500L to "快 500ms", 1500L to "中 1500ms", 3000L to "慢 3000ms"),
            selected = pulseRateMs,
            onSelect = { pulseRateMs = it },
        )

        Stage(height = 180.dp) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    // alpha 每帧变化，用 graphicsLayer 的 lambda 只走绘制阶段，不触发重组。
                    .graphicsLayer { this.alpha = alpha.value }
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Readout("当前 alpha ≈ ${"%.2f".format(alpha.value)} · 已脉冲 $pulses 次")

        SectionTitle("effect 生命周期日志（最新在上）")
        LogBox(logs)

        SectionTitle("要点")
        Text(
            text = "• LaunchedEffect 是 composable，只能写在别的 composable 里；它的 block 是 `suspend CoroutineScope.() -> Unit`。\n" +
                "• 进入组合启动、离开组合取消；**key 变化**则取消旧的、重启新的（Restarting effects）。\n" +
                "• block 里用到的可变值，原则上都应作为 key 传入；若不希望它触发重启，改用 rememberUpdatedState（见示例③）。\n" +
                "• 常量 key（如 `LaunchedEffect(true)`/`(Unit)`）表示「只随调用点生命周期、不重启」——像 while(true) 一样要谨慎。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
