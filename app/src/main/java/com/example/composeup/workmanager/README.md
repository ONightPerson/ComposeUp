# WorkManager · 持久化后台任务（一次性 / 周期性 / 更新任务）

本目录用 **3 个可交互示例**，把 WorkManager 的核心用法串成一条线讲清楚：
从最简单的一次性任务出发，逐步加上周期调度和运行时更新约束。
它不是孤立的 API 罗列，而是「一个后台任务功能如何一步步长出来」。

WorkManager 是 Android 官方推荐的**持久化后台任务调度器**：
即使 App 被杀、设备重启，已提交的任务仍会在满足约束时被系统重新调度执行。
它替代了历史上 AlarmManager / BroadcastReceiver / Firebase JobDispatcher 等分散方案，
提供统一 API 来处理一次性任务、周期性任务、任务链和运行时更新。

依赖版本（本项目实际使用）：

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| `androidx.work:work-runtime-ktx` | `2.12.0` | WorkManager + CoroutineWorker 支持 |
| Kotlin | `2.4.20` | 协程 / suspend 支持 |
| Compose BOM | `2026.09.00` | UI 层 |
| `lifecycle-runtime-compose` | `2.11.0` | `collectAsStateWithLifecycle` 观察 WorkInfo |

---

## 一、设计思路

### 1.1 为什么选 WorkManager？

Android 后台任务有多种方案，选型取决于「是否需要保证执行」：

| 需求 | 推荐方案 | 原因 |
| --- | --- | --- |
| 必须执行（上传/同步/清理） | **WorkManager** | 持久化、自动重试、约束感知 |
| 精确定时（闹钟/提醒） | AlarmManager | WorkManager 不保证精确时间 |
| 用户可见的持续任务（音乐/导航） | Foreground Service | WorkManager 是后台不可见的 |
| 即时响应推送 | FCM + WorkManager | FCM 触发 → WorkManager 执行 |

一句话记忆：

> **WorkManager = 「保证会跑」+ 「条件满足才跑」+ 「App 死了也能跑」**

### 1.2 核心概念三件套

```
Worker（做什么） → WorkRequest（怎么做） → WorkManager（何时做）
```

- **Worker / CoroutineWorker**：定义具体的工作逻辑，`doWork()` 在后台线程执行；
- **WorkRequest**（OneTime / Periodic）：描述「何时、在什么条件下」执行，携带输入数据和约束；
- **WorkManager**：系统级调度器，负责排队、约束检查、重试、持久化。

### 1.3 三个示例的递进关系

```
① 一次性任务：学会 Worker + Request + enqueue + 观察状态
       ↓
② 周期性任务：加上 repeatInterval + 唯一名称 + 冲突策略
       ↓
③ 更新任务：学会 updateWork() 原地修改，避免 cancel + enqueue 的坑
```

每个示例都解决一个真实问题：
- ① 「用户上传一张照片」→ 后台执行、带进度、有成功/失败反馈；
- ② 「每天同步一次数据」→ 周期调度、不重复创建、可切换策略；
- ③ 「用户改了设置，约束要跟着变」→ 不丢中间状态地更新约束。

---

## 二、怎么跑起来

不需要任何额外配置，直接运行 App：

```
MainActivity
  └─ ComposeUpTheme
       └─ ComposeUpRoot            ← 主页面 = Topic 列表
            ├─ Home          ComposeUpApp()
            ├─ NestedScroll  NestedScrollHub()
            ├─ Animation     AnimationHub()
            ├─ Keywords      KeywordsHub()
            ├─ Flow          FlowHub()
            ├─ Architecture  ArchitectureHub()
            ├─ SideEffects   SideEffectsHub()
            ├─ Settings      DatastoreHub()
            ├─ ScreenRecord  ScreenRecordHub()
            ├─ Remember      RememberHub()
            ├─ DataStore     DatastoreHub()
            ├─ Local         LocalHub()
            ├─ Navigation3   Nav3Activity
            └─ WorkManager   WorkManagerHub()        ← 本模块（Icons.Filled.Build）
```

