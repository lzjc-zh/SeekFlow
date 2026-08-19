package com.deepseek.lzjc.data.worker

import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.ui.widget.BalanceWidgetLarge
import com.deepseek.lzjc.ui.widget.BalanceWidgetMedium
import com.deepseek.lzjc.ui.widget.BalanceWidgetProvider
import com.deepseek.lzjc.ui.widget.BalanceWidgetSmall
import com.deepseek.lzjc.ui.widget.WidgetDataCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: UsageRepository,
    private val widgetDataCache: WidgetDataCache
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val today = dateFormat.format(System.currentTimeMillis())
            val month = monthFormat.format(System.currentTimeMillis())

            val balanceResult = repository.refreshAndRecord()
            val totalBalance = balanceResult.getOrNull()
                ?.balanceInfos?.firstOrNull()?.totalBalance ?: "0.00"
            val dailyCost = repository.getDailyCost(today)
            val monthlyCost = repository.getMonthlyCost(month)

            widgetDataCache.saveBalanceData(
                totalBalance = totalBalance,
                dailyCost = String.format("%.2f", dailyCost),
                monthlyCost = String.format("%.2f", monthlyCost)
            )

            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val widgetConfigs = listOf(
                BalanceWidgetSmall::class.java to R.layout.widget_balance,
                BalanceWidgetMedium::class.java to R.layout.widget_balance_medium,
                BalanceWidgetLarge::class.java to R.layout.widget_balance_large
            )
            for ((cls, layoutId) in widgetConfigs) {
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(appContext, cls))
                val labels = layoutId == R.layout.widget_balance_medium
                for (id in ids) {
                    BalanceWidgetProvider.updateAppWidget(appContext, appWidgetManager, id, layoutId, labels)
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
