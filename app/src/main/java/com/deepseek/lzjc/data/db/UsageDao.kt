package com.deepseek.lzjc.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(record: UsageEntity)

    /** 删除指定供应商+日期+模型的记录（用于刷新时替换） */
    @Query("DELETE FROM usage_records WHERE providerId = :providerId AND date = :date AND model = :model")
    suspend fun deleteByDateAndModel(providerId: String, date: String, model: String)

    /** 删除指定供应商在某个日期范围内的记录（刷新当月数据前清理） */
    @Query("DELETE FROM usage_records WHERE providerId = :providerId AND month = :month")
    suspend fun deleteByProviderAndMonth(providerId: String, month: String)

    /** 查询指定供应商某日期的总消耗 */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE providerId = :providerId AND date = :date")
    suspend fun getDailyCost(providerId: String, date: String): Double

    /** 查询指定供应商某月份的总消耗 */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE providerId = :providerId AND month = :month")
    suspend fun getMonthlyCost(providerId: String, month: String): Double

    /** 查询全部供应商某日期的总消耗（汇总视图） */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE date = :date")
    suspend fun getDailyCostAll(date: String): Double

    /** 查询全部供应商某月份的总消耗（汇总视图） */
    @Query("SELECT COALESCE(SUM(costAmount), 0.0) FROM usage_records WHERE month = :month")
    suspend fun getMonthlyCostAll(month: String): Double

    /** 查询指定供应商指定日期指定模型的token总量 */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE providerId = :providerId AND date = :date AND model = :model")
    suspend fun getDailyModelTokens(providerId: String, date: String, model: String): Long

    /** 查询指定供应商指定月份指定模型的token总量 */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE providerId = :providerId AND month = :month AND model = :model")
    suspend fun getMonthlyModelTokens(providerId: String, month: String, model: String): Long

    /** 查询指定供应商最近N天的每日消耗（用于柱状图） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE providerId = :providerId AND date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getDailyUsageSince(providerId: String, fromDate: String): Flow<List<DailyUsageSummary>>

    /** 查询全部供应商最近N天的每日消耗（汇总视图） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    fun getDailyUsageSinceAll(fromDate: String): Flow<List<DailyUsageSummary>>

    /** 查询指定供应商某日期的总token */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE providerId = :providerId AND date = :date")
    suspend fun getDailyTotalTokens(providerId: String, date: String): Long

    /** 查询指定供应商某月份的总token */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records WHERE providerId = :providerId AND month = :month")
    suspend fun getMonthlyTotalTokens(providerId: String, month: String): Long

    /** 查询所有记录 */
    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 100): Flow<List<UsageEntity>>

    /** 按供应商+模型汇总消费（用于饼图） */
    @Query("""
        SELECT model, SUM(costAmount) as costAmount, SUM(totalTokens) as totalTokens
        FROM usage_records
        WHERE providerId = :providerId AND date >= :fromDate AND model != 'balance-delta'
        GROUP BY model
    """)
    suspend fun getModelCostSince(providerId: String, fromDate: String): List<ModelCostSummary>

    /** 全部供应商按模型汇总消费（用于汇总饼图） */
    @Query("""
        SELECT model, SUM(costAmount) as costAmount, SUM(totalTokens) as totalTokens
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
        GROUP BY model
    """)
    suspend fun getModelCostSinceAll(fromDate: String): List<ModelCostSummary>

    /** 计算指定供应商的日均消耗 */
    @Query("""
        SELECT COALESCE(SUM(costAmount), 0.0) / MAX(1, COUNT(DISTINCT date))
        FROM usage_records
        WHERE providerId = :providerId AND date >= :fromDate AND model != 'balance-delta'
    """)
    suspend fun getAvgDailyCostSince(providerId: String, fromDate: String): Double

    /** 计算全部供应商的日均消耗 */
    @Query("""
        SELECT COALESCE(SUM(costAmount), 0.0) / MAX(1, COUNT(DISTINCT date))
        FROM usage_records
        WHERE date >= :fromDate AND model != 'balance-delta'
    """)
    suspend fun getAvgDailyCostSinceAll(fromDate: String): Double

    /** 查询指定供应商每天的消耗（用于趋势图） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE providerId = :providerId AND date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyCostListSince(providerId: String, fromDate: String): List<DailyUsageSummary>

    /** 查询全部供应商每天的消耗（用于汇总趋势图） */
    @Query("""
        SELECT date, SUM(totalTokens) as totalTokens, SUM(costAmount) as costAmount
        FROM usage_records
        WHERE date >= :fromDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyCostListSinceAll(fromDate: String): List<DailyUsageSummary>
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