主页面点击 **WorkManager** 进入 `WorkManagerHub`，再点击列表里任意一项进入具体示例；
用系统返回键或左上角箭头逐级退回。

> WorkManager 的任务状态变化（ENQUEUED → RUNNING → SUCCEEDED）发生在后台，
> 静态 UI 表达不了。因此本模块统一用 `LogBox` 把每一次状态变更**按时间顺序打印**到屏幕上，
> 让「看不见的调度」变得可见；每个示例都可交互重跑。

---

## 三、文件结构与职责

```
workmanager/
├── WorkManagerHub.kt       入口：话题列表 + 二级页面切换（含 BackHandler）
├── Workers.kt              Worker 实现：UploadWorker（一次性）+ DailySyncWorker（周期性）
├── OneTimeWorkDemo.kt      示例①：OneTimeWorkRequest + Constraints + Data + 状态观察
├── PeriodicWorkDemo.kt     示例②：PeriodicWorkRequest + 唯一名称 + ExistingPeriodicWorkPolicy
├── UpdateWorkDemo.kt       示例③：updateWork() + setId + generation 追踪
├── WorkManagerWidgets.kt   包内复用小组件（CodeBlock / LogBox / Stage / ButtonRow / OptionChips / Readout …）
└── README.md               本文件
```

---

## 四、逐个示例的实现方式

### 示例① 一次性任务：Worker + OneTimeWorkRequest + 状态观察

**真实案例**：用户上传一张照片到服务器。

**实现方式**：`UploadWorker` 继承 `CoroutineWorker`，`doWork()` 中模拟 3 秒上传过程，
每秒通过 `setProgress()` 报告进度，最终返回 `Result.success(outputData)` 或 `Result.failure()`。
UI 层用 `OneTimeWorkRequestBuilder` 构建请求，设置输入数据（图片 URI）和约束（需要网络），
`enqueue()` 后用 `getWorkInfoByIdFlow()` 实时观察状态。

```kotlin
// Worker 定义
class UploadWorker(ctx, params) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_IMAGE_URI) ?: return Result.failure()
        for (i in 1..3) {
            delay(1000)
            setProgress(Data.Builder().putInt(KEY_PROGRESS, i * 33).build())
        }
        return Result.success(Data.Builder().putString(KEY_RESULT_URI, "$uri/uploaded").build())
    }
}

// 构建 & 提交
val request = OneTimeWorkRequestBuilder<UploadWorker>()
    .setInputData(Data.Builder().putString(KEY_IMAGE_URI, "content://...").build())
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .addTag("upload")
    .build()
WorkManager.getInstance(ctx).enqueue(request)

// 观察状态
workManager.getWorkInfoByIdFlow(request.id).collect { info ->
    // info.state: ENQUEUED / RUNNING / SUCCEEDED / FAILED / CANCELLED
    // info.progress: 中间进度
    // info.outputData: 最终输出
}
```

**关键 API 说明**：

| API | 作用 |
| --- | --- |
| `CoroutineWorker` | Worker 的协程版本，`doWork()` 是 suspend 函数，可直接调用协程 API |
| `doWork(): Result` | 在后台线程执行的工作逻辑，必须返回 success/failure/retry |
| `inputData` / `outputData` | 键值对数据传递，类型安全（String/Int/Long/Boolean/Float/Double/ByteArray） |
| `setProgress(data)` | 报告中间进度，UI 通过 `WorkInfo.progress` 读取 |
| `Constraints.Builder` | 声明运行条件：网络类型、充电、存储空间、电池电量、设备空闲 |
| `OneTimeWorkRequestBuilder<W>()` | DSL 构建一次性请求，可链式设置 input/constraints/tags/backoff |
| `enqueue(request)` | 将请求加入 WorkManager 调度队列 |
| `getWorkInfoByIdFlow(id)` | 返回 `Flow<WorkInfo?>`，实时反映任务生命周期 |
| `cancelWorkById(id)` | 取消指定任务 |

