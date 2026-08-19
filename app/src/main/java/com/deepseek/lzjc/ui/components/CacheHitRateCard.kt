package com.deepseek.lzjc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.lzjc.R

@Composable
fun CacheHitRateCard(
    cacheHitRate: Double,         // 0.0 ~ 1.0
    cacheHitTokens: Long,
    cacheMissTokens: Long,
    estimatedSaved: Double,       // 缓存节省的金额
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier.fillMaxWidth(), radius = 22) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cached,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.cache_title),
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))

            if (cacheHitTokens + cacheMissTokens == 0L) {
                Text(
                    stringResource(R.string.cache_no_data),
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：命中率数字
                    Column {
                        val ratePercent = String.format("%.1f", cacheHitRate * 100)
                        Text(
                            stringResource(R.string.cache_rate_format, ratePercent),
                            color = Color(0xFF10B981),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        if (estimatedSaved > 0.001) {
                            Text(
                                stringResource(R.string.cache_saved, String.format("%.2f", estimatedSaved)),
                                color = Color(0xFF666666),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // 右侧：环形进度
                    CacheRateRing(
                        rate = cacheHitRate,
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 底部命中/未命中详情
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CacheDetailItem(
                        label = stringResource(R.string.cache_hit_tokens),
                        value = formatTokenCount(cacheHitTokens),
                        color = Color(0xFF10B981)
                    )
                    CacheDetailItem(
                        label = stringResource(R.string.cache_miss_tokens),
                        value = formatTokenCount(cacheMissTokens),
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheRateRing(
    rate: Double,
    modifier: Modifier = Modifier
) {
    val bgColor = Color(0xFFE5E7EB)
    val fillColor = Color(0xFF10B981)

    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val padding = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(padding, padding)

        // 背景环
        drawArc(
            color = bgColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // 填充环
        drawArc(
            color = fillColor,
            startAngle = -90f,
            sweepAngle = (360f * rate).toFloat(),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun CacheDetailItem(
    label: String,
    value: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Color(0xFF999999),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            color = Color(0xFF333333),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTokenCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
