package com.deepseek.lzjc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(record: UsageEntity)

    /** 删除指定日期+模型的记录（用于刷新时替换） */
    @Query("DELETE FROM usage_records WHERE date = :date AND model = :model")
    suspend fun deleteByDateAndModel(date: String, model: String)

    /** 查询指定日期的总消耗 */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE date = :date")
    suspend fun getDailyCost(date: String): Double

    /** 查询指定月份的总消耗 */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE month = :month")
    suspend fun getMonthlyCost(month: String): Double

    /** 查询指定日期指定模型的token总量 */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE date = :date AND model = :model")
    suspend fun getDailyModelTokens(date: String, model: String): Long

    /** 查询指定月份指定模型的token总量 */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE month = :month AND model = :model")
    suspend fun getMonthlyModelTokens(month: String, model: String): Long

    /** 查询最近N天的每日消耗（用于柱状图） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getDailyUsageSince(fromDate: String): Flow<List<DailyUsageSummary>>

    /** 查询指定日期的总token */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE date = :date")
    suspend fun getDailyTotalTokens(date: String): Long

    /** 查询指定月份的总token */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE month = :month")
    suspend fun getMonthlyTotalTokens(month: String): Long

    /** 查询所有记录 */
    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 100): Flow<List<UsageEntity>>

    /** 按模型汇总消费（用于饼图） */
    @Query("""
        SELECT model, SUM(costAmount) as costAmount, SUM(totalTokens) as totalTokens
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
        GROUP BY model
    """)
    suspend fun getModelCostSince(fromDate: String): List<ModelCostSummary>

    /** 计算日均消耗 */
    @Query("""
        SELECT COALESCE(SUM(costAmount), 0.0) / MAX(1, COUNT(DISTINCT date))
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
    """)
    suspend fun getAvgDailyCostSince(fromDate: String): Double

    /** 查询每天的消耗（用于趋势图，包含balance-delta） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyCostListSince(fromDate: String): List<DailyUsageSummary>

    // ===== v2: 缓存命中 & 请求次数相关 =====

    /** 查询指定月份的缓存命中 token 总量 */
    @Query("SELECT COALESCE(SUM(cacheHitTokens), 0) FROM usage_records WHERE month = :month AND model != 'balance-delta'")
    suspend fun getMonthlyCacheHitTokens(month: String): Long

    /** 查询指定月份的缓存未命中 token 总量 */
    @Query("SELECT COALESCE(SUM(cacheMissTokens), 0) FROM usage_records WHERE month = :month AND model != 'balance-delta'")
    suspend fun getMonthlyCacheMissTokens(month: String): Long

    /** 查询指定日期的请求次数 */
    @Query("SELECT COALESCE(SUM(requestCount), 0) FROM usage_records WHERE date = :date AND model != 'balance-delta'")
    suspend fun getDailyRequestCount(date: String): Long

    /** 查询指定月份的请求次数 */
    @Query("SELECT COALESCE(SUM(requestCount), 0) FROM usage_records WHERE month = :month AND model != 'balance-delta'")
    suspend fun getMonthlyRequestCount(month: String): Long

    /** 查询最近N天的每日汇总（含缓存和请求次数，用于增强趋势图） */
    @Query("""
        SELECT date,
               SUM(totalTokens) as totalTokens,
               SUM(costAmount) as costAmount,
               SUM(cacheHitTokens) as cacheHitTokens,
               SUM(cacheMissTokens) as cacheMissTokens,
               SUM(requestCount) as requestCount
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getEnhancedDailySince(fromDate: String): List<EnhancedDailySummary>

    /** 按模型汇总消费（增强版，含缓存和请求次数） */
    @Query("""
        SELECT model,
               SUM(costAmount) as costAmount,
               SUM(totalTokens) as totalTokens,
               SUM(cacheHitTokens) as cacheHitTokens,
               SUM(cacheMissTokens) as cacheMissTokens,
               SUM(requestCount) as requestCount
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
        GROUP BY model
    """)
    suspend fun getEnhancedModelCostSince(fromDate: String): List<EnhancedModelCostSummary>

    /** 查询指定日期范围内按日期+模型分组的明细（用于柱状图点触弹窗） */
    @Query("""
        SELECT date, model,
               SUM(cacheHitTokens) as cacheHitTokens,
               SUM(cacheMissTokens) as cacheMissTokens,
               SUM(outputTokens) as outputTokens,
               SUM(requestCount) as requestCount
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
        GROUP BY date, model
        ORDER BY date ASC, model ASC
    """)
    suspend fun getDailyModelBreakdownSince(fromDate: String): List<DailyModelBreakdown>
}

/** 每日汇总 */
data class DailyUsageSummary(
    val date: String,
    val totalTokens: Long,
    val costAmount: Double
)

/** 模型消费汇总 */
data class ModelCostSummary(
    val model: String,
    val costAmount: Double,
    val totalTokens: Long
)

/** 增强版每日汇总 — 含缓存和请求次数 */
data class EnhancedDailySummary(
    val date: String,
    val totalTokens: Long,
    val costAmount: Double,
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val requestCount: Long
)

/** 增强版模型消费汇总 — 含缓存和请求次数 */
data class EnhancedModelCostSummary(
    val model: String,
    val costAmount: Double,
    val totalTokens: Long,
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val requestCount: Long
)

/** 每日每模型明细 — 用于柱状图点触弹窗 */
data class DailyModelBreakdown(
    val date: String,
    val model: String,
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val outputTokens: Long,
    val requestCount: Long
)
