package com.example.composeup.workmanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * 模拟「上传图片」的一次性任务。
 *
 * 从 inputData 读取 "image_uri"，假装上传耗时 3 秒，然后把结果写进 outputData。
 * 演示 Worker 如何接收输入、返回输出、以及通过 setProgress 报告中间进度。
 */
class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val imageUri = inputData.getString(KEY_IMAGE_URI) ?: "unknown"
        val simulateFailure = inputData.getBoolean(KEY_SIMULATE_FAILURE, false)

        for (i in 1..3) {
            delay(1000)
            setProgress(
                Data.Builder()
                    .putInt(KEY_PROGRESS, i * 33)
                    .build(),
            )
        }

        if (simulateFailure) {
            return Result.failure(
                Data.Builder().putString(KEY_ERROR_MSG, "模拟上传失败").build(),
            )
        }

        return Result.success(
            Data.Builder()
                .putString(KEY_RESULT_URI, "$imageUri/uploaded")
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .build(),
        )
    }

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_SIMULATE_FAILURE = "simulate_failure"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_ERROR_MSG = "error_msg"
    }
}

/**
 * 模拟「每日数据同步」的周期性任务。
 *
 * 每次执行时把当前时间戳写入 outputData，演示 PeriodicWorkRequest 的重复调度。
 */
class DailySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        delay(1500)
        val syncCount = inputData.getInt(KEY_SYNC_COUNT, 0) + 1
        return Result.success(
            Data.Builder()
                .putInt(KEY_SYNC_COUNT, syncCount)
                .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                .build(),
        )
    }

    companion object {
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }
}
