package com.deepseek.lzjc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.db.DailyModelBreakdown

private val MODEL_COLORS = mapOf(
    "deepseek-v4-flash" to Color(0xFF19C9FF),
    "deepseek-v4-pro" to Color(0xFFB84DFF),
    "deepseek-chat" to Color(0xFF4D6BFE),
    "deepseek-reasoner" to Color(0xFFFF6B6B),
)

private fun modelColor(model: String): Color {
    return MODEL_COLORS.entries.firstOrNull { model.contains(it.key) }?.value
        ?: Color(0xFF8B8B8B)
}

private fun formatTokens(n: Long): String {
    return when {
        n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}

@Composable
fun DayModelBreakdownPopup(
    date: String,
    breakdowns: List<DailyModelBreakdown>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume */ },
            radius = 22
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = date,
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                if (breakdowns.isEmpty()) {
                    Text(
                        stringResource(R.string.cache_no_data),
                        color = Color(0xFF999999),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // 列头
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(12.dp))
                        Text("", modifier = Modifier.weight(1f))
                        listOf(
                            stringResource(R.string.breakdown_cache_hit),
                            stringResource(R.string.breakdown_cache_miss),
                            stringResource(R.string.breakdown_output),
                            stringResource(R.string.breakdown_requests)
                        ).forEach { label ->
                            Text(
                                label,
                                color = Color(0xFF999999),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                modifier = Modifier.width(52.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    breakdowns.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = Color(0xFFE5E7EB),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val color = modelColor(item.model)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.model,
                                color = Color(0xFF333333),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(formatTokens(item.cacheHitTokens), color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                            Text(formatTokens(item.cacheMissTokens), color = Color(0xFFF59E0B), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                            Text(formatTokens(item.outputTokens), color = Color(0xFF4D6BFE), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                            Text(formatTokens(item.requestCount), color = Color(0xFF666666), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