> **要点**：`Result.retry()` 会按退避策略（默认指数退避）重新调度；
> `setProgress()` 的数据大小限制为 10KB；`WorkInfo.State` 是单向流转，不会回退。

### 示例② 周期性任务：PeriodicWorkRequest + 唯一名称 + 冲突策略

**真实案例**：App 每天自动同步用户数据到云端。

**实现方式**：`DailySyncWorker` 每次执行时递增同步计数并记录时间戳。
UI 层用 `PeriodicWorkRequestBuilder` 设定 15 分钟间隔（WorkManager 最短周期），
以唯一名称 `"daily_sync"` 提交，通过 `OptionChips` 切换 KEEP / REPLACE 策略观察行为差异。

```kotlin
// 构建周期请求
val request = PeriodicWorkRequestBuilder<DailySyncWorker>(
    repeatInterval = 15,
    repeatIntervalTimeUnit = TimeUnit.MINUTES,
)
    .setConstraints(constraints)
    .build()

// 以唯一名称提交
workManager.enqueueUniquePeriodicWork(
    "daily_sync",
    ExistingPeriodicWorkPolicy.KEEP,  // 已有同名则保留
    request,
)

// 观察
workManager.getWorkInfosForUniqueWorkFlow("daily_sync").collect { infos -> ... }
```

**ExistingPeriodicWorkPolicy 对比**：

| 策略 | 行为 | 适用场景 |
| --- | --- | --- |
| `KEEP` | 已有同名任务 → 保留旧的，忽略新请求 | 首次初始化、防止重复创建 |
| `REPLACE` | 取消旧任务，用新请求替代（重置计时器） | 参数变更需要立即生效 |
| `UPDATE` | 保留执行记录，仅更新约束/输入（不重置计时器） | 微调约束，不打断节奏 |

> **要点**：WorkManager 周期任务最短间隔 15 分钟；更短的值会被自动拉长。
> 周期任务适合「定期同步 / 清理缓存 / 上报统计」等不需要精确定时的场景。

### 示例③ 更新任务：updateWork() 原地修改约束

**真实案例**：用户在设置页关闭了「仅 Wi-Fi 上传」，需要动态放宽网络约束。

**实现方式**：先创建一个带「需要充电」约束的任务，然后用 `updateWork()` 去掉该约束。
全程不 cancel、不重建，任务的执行记录无缝衔接。通过 `generation` 追踪更新次数。

```kotlin
// Step 1: 获取已有任务 ID
val existingId = workManager
    .getWorkInfosForUniqueWork("my_work").await()
    .firstOrNull()?.id ?: return

// Step 2: 构建新请求，setId 为旧 ID
val updated = OneTimeWorkRequestBuilder<MyWorker>()
    .setConstraints(newConstraints)
    .setId(existingId)           // ← 关键：匹配更新目标
    .build()

// Step 3: 调用 updateWork
workManager.updateWork(updated)

// Step 4: 通过 generation 追踪
val gen = workManager.getWorkInfoById(existingId).getGeneration()
```

**updateWork vs cancel + enqueue**：

| | updateWork | cancel + enqueue |
| --- | --- | --- |
| 原子性 | ✅ 原子操作 | ❌ 两步，有竞态窗口 |
| 执行记录 | 保留 | 丢失 |
| 中间状态 | 不中断 | 可能丢失正在计算的结果 |
| 周期任务计时 | 不重置 | 需手动对齐偏移 |
| 换 Worker 类型 | ❌ 不支持 | ✅ 支持 |

> **要点**：`setId()` 是 updateWork 的前提；`generation` 从 0 开始，每次成功更新 +1；
> updateWork 返回 `ListenableFuture<UpdateResult>`，可异步检查结果。

---

## 五、API 速查表

