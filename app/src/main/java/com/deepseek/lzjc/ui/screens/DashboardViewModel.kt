package com.deepseek.lzjc.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.data.worker.RefreshWorker
import com.deepseek.lzjc.ui.widget.WidgetDataCache
import com.deepseek.lzjc.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val totalBalance: String = "0.00",
    val grantedBalance: String = "0.00",
    val toppedUpBalance: String = "0.00",
    val dailyCost: String = "0.00",
    val monthlyCost: String = "0.00",
    val flashTokens: Long = 0,
    val proTokens: Long = 0,
    val dailyData: List<DailyUsageSummary> = emptyList(),
    val hasApiKey: Boolean = false,
    val hasUserToken: Boolean = false,
    // v2: 请求次数
    val dailyRequests: Long = 0,
    val monthlyRequests: Long = 0,
    // v3: 每日每模型明细（柱状图点触弹窗）
    val modelBreakdowns: List<DailyModelBreakdown> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val repository: UsageRepository,
    private val widgetDataCache: WidgetDataCache
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    init {
        viewModelScope.launch {
            combine(repository.apiKey, repository.userToken) { key, token -> key to token }
                .distinctUntilChanged()
                .collect { (key, token) ->
                    _state.update {
                        it.copy(hasApiKey = key.isNotBlank(), hasUserToken = token.isNotBlank())
                    }
                    if (key.isNotBlank()) {
                        yield() // 让初始渲染先完成
                        refresh()
                        schedulePeriodicRefresh()
                    } else {
                        _state.update { it.copy(isLoading = false, isRefreshing = false) }
                    }
                }
        }
    }

    private fun schedulePeriodicRefresh() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                "balance_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                var totalBalance = _state.value.totalBalance
                var grantedBalance = _state.value.grantedBalance
                var toppedUpBalance = _state.value.toppedUpBalance
                var errorMsg: String? = null

                repository.refreshAndRecord()
                    .onSuccess { response ->
                        val info = response.balanceInfos.firstOrNull()
                        totalBalance = info?.totalBalance ?: "0.00"
                        grantedBalance = info?.grantedBalance ?: "0.00"
                        toppedUpBalance = info?.toppedUpBalance ?: "0.00"
                    }
                    .onFailure { e ->
                        errorMsg = e.message ?: application.getString(R.string.network_error)
                    }

                val today = dateFormat.format(System.currentTimeMillis())
                val month = monthFormat.format(System.currentTimeMillis())
                val dailyCost = repository.getDailyCost(today)
                val monthlyCost = repository.getMonthlyCost(month)
                val flashTokens = repository.getMonthlyModelTokens(UsageRepository.MODEL_FLASH, month)
                val proTokens = repository.getMonthlyModelTokens(UsageRepository.MODEL_PRO, month)

                // v2: 请求次数
                val dailyRequests = repository.getDailyRequestCount(today)
                val monthlyRequests = repository.getMonthlyRequestCount(month)

                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val fromDate = dateFormat.format(cal.time)
                val data = repository.getDailyUsageSince(fromDate).first()

                // v3: 每日每模型明细
                val modelBreakdowns = repository.getDailyModelBreakdowns(7)

                // 一次性更新所有状态，避免多次触发重组
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        totalBalance = totalBalance,
                        grantedBalance = grantedBalance,
                        toppedUpBalance = toppedUpBalance,
                        errorMessage = errorMsg,
                        dailyCost = String.format("%.2f", dailyCost),
                        monthlyCost = String.format("%.2f", monthlyCost),
                        flashTokens = flashTokens,
                        proTokens = proTokens,
                        dailyData = data,
                        dailyRequests = dailyRequests,
                        monthlyRequests = monthlyRequests,
                        modelBreakdowns = modelBreakdowns
                    )
                }

                val currentState = _state.value
                widgetDataCache.saveBalanceData(
                    totalBalance = currentState.totalBalance,
                    dailyCost = currentState.dailyCost,
                    monthlyCost = currentState.monthlyCost
                )

                val thresholdStr = prefs.getString("balance_threshold", "") ?: ""
                if (thresholdStr.isNotBlank()) {
                    val threshold = thresholdStr.toFloatOrNull()
                    val balance = currentState.totalBalance.toFloatOrNull()
                    if (threshold != null && balance != null && balance < threshold) {
                        NotificationHelper.showBalanceAlert(
                            application, currentState.totalBalance, thresholdStr
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: application.getString(R.string.refresh_failed)
                    )
                }
            }
        }
    }
}
