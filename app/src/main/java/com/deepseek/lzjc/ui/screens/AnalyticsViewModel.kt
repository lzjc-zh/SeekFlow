package com.deepseek.lzjc.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.repository.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalyticsState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val balance: Double = 0.0,
    val avgDailyCost: Double = 0.0,
    val daysRemaining: Int = 0,
    val trendData: List<DailyUsageSummary> = emptyList(),
    val modelCosts: List<ModelCostSummary> = emptyList(),
    // v2: 缓存命中率 & 请求次数
    val cacheHitRate: Double = 0.0,
    val cacheHitTokens: Long = 0,
    val cacheMissTokens: Long = 0,
    val cacheEstimatedSaved: Double = 0.0,
    val dailyRequests: Long = 0,
    val monthlyRequests: Long = 0
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: UsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    private var hasLoaded = false

    fun refreshIfNotLoaded() {
        if (!hasLoaded) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (hasLoaded) {
                _state.update { it.copy(isRefreshing = true) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                // 获取当前余额
                val balanceStr = repository.apiKey.first()
                var balance = 0.0
                if (balanceStr.isNotBlank()) {
                    repository.fetchBalance().onSuccess { resp ->
                        balance = resp.balanceInfos.firstOrNull()?.totalBalance?.toDoubleOrNull() ?: 0.0
                    }
                }

                // 获取分析数据
                val avgDailyCost = repository.getAvgDailyCost(7)
                val daysRemaining = if (avgDailyCost > 0.0001) (balance / avgDailyCost).toInt() else 0
                val trendData = repository.getDailyCostList(30)
                val modelCosts = repository.getModelCosts(30)

                // v2: 缓存命中率 & 请求次数
                val cacheHitRate = repository.getMonthlyCacheHitRate()
                val (cacheHit, cacheMiss) = repository.getMonthlyCacheTokens()
                val dailyRequests = repository.getDailyRequestCount()
                val monthlyRequests = repository.getMonthlyRequestCount()
                // 估算缓存节省的金额（命中缓存的 token 按最低价格估算）
                val cacheEstimatedSaved = if (cacheHit > 0) {
                    // deepseek-v4-flash: 缓存命中 0.02元/百万tokens，未命中 1元/百万tokens
                    val savedPerToken = (1.0 - 0.02) / 1_000_000.0
                    cacheHit * savedPerToken
                } else 0.0

                hasLoaded = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        balance = balance,
                        avgDailyCost = avgDailyCost,
                        daysRemaining = daysRemaining,
                        trendData = trendData,
                        modelCosts = modelCosts,
                        cacheHitRate = cacheHitRate,
                        cacheHitTokens = cacheHit,
                        cacheMissTokens = cacheMiss,
                        cacheEstimatedSaved = cacheEstimatedSaved,
                        dailyRequests = dailyRequests,
                        monthlyRequests = monthlyRequests
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}