```
定义工作：
  Worker / CoroutineWorker          后台执行单元
  doWork(): Result                  success / failure / retry
  inputData / outputData            键值对数据传递
  setProgress(data)                 报告中间进度

构建请求：
  OneTimeWorkRequestBuilder<W>()    一次性任务
  PeriodicWorkRequestBuilder<W>()   周期性任务（≥15min）
  .setInputData(data)               传入参数
  .setConstraints(constraints)      运行条件
  .addTag(tag)                      分组标签
  .setBackoffCriteria(...)          重试退避策略
  .setInitialDelay(duration)        首次延迟
  .setId(uuid)                      指定 ID（用于 updateWork）

约束条件：
  Constraints.Builder()
    .setRequiredNetworkType(...)    NOT_REQUIRED / CONNECTED / UNMETERED / METERED / TEMPORARILY_UNMETERED
    .setRequiresCharging(bool)      需要充电
    .setRequiresBatteryNotLow(bool) 电量不低于阈值
    .setRequiresStorageNotLow(bool) 存储不低于阈值
    .setRequiresDeviceIdle(bool)    设备空闲

提交 & 管理：
  enqueue(request)                  提交普通请求
  enqueueUniqueWork(name, policy, request)    唯一一次性任务
  enqueueUniquePeriodicWork(name, policy, request)  唯一周期任务
  updateWork(request)               原地更新已有任务
  cancelWorkById(id)                取消指定任务
  cancelUniqueWork(name)            取消唯一任务
  cancelAllWorkByTag(tag)           按标签批量取消
  pruneWork()                       清理已完成任务

观察状态：
  getWorkInfoByIdFlow(id)           Flow<WorkInfo?>
  getWorkInfosForUniqueWorkFlow(name)  Flow<List<WorkInfo>>
  getWorkInfosByTagFlow(tag)        Flow<List<WorkInfo>>
  WorkInfo.state                    ENQUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED/BLOCKED
  WorkInfo.progress                 中间进度 Data
  WorkInfo.outputData               最终输出 Data
  WorkInfo.generation               更新代数（updateWork 后 +1）
```

---

## 六、实践清单

**必须做**

- [ ] 后台任务优先用 WorkManager，不要用裸 Thread / Timer / Handler.postDelayed。
- [ ] 用 `CoroutineWorker` 而非 `Worker`，`doWork()` 是 suspend 函数，可直接调协程 API。
- [ ] 通过 `inputData` / `outputData` 传参，不要持有外部引用（Worker 可能被序列化重建）。
- [ ] 用 `getWorkInfoByIdFlow()` 观察状态，不要在 Activity/Fragment 里轮询。
- [ ] 周期任务用唯一名称 + `enqueueUniquePeriodicWork`，避免重复创建。
- [ ] 需要更新约束时用 `updateWork()`，不要 cancel + enqueue。
- [ ] 大数据（>10KB）不要放 inputData/outputData，改用 Room / DataStore 中转。

**必须避免**

- [ ] 在 `doWork()` 里访问 UI 或 Activity 引用（Worker 生命周期独立于 UI）。
- [ ] 假设 WorkManager 会精确按时执行（它是「尽力而为」，不是定时器）。
- [ ] 用 `Result.retry()` 处理永久性错误（应返回 `Result.failure()`）。
- [ ] 在周期任务里做大量一次性初始化（每次执行都会跑）。
- [ ] 忘记处理 `doWork()` 被取消的情况（检查 `isStopped` 或用协程的 `ensureActive()`）。
- [ ] 把敏感信息明文放进 inputData（它存储在 SQLite 中）。

---

## 七、延伸阅读

- [WorkManager 官方文档 - Getting Started](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)
- [Define your work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/define-work)
- [Update work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/update-work)
- [Chain work together](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/chain-work)
- [WorkManager Codelab (Kotlin)](https://developer.android.com/codelabs/android-workmanager)
- 本项目 `flow/` 模块 —— Flow 是观察 WorkInfo 状态的基础，可与本模块对照理解。
