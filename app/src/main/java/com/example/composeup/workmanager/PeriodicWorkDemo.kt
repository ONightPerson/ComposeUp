package com.example.composeup.workmanager

import android.text.format.DateFormat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * 示例②：周期性任务（PeriodicWorkRequest）。
 *
 * 演示 WorkManager 的周期调度能力：
 * 1. 用 PeriodicWorkRequestBuilder 设定重复间隔（最短 15 分钟）；
 * 2. 通过 ExistingPeriodicWorkPolicy 控制「已存在同名任务」时的行为；
 * 3. 用 getWorkInfosForUniqueWorkFlow() 观察周期任务的最新执行状态。
 *
 * 核心 API：
 * - [PeriodicWorkRequestBuilder]：构建周期请求，指定 repeatInterval；
 * - [ExistingPeriodicWorkPolicy]：KEEP / REPLACE / UPDATE 三种冲突策略；
 * - [WorkManager.enqueueUniquePeriodicWork]：以唯一名称提交周期任务；
 * - [WorkManager.getWorkInfosForUniqueWorkFlow]：按唯一名称获取任务信息流。
 */
@Composable
fun PeriodicWorkDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    val uniqueName = "daily_sync"
    val workInfos by workManager.getWorkInfosForUniqueWorkFlow(uniqueName)
        .collectAsStateWithLifecycle(initialValue = emptyList<WorkInfo>())

    fun log(line: String) {
        val time = DateFormat.format("HH:mm:ss", Date()).toString()
        logs = (listOf("[$time] $line") + logs).take(60)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Note(
            "点「启动每日同步」创建一个 PeriodicWorkRequest（间隔 15 分钟，WorkManager 最短周期）。\n" +
                "系统会在满足约束时反复执行，即使 App 被杀也会在下次满足条件时恢复。\n" +
                "注意：模拟器上周期任务可能不会精确按时触发，这是系统优化行为。",
        )

        SectionTitle("当前周期任务状态")
        if (workInfos.isEmpty()) {
            Readout("尚无周期任务")
        } else {
            workInfos.forEach { info ->
                Readout(formatPeriodicInfo(info))
            }
        }

        ButtonRow {
            DemoButton(text = "启动每日同步") {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<DailySyncWorker>(
                    repeatInterval = 15,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .addTag("daily_sync")
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    uniqueName,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
                log("✓ 已 enqueueUniquePeriodicWork(KEEP)")
                log("  间隔：15 分钟（WorkManager 最短周期）")
                log("  策略：KEEP — 已有同名任务则保留旧的")
            }

            DemoButton(text = "替换为新任务") {
                val request = PeriodicWorkRequestBuilder<DailySyncWorker>(
                    repeatInterval = 15,
                    repeatIntervalTimeUnit = TimeUnit.MINUTES,
                )
                    .addTag("daily_sync")
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    uniqueName,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request,
                )
                log("✓ 已 enqueueUniquePeriodicWork(REPLACE)")
                log("  策略：REPLACE — 取消旧任务，用新的替代")
            }

            DemoButton(text = "取消周期任务") {
                workManager.cancelUniqueWork(uniqueName)
                log("⏹ 已 cancelUniqueWork($uniqueName)")
            }
        }

        SectionTitle("运行日志（最新在上）")
        LogBox(logs)

        SectionTitle("核心 API 速览")
        CodeBlock(
            """
            // 构建周期请求：最短 15 分钟
            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            // 以唯一名称提交（避免重复创建）
            workManager.enqueueUniquePeriodicWork(
                "daily_sync",
                ExistingPeriodicWorkPolicy.KEEP,   // 已有则保留
                request,
            )

            // 观察周期任务状态
            workManager.getWorkInfosForUniqueWorkFlow("daily_sync")
                .collect { infos -> /* 每次执行产生新 WorkInfo */ }
            """.trimIndent(),
        )

        SectionTitle("ExistingPeriodicWorkPolicy 对比")
        Text(
            text = "• KEEP — 如果已有同名周期任务在运行，保留它、忽略新请求\n" +
                "• REPLACE — 取消旧任务，用新请求替代（重置计时器）\n" +
                "• UPDATE — 保留旧任务的执行记录，仅更新约束/输入（不重置计时器）",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        SectionTitle("要点")
        Text(
            text = "• WorkManager 周期任务的最短间隔是 15 分钟，更短的会被自动拉长。\n" +
                "• 周期任务适合「定期同步 / 清理缓存 / 上报统计」等不需要精确定时的场景。\n" +
                "• 需要精确定时请用 AlarmManager；需要前台可见请用 Foreground Service。\n" +
                "• 任务在 App 被卸载或用户清除数据后会自动取消。",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun formatPeriodicInfo(info: WorkInfo): String {
    val state = info.state.name
    val syncCount = info.outputData.getInt(DailySyncWorker.KEY_SYNC_COUNT, 0)
    val lastSync = info.outputData.getLong(DailySyncWorker.KEY_LAST_SYNC_TIME, 0L)
    val lastSyncStr = if (lastSync > 0) DateFormat.format("HH:mm:ss", Date(lastSync)).toString() else "-"

    return buildString {
        append("状态：$state")
        if (syncCount > 0) append(" · 已同步 $syncCount 次")
        append(" · 上次：$lastSyncStr")
        append(" · id=${info.id.toString().take(8)}…")
    }
}
