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
    val modelCosts: List<ModelCostSummary> = emptyList()
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
                // 获取当前合计余额（刷新全部供应商）
                val results = repository.refreshAllProviders()
                val balance = results.values
                    .mapNotNull { it.getOrNull() }
                    .sumOf { it.totalBalance.toDoubleOrNull() ?: 0.0 }

                // 获取汇总分析数据
                val avgDailyCost = repository.getAvgDailyCostAll(7)
                val daysRemaining = if (avgDailyCost > 0.0001) (balance / avgDailyCost).toInt() else 0
                val trendData = repository.getDailyCostListAll(30)
                val modelCosts = repository.getModelCostsAll(30)

                hasLoaded = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        balance = balance,
                        avgDailyCost = avgDailyCost,
                        daysRemaining = daysRemaining,
                        trendData = trendData,
                        modelCosts = modelCosts
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}
