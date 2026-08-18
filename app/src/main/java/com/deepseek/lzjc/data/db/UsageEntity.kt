package com.deepseek.lzjc.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["providerId", "date", "model"])]
)
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String = "deepseek_default",
    val timestamp: Long,
    val date: String,
    val month: String,
    val model: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val costAmount: Double = 0.0
)
