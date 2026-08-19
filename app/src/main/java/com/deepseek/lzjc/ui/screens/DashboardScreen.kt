package com.deepseek.lzjc.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.deepseek.lzjc.R
import com.deepseek.lzjc.ui.components.BalanceCard
import com.deepseek.lzjc.ui.components.DailyBarChart
import com.deepseek.lzjc.ui.components.DayModelBreakdownPopup
import com.deepseek.lzjc.ui.components.GlassPanel
import com.deepseek.lzjc.ui.components.ModelTokenRow
import com.deepseek.lzjc.ui.components.RefreshAnimation

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        if (!state.hasApiKey) {
            EmptyDashboard(onNavigateToSettings = onNavigateToSettings)
        } else {
            DashboardContent(state = state, onRefresh = { viewModel.refresh() })
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    onRefresh: () -> Unit
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
                    stringResource(R.string.loading_refreshing),
                    color = Color(0xFF666666),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else {
        var selectedDay by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            // 纯 LazyColumn，无任何嵌套滚动干扰
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item(key = "header") {
                    HeaderBar(onRefresh = onRefresh)
                }

                if (state.errorMessage != null) {
                    item(key = "error") {
                        ErrorStrip(message = state.errorMessage)
                    }
                }

                item(key = "balance") {
                    BalanceCard(
                        totalBalance = state.totalBalance,
                        grantedBalance = state.grantedBalance,
                        toppedUpBalance = state.toppedUpBalance,
                        dailyCost = state.dailyCost,
                        monthlyCost = state.monthlyCost,
                        isLoading = false
                    )
                }

                item(key = "token_summary") {
                    val maxTokens = maxOf(state.flashTokens, state.proTokens, 1L)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModelTokenRow(
                            modelName = "V4 Flash",
                            tokens = state.flashTokens,
                            progress = state.flashTokens.toFloat() / maxTokens,
                            accent = Color(0xFF19C9FF)
                        )
                        ModelTokenRow(
                            modelName = "V4 Pro",
                            tokens = state.proTokens,
                            progress = state.proTokens.toFloat() / maxTokens,
                            accent = Color(0xFFB84DFF)
                        )
                    }
                }

                if (state.dailyRequests > 0 || state.monthlyRequests > 0) {
                    item(key = "requests") {
                        RequestCountSummary(
                            dailyRequests = state.dailyRequests,
                            monthlyRequests = state.monthlyRequests
                        )
                    }
                }

                item(key = "chart") {
                    DailyBarChart(
                        dailyData = state.dailyData,
                        onBarTap = { date -> selectedDay = date }
                    )
                }
            }

            // 正在刷新时的轻量指示器
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

            // 弹窗：显示选中日期的模型明细
            selectedDay?.let { date ->
                val breakdownsForDay = remember(date, state.modelBreakdowns) {
                    state.modelBreakdowns.filter { it.date == date }
                }
                DayModelBreakdownPopup(
                    date = date,
                    breakdowns = breakdownsForDay,
                    onDismiss = { selectedDay = null }
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_seekflow_signal),
                contentDescription = "SeekFlow logo",
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF4D6BFE))) { append("Seek") }
                withStyle(SpanStyle(color = Color(0xFF000000))) { append("Flow") }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.loading_refreshing), tint = Color(0xFF333333))
        }
    }
}

@Composable
private fun ErrorStrip(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFFFF6B6B).copy(alpha = 0.18f), shape = RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(message, color = Color(0xFFFFD8D8), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RequestCountSummary(
    dailyRequests: Long,
    monthlyRequests: Long
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), radius = 18) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.request_title),
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_today),
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(dailyRequests),
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.request_month),
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatCompactNumber(monthlyRequests),
                        color = Color(0xFF1A1A1A),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatCompactNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}

@Composable
private fun EmptyDashboard(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_seekflow_signal),
            contentDescription = "SeekFlow logo",
            modifier = Modifier
                .size(86.dp)
                .background(Color(0xFFF0F0F5), shape = RoundedCornerShape(24.dp))
                .padding(10.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF4D6BFE))) { append("Seek") }
                withStyle(SpanStyle(color = Color(0xFF000000))) { append("Flow") }
            },
            color = Color(0xFF1A1A1A),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.empty_dashboard_desc),
            color = Color(0xFF888888),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF74D9FF),
                contentColor = Color(0xFF06222C)
            )
        ) {
            Text(stringResource(R.string.go_to_settings), fontWeight = FontWeight.SemiBold)
        }
    }
}
