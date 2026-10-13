package com.example.composeup.workmanager

import android.text.format.DateFormat
import android.util.Log
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
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * 示例①：一次性任务（OneTimeWorkRequest）。
 *
 * 演示 WorkManager 最基础的用法：
 * 1. 定义 Worker（UploadWorker）—— doWork() 在后台线程执行；
 * 2. 构建 OneTimeWorkRequest —— 设置输入数据、约束条件、退避策略；
 * 3. 通过 enqueue() 提交给系统；
 * 4. 用 getWorkInfoByIdFlow() 观察任务状态变化。
 *
 * 核心 API：
 * - [OneTimeWorkRequestBuilder]：构建一次性请求的 DSL 入口；
 * - [Constraints.Builder]：声明运行条件（网络、充电、存储等）；
 * - [Data.Builder]：向 Worker 传递键值对输入参数；
 * - [WorkManager.enqueue]：将请求加入调度队列；
 * - [WorkManager.getWorkInfoByIdFlow]：返回 Flow<WorkInfo?>，实时反映任务生命周期。
 */
private const val TAG = "OneTimeWorkDemo"
@Composable
fun OneTimeWorkDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
    var simulateFailure by remember { mutableStateOf(false) }

    val workInfo by remember(currentWorkId) {
        currentWorkId?.let { id ->
            workManager.getWorkInfoByIdFlow(id).map { info ->
                if (info != null) formatWorkInfo(info) else "任务信息为空"
            }
        } ?: kotlinx.coroutines.flow.flowOf("尚未提交任务")
    }.collectAsStateWithLifecycle(initialValue = "尚未提交任务")

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
            "点「提交上传任务」创建一个 OneTimeWorkRequest 并 enqueue。\n" +
                "WorkManager 会在满足约束后调度执行，全程通过 WorkInfo 观察状态流转：\n" +
                "ENQUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED。",
        )

        SectionTitle("约束与输入")
        OptionChips(
            options = listOf(false to "正常上传", true to "模拟失败"),
            selected = simulateFailure,
            onSelect = { simulateFailure = it },
        )

        ButtonRow {
            DemoButton(text = "提交上传任务") {
                val inputData = Data.Builder()
                    .putString(UploadWorker.KEY_IMAGE_URI, "content://photos/img_${System.currentTimeMillis()}")
                    .putBoolean(UploadWorker.KEY_SIMULATE_FAILURE, simulateFailure)
                    .build()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("upload")
                    .build()

                workManager.enqueue(request)
                currentWorkId = request.id
                log("✓ 已 enqueue，id=${request.id}")
                log("  约束：需要网络连接")
                log("  输入：image_uri=content://photos/img_...")
            }

            DemoButton(
                text = "取消当前任务",
                enabled = currentWorkId != null,
            ) {
                currentWorkId?.let { id ->
                    workManager.cancelWorkById(id)
                    log("⏹ 已 cancelWorkById($id)")
                }
            }
        }

        Readout(workInfo)

        SectionTitle("运行日志（最新在上）")
        LogBox(logs)

        SectionTitle("核心 API 速览")
        CodeBlock(
            """
            // 1. 定义 Worker：doWork() 在后台线程执行
            class UploadWorker(ctx, params) : CoroutineWorker(ctx, params) {
                override suspend fun doWork(): Result {
                    val uri = inputData.getString("image_uri")
                    // ... 执行耗时操作 ...
                    return Result.success(outputData)
                }
            }

            // 2. 构建请求：输入数据 + 约束 + 标签
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(Data.Builder().putString("key", "value").build())
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .addTag("upload")
                .build()

            // 3. 提交 & 观察
            WorkManager.getInstance(ctx).enqueue(request)
            workManager.getWorkInfoByIdFlow(request.id)
                .collect { info -> /* ENQUEUED/RUNNING/SUCCEEDED/FAILED */ }
            """.trimIndent(),
        )

        SectionTitle("Result 三种返回值")
        Text(
            text = "• Result.success(data) — 任务成功完成，可携带输出数据\n" +
                "• Result.failure(data) — 任务失败，不再重试\n" +
                "• Result.retry() — 任务暂时失败，按退避策略重新调度",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        SectionTitle("WorkInfo.State 生命周期")
        CodeBlock(
            """
            ENQUEUED ──→ RUNNING ──→ SUCCEEDED
               │              │
               │              └──→ FAILED
               │
               └──────────────→ CANCELLED
            """.trimIndent(),
        )
    }
}

private fun formatWorkInfo(info: WorkInfo): String {
    val state = info.state.name
    val progress = info.progress.getInt(UploadWorker.KEY_PROGRESS, -1)
    val outputUri = info.outputData.getString(UploadWorker.KEY_RESULT_URI)
    val errorMsg = info.outputData.getString(UploadWorker.KEY_ERROR_MSG)
    Log.i(TAG, "formatWorkInfo: progress=$progress, outputUri=$outputUri, errorMsg=$errorMsg")

    return buildString {
        append("状态：$state")
        if (progress >= 0) append(" · 进度：$progress%")
        if (outputUri != null) append(" · 结果：$outputUri")
        if (errorMsg != null) append(" · 错误：$errorMsg")
    }
}
