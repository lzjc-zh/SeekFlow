package com.deepseek.lzjc.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.provider.ProviderBalanceResult
import com.deepseek.lzjc.data.provider.ProviderConfig
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.data.worker.RefreshWorker
import com.deepseek.lzjc.ui.widget.WidgetDataCache
import com.deepseek.lzjc.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 单个供应商的展示数据 */
data class ProviderUiState(
    val config: ProviderConfig,
    val balance: String = "--",
    val currencySymbol: String = "¥",
    val errorMessage: String? = null
)

data class DashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** 全部供应商余额合计（仅同币种求和，展示以 CNY 为主） */
    val totalBalance: String = "0.00",
    val dailyCost: String = "0.00",
    val monthlyCost: String = "0.00",
    val dailyData: List<DailyUsageSummary> = emptyList(),
    val providerStates: List<ProviderUiState> = emptyList(),
    val hasAnyProvider: Boolean = false
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

    private var scheduled = false

    init {
        viewModelScope.launch {
            repository.providers.collect { providers ->
                val usable = providers.any { it.enabled && (it.apiKey.isNotBlank() || it.userToken.isNotBlank()) }
                _state.update {
                    it.copy(
                        hasAnyProvider = usable,
                        providerStates = if (it.providerStates.isEmpty()) {
                            providers.filter { p -> p.enabled }.map { p -> ProviderUiState(config = p) }
                        } else {
                            // 保留已刷新的余额数据，只同步配置变化
                            mergeProviderStates(it.providerStates, providers.filter { p -> p.enabled })
                        }
                    )
                }
                if (usable) {
                    yield()
                    refresh()
                    schedulePeriodicRefresh()
                } else {
                    _state.update { it.copy(isLoading = false, isRefreshing = false) }
                }
            }
        }
    }

    private fun mergeProviderStates(
        old: List<ProviderUiState>,
        configs: List<ProviderConfig>
    ): List<ProviderUiState> {
        return configs.map { config ->
            old.firstOrNull { it.config.id == config.id }?.copy(config = config)
                ?: ProviderUiState(config = config)
        }
    }

    private fun schedulePeriodicRefresh() {
        if (scheduled) return
        scheduled = true
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
                val results = repository.refreshAllProviders()

                // 更新每个供应商的余额展示
                val providerStates = _state.value.providerStates.map { ps ->
                    val result = results[ps.config.id]
                    when {
                        result == null -> ps
                        result.isSuccess -> {
                            val balance = result.getOrNull()!!
                            ps.copy(
                                balance = balance.totalBalance,
                                currencySymbol = currencySymbol(balance.currency),
                                errorMessage = null
                            )
                        }
                        else -> ps.copy(errorMessage = result.exceptionOrNull()?.message)
                    }
                }

                // 汇总余额（仅数值求和；不同币种时以列表为准展示明细）
                val sum = providerStates.sumOf {
                    it.balance.toDoubleOrNull() ?: 0.0
                }
                val failures = providerStates.count { it.errorMessage != null }

                _state.update {
                    it.copy(
                        totalBalance = String.format("%.2f", sum),
                        providerStates = providerStates,
                        errorMessage = if (failures > 0 && failures == providerStates.size) {
                            providerStates.firstNotNullOfOrNull { s -> s.errorMessage }
                        } else {
                            null
                        }
                    )
                }

                // 聚合消耗数据（全部供应商）
                val today = dateFormat.format(System.currentTimeMillis())
                val month = monthFormat.format(System.currentTimeMillis())
                val dailyCost = repository.getDailyCostAll(today)
                val monthlyCost = repository.getMonthlyCostAll(month)

                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val fromDate = dateFormat.format(cal.time)
                val data = repository.getDailyUsageSinceAll(fromDate).first()

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        dailyCost = String.format("%.2f", dailyCost),
                        monthlyCost = String.format("%.2f", monthlyCost),
                        dailyData = data
                    )
                }

                val currentState = _state.value
                widgetDataCache.saveBalanceData(
                    totalBalance = currentState.totalBalance,
                    dailyCost = currentState.dailyCost,
                    monthlyCost = currentState.monthlyCost
                )

                // 阈值提醒（针对合计余额）
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

    private fun currencySymbol(currency: String): String =
        if (currency == "USD") "$" else "¥"
}
