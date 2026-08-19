package com.deepseek.lzjc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deepseek.lzjc.R
import com.deepseek.lzjc.ui.components.BalanceForecast
import com.deepseek.lzjc.ui.components.CacheHitRateCard
import com.deepseek.lzjc.ui.components.ModelPieChart
import com.deepseek.lzjc.ui.components.RefreshAnimation
import com.deepseek.lzjc.ui.components.RequestCountCard
import com.deepseek.lzjc.ui.components.TrendLineChart

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshIfNotLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RefreshAnimation(size = 52.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.loading_analytics_data),
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // 纯 LazyColumn，无任何嵌套滚动干扰
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.title_analytics),
                            color = Color(0xFF1A1A1A),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.loading_refreshing), tint = Color(0xFF333333))
                        }
                    }
                }

                item(key = "forecast") {
                    BalanceForecast(
                        balance = state.balance,
                        avgDailyCost = state.avgDailyCost
                    )
                }

                item(key = "cache") {
                    CacheHitRateCard(
                        cacheHitRate = state.cacheHitRate,
                        cacheHitTokens = state.cacheHitTokens,
                        cacheMissTokens = state.cacheMissTokens,
                        estimatedSaved = state.cacheEstimatedSaved
                    )
                }

                item(key = "requests") {
                    RequestCountCard(
                        dailyRequests = state.dailyRequests,
                        monthlyRequests = state.monthlyRequests
                    )
                }

                item(key = "trend") {
                    TrendLineChart(dailyData = state.trendData)
                }

                item(key = "pie") {
                    ModelPieChart(modelCosts = state.modelCosts)
                }
            }
        }

        // 正在刷新时的全屏遮罩
        if (state.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                RefreshAnimation(size = 36.dp, isAnimating = true)
            }
        }
    }
}
