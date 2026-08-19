package com.deepseek.lzjc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.db.ModelCostSummary

private val MODEL_COLORS = listOf(
    Color(0xFF4D6BFE),  // 蓝
    Color(0xFFFF6B6B),  // 红
    Color(0xFF51F0AE),  // 绿
    Color(0xFFFFB84D),  // 橙
    Color(0xFFB84DFF),  // 紫
    Color(0xFF19C9FF),  // 青
    Color(0xFFFF69B4),  // 粉
    Color(0xFF8B8B8B),  // 灰
)

@Composable
fun ModelPieChart(
    modelCosts: List<ModelCostSummary>,
    modifier: Modifier = Modifier
) {
    // 预计算所有数值，避免重组时重复计算
    val total = remember(modelCosts) { modelCosts.sumOf { it.costAmount } }
    val legendItems = remember(modelCosts, total) {
        if (total > 0.0) {
            modelCosts.mapIndexed { index, item ->
                LegendItem(
                    color = MODEL_COLORS[index % MODEL_COLORS.size],
                    name = item.model,
                    pct = (item.costAmount / total * 100)
                )
            }
        } else emptyList()
    }
    val arcData = remember(modelCosts, total) {
        if (total > 0.0) {
            modelCosts.mapIndexed { index, item ->
                ArcData(
                    color = MODEL_COLORS[index % MODEL_COLORS.size],
                    sweepAngle = (item.costAmount / total * 360).toFloat()
                )
            }
        } else emptyList()
    }

    GlassPanel(modifier = modifier.fillMaxWidth(), radius = 22) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.pie_title),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            if (modelCosts.isEmpty() || total <= 0.0) {
                Text(
                    stringResource(R.string.pie_no_data),
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 环形图
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(72.dp)) {
                            val strokeWidth = 14f
                            val diameter = size.width - strokeWidth
                            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                            var startAngle = -90f
                            arcData.forEach { arc ->
                                drawArc(
                                    color = arc.color,
                                    startAngle = startAngle,
                                    sweepAngle = arc.sweepAngle,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = Size(diameter, diameter),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += arc.sweepAngle
                            }
                        }
                        Text(
                            "¥${String.format("%.1f", total)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // 图例
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        legendItems.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(item.color)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.name,
                                    color = Color(0xFF333333),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    "${String.format("%.0f", item.pct)}%",
                                    color = Color(0xFF666666),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LegendItem(val color: Color, val name: String, val pct: Double)
private data class ArcData(val color: Color, val sweepAngle: Float)
