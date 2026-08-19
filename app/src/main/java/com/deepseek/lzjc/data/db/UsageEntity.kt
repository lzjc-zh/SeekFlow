package com.deepseek.lzjc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String,
    val month: String,
    val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val costAmount: Double = 0.0,
    // --- v2: 细分 token 类型 ---
    val cacheHitTokens: Long = 0,       // PROMPT_CACHE_HIT_TOKEN
    val cacheMissTokens: Long = 0,      // PROMPT_CACHE_MISS_TOKEN
    val requestCount: Long = 0           // REQUEST 次数
)
