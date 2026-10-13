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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import java.util.Date

/**
 * 示例③：更新已有任务（updateWork）。
 *
 * 演示 WorkManager 2.10+ 新增的 updateWork() API：
 * 1. 先提交一个带「需要充电」约束的任务；
 * 2. 用 updateWork() 在不取消/重建的前提下修改约束；
 * 3. 通过 generation 追踪任务被更新的次数。
 *
 * 核心 API：
 * - [WorkManager.updateWork]：原地更新已有 WorkRequest 的约束/输入，无需 cancel + enqueue；
 * - [WorkRequest.Builder.setId]：新请求必须携带旧请求的 ID 才能匹配更新目标；
 * - [WorkInfo.getGeneration]：每次 updateWork 成功后 generation +1，用于追踪版本。
 *
 * 为什么不用 cancel + enqueue？
 * - cancel 会丢失正在计算的中间结果（如已构建一半的请求体）；
 * - 周期任务 cancel 后重新 enqueue 需要手动对齐时间偏移；
 * - updateWork 是原子操作，避免竞态条件。
 */
@Composable
fun UpdateWorkDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }

    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
    var requireCharging by remember { mutableStateOf(true) }

    val workInfo by remember(currentWorkId) {
        currentWorkId?.let { id ->
            workManager.getWorkInfoByIdFlow(id)
        } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

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
            "先点「创建任务（需充电）」提交一个带充电约束的任务，\n" +
                "再点「更新：去掉充电约束」用 updateWork() 原地修改约束。\n" +
                "全程不 cancel、不重建，任务的执行记录和执行时机无缝衔接。",
        )

        SectionTitle("当前任务状态")
        val info = workInfo
        if (info != null) {
            Readout(formatUpdateInfo(info))
        } else {
            Readout("尚未创建任务")
        }

        ButtonRow {
            DemoButton(text = "创建任务（需充电）") {
                requireCharging = true
                val constraints = Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString(UploadWorker.KEY_IMAGE_URI, "photo_backup")
                            .build(),
                    )
                    .setConstraints(constraints)
                    .addTag("updatable")
                    .build()

                workManager.enqueue(request)
                currentWorkId = request.id
                log("✓ 已 enqueue，约束：需要充电 + 网络")
                log("  id=${request.id}")
            }

            DemoButton(
                text = "更新：去掉充电约束",
                enabled = currentWorkId != null,
            ) {
                val id = currentWorkId ?: return@DemoButton
                requireCharging = false
                val newConstraints = Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val updatedRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString(UploadWorker.KEY_IMAGE_URI, "photo_backup")
                            .build(),
                    )
                    .setConstraints(newConstraints)
                    .setId(id)
                    .build()

                workManager.updateWork(updatedRequest)
                log("✓ 已 updateWork：去掉充电约束")
                log("  使用 setId($id) 匹配原任务")
            }

            DemoButton(
                text = "更新：恢复充电约束",
                enabled = currentWorkId != null,
            ) {
                val id = currentWorkId ?: return@DemoButton
                requireCharging = true
                val newConstraints = Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val updatedRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString(UploadWorker.KEY_IMAGE_URI, "photo_backup")
                            .build(),
                    )
                    .setConstraints(newConstraints)
                    .setId(id)
                    .build()

                workManager.updateWork(updatedRequest)
                log("✓ 已 updateWork：恢复充电约束")
            }

            DemoButton(
                text = "取消任务",
                enabled = currentWorkId != null,
            ) {
                currentWorkId?.let { id ->
                    workManager.cancelWorkById(id)
                    log("⏹ 已 cancelWorkById($id)")
                }
            }
        }

        SectionTitle("运行日志（最新在上）")
        LogBox(logs)

        SectionTitle("updateWork 四步流程")
        CodeBlock(
            """
            // Step 1: 获取已有任务的 ID
            val existingId = workManager
                .getWorkInfosForUniqueWork("my_work").await()
                .firstOrNull()?.id ?: return

            // Step 2: 构建新 WorkRequest，必须 setId 为旧 ID
            val updated = OneTimeWorkRequestBuilder<MyWorker>()
                .setConstraints(newConstraints)
                .setId(existingId)           // ← 关键：匹配更新目标
                .build()

            // Step 3: 调用 updateWork
            workManager.updateWork(updated)

            // Step 4: 通过 generation 追踪更新次数
            val gen = workManager.getWorkInfoById(existingId)
                .getGeneration()   // 每次 updateWork 成功 +1
            """.trimIndent(),
        )

        SectionTitle("updateWork vs cancel + enqueue")
        Text(
            text = "• updateWork：原子操作，保留执行记录，不中断正在进行的计算\n" +
                "• cancel + enqueue：两步操作，有竞态窗口，丢失中间状态\n" +
                "• updateWork 不能改变 Worker 类型（OneTime ↔ Periodic），只能改约束/输入\n" +
                "• 如果确实要换 Worker 类型，仍需 cancel + enqueue",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        SectionTitle("要点")
        Text(
            text = "• setId() 是 updateWork 的前提——没有它，WorkManager 无法知道要更新哪个任务。\n" +
                "• generation 从 0 开始，每次成功 updateWork 后递增，可用于测试和日志追踪。\n" +
                "• updateWork 返回 ListenableFuture<UpdateResult>，可异步检查是否成功。\n" +
                "• 适用场景：用户切换设置后动态调整约束（如关闭 Wi-Fi Only）。",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private fun formatUpdateInfo(info: WorkInfo): String {
    val state = info.state.name
    val generation = info.generation
    return buildString {
        append("状态：$state · generation=$generation")
        append(" · id=${info.id.toString().take(8)}…")
    }
}
