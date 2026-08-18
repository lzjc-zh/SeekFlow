package com.deepseek.lzjc.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepseek.lzjc.data.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: UsageRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val results = repository.refreshAllProviders()
            when {
                // 未配置供应商时直接成功，避免无意义的重试
                results.isEmpty() -> Result.success()
                results.values.any { it.isSuccess } -> Result.success()
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
