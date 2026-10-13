package com.example.composeup.workmanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 本包收录的「WorkManager」示例。
 *
 * WorkManager 是 Android 官方推荐的**持久化后台任务**调度器：
 * 即使 App 被杀、设备重启，已提交的任务仍会在满足约束时被系统重新调度执行。
 * 它替代了历史上 AlarmManager / BroadcastReceiver / Firebase JobDispatcher 等分散方案，
 * 提供统一的 API 来处理一次性任务、周期性任务、任务链、以及运行时更新。
 *
 * 这一组示例按「从简单到进阶」串成一条线：
 * ① 一次性任务 —— Worker + OneTimeWorkRequest + 状态观察；
 * ② 周期性任务 —— PeriodicWorkRequest + 唯一名称 + 冲突策略；
 * ③ 更新任务 —— updateWork() 原地修改约束，无需 cancel + enqueue。
 */
private enum class WorkManagerTopic(
    val title: String,
    val summary: String,
    val keywords: String,
) {
    OneTime(
        title = "① 一次性任务：Worker + OneTimeWorkRequest + 状态观察",
        summary = "最基础的用法：定义 CoroutineWorker、设置输入数据与约束、enqueue 后通过 Flow 观察状态流转。" +
            "演示 Result.success/failure/retry 三种返回值，以及 WorkInfo.State 的完整生命周期。",
        keywords = "Worker · CoroutineWorker · doWork · OneTimeWorkRequest · Constraints · Data · enqueue · WorkInfo",
    ),
    Periodic(
        title = "② 周期性任务：PeriodicWorkRequest + 唯一名称 + 冲突策略",
        summary = "用 PeriodicWorkRequestBuilder 设定重复间隔（最短 15 分钟），以唯一名称避免重复创建。" +
            "交互对比 KEEP / REPLACE 两种 ExistingPeriodicWorkPolicy 的行为差异。",
        keywords = "PeriodicWorkRequest · repeatInterval · enqueueUniquePeriodicWork · ExistingPeriodicWorkPolicy · KEEP/REPLACE/UPDATE",
    ),
    Update(
        title = "③ 更新任务：updateWork() 原地修改约束",
        summary = "WorkManager 2.10+ 新增的 updateWork() API：不 cancel、不重建，直接修改已有任务的约束或输入。" +
            "演示 setId() 匹配目标、generation 追踪更新次数，以及与 cancel+enqueue 的对比。",
        keywords = "updateWork · setId · getGeneration · UpdateResult · cancel vs update",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkManagerHub(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<WorkManagerTopic?>(null) }
    val topics = remember { WorkManagerTopic.entries.toList() }

    val current = selected
    if (current == null) {
        Surface(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "WorkManager · 持久化后台任务",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Note(
                            "WorkManager 是 Android 官方推荐的后台任务调度器，保证任务在 App 退出、\n" +
                                "设备重启后仍能执行。这一组示例串成一条线：\n" +
                                "· ① 一次性任务 —— 上传图片，观察 ENQUEUED → RUNNING → SUCCEEDED；\n" +
                                "· ② 周期性任务 —— 每日同步，对比 KEEP / REPLACE 冲突策略；\n" +
                                "· ③ 更新任务 —— updateWork() 原地改约束，不丢中间状态。\n" +
                                "下面 3 个示例逐个拆解，点击任意一项进入可交互演示。",
                        )
                    }
                }
                items(count = topics.size) { index ->
                    val topic = topics[index]
                    WorkManagerRow(
                        title = topic.title,
                        subtitle = topic.summary + "\n\n" + topic.keywords,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { selected = topic },
                    )
                }
            }
        }
        return
    }

    BackHandler { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { selected = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回话题列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val mod = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (current) {
            WorkManagerTopic.OneTime -> OneTimeWorkDemo(mod)
            WorkManagerTopic.Periodic -> PeriodicWorkDemo(mod)
            WorkManagerTopic.Update -> UpdateWorkDemo(mod)
        }
    }
}
