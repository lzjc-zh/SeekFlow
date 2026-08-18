package com.deepseek.lzjc.data.repository

import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.db.UsageDao
import com.deepseek.lzjc.data.db.UsageEntity
import com.deepseek.lzjc.data.provider.ProviderBalanceResult
import com.deepseek.lzjc.data.provider.ProviderClient
import com.deepseek.lzjc.data.provider.ProviderConfig
import com.deepseek.lzjc.data.provider.ProviderStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多供应商用量仓库。
 * 余额与用量查询通过 ProviderClient 按供应商配置动态发起，
 * 聚合查询支持按供应商过滤或全量汇总。
 */
@Singleton
class UsageRepository @Inject constructor(
    private val providerStore: ProviderStore,
    private val usageDao: UsageDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    val providers: Flow<List<ProviderConfig>> = providerStore.providers
    val enabledProviders: Flow<List<ProviderConfig>> = providerStore.enabledProviders

    /** 是否至少配置了一个带 API Key 的供应商 */
    val hasAnyApiKey: Flow<Boolean> = providerStore.providers.map { list ->
        list.any { it.enabled && it.apiKey.isNotBlank() }
    }

    suspend fun saveProvider(provider: ProviderConfig) = providerStore.save(provider)

    suspend fun deleteProvider(id: String) = providerStore.delete(id)

    suspend fun getEnabledProviders(): List<ProviderConfig> = providerStore.getEnabledProviders()

    // ===== 余额与用量刷新 =====

    /** 刷新单个供应商：拉取余额 + 当月用量并写入数据库 */
    suspend fun refreshProvider(config: ProviderConfig): Result<ProviderBalanceResult> {
        if (config.apiKey.isBlank() && config.userToken.isBlank()) {
            return Result.failure(Exception("API Key not set for ${config.name}"))
        }
        return try {
            val client = ProviderClient(config)
            val balanceResult = client.fetchBalance()
            val balance = balanceResult.getOrThrow()

            // 拉取当月用量明细（仅支持的供应商有数据）
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val monthStr = String.format("%04d-%02d", year, month)
            val usageRecords = client.fetchMonthlyUsage(year, month)

            if (usageRecords.isNotEmpty()) {
                usageDao.deleteByProviderAndMonth(config.id, monthStr)
                usageRecords.forEach { record ->
                    usageDao.insert(
                        UsageEntity(
                            providerId = config.id,
                            timestamp = System.currentTimeMillis(),
                            date = record.date,
                            month = monthStr,
                            model = record.model,
                            totalTokens = record.totalTokens,
                            costAmount = record.costAmount
                        )
                    )
                }
            }

            Result.success(balance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 刷新所有启用的供应商，返回 providerId -> 余额结果 的映射。
     * 单个供应商失败不影响其他供应商。
     */
    suspend fun refreshAllProviders(): Map<String, Result<ProviderBalanceResult>> {
        val results = mutableMapOf<String, Result<ProviderBalanceResult>>()
        getEnabledProviders().forEach { config ->
            results[config.id] = refreshProvider(config)
        }
        return results
    }

    /**
     * 汇总所有供应商的余额（按货币分组后分别求和）。
     * 返回 providerId -> 余额结果。
     */
    suspend fun refreshAndRecord(): Map<String, Result<ProviderBalanceResult>> {
        return refreshAllProviders()
    }

    // ===== 聚合查询（按供应商） =====

    suspend fun getDailyCost(providerId: String, date: String = dateFormat.format(Date())): Double =
        usageDao.getDailyCost(providerId, date)

    suspend fun getMonthlyCost(providerId: String, month: String = monthFormat.format(Date())): Double =
        usageDao.getMonthlyCost(providerId, month)

    suspend fun getDailyTotalTokens(providerId: String, date: String = dateFormat.format(Date())): Long =
        usageDao.getDailyTotalTokens(providerId, date)

    suspend fun getMonthlyTotalTokens(providerId: String, month: String = monthFormat.format(Date())): Long =
        usageDao.getMonthlyTotalTokens(providerId, month)

    suspend fun getMonthlyModelTokens(providerId: String, model: String, month: String = monthFormat.format(Date())): Long =
        usageDao.getMonthlyModelTokens(providerId, month, model)

    fun getDailyUsageSince(providerId: String, fromDate: String): Flow<List<DailyUsageSummary>> =
        usageDao.getDailyUsageSince(providerId, fromDate)

    suspend fun getDailyCostList(providerId: String, days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getDailyCostListSince(providerId, dateFormat.format(cal.time))
    }

    suspend fun getModelCosts(providerId: String, days: Int = 30): List<ModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getModelCostSince(providerId, dateFormat.format(cal.time))
    }

    suspend fun getAvgDailyCost(providerId: String, days: Int = 7): Double {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getAvgDailyCostSince(providerId, dateFormat.format(cal.time))
    }

    // ===== 聚合查询（全部供应商汇总） =====

    suspend fun getDailyCostAll(date: String = dateFormat.format(Date())): Double =
        usageDao.getDailyCostAll(date)

    suspend fun getMonthlyCostAll(month: String = monthFormat.format(Date())): Double =
        usageDao.getMonthlyCostAll(month)

    fun getDailyUsageSinceAll(fromDate: String): Flow<List<DailyUsageSummary>> =
        usageDao.getDailyUsageSinceAll(fromDate)

    suspend fun getDailyCostListAll(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getDailyCostListSinceAll(dateFormat.format(cal.time))
    }

    suspend fun getModelCostsAll(days: Int = 30): List<ModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getModelCostSinceAll(dateFormat.format(cal.time))
    }

    suspend fun getAvgDailyCostAll(days: Int = 7): Double {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return usageDao.getAvgDailyCostSinceAll(dateFormat.format(cal.time))
    }

    // ===== 手动记录（用于不提供用量接口的供应商） =====

    suspend fun addManualRecord(
        providerId: String,
        model: String,
        inputTokens: Long,
        outputTokens: Long,
        costAmount: Double
    ) {
        val now = Date()
        usageDao.insert(
            UsageEntity(
                providerId = providerId,
                timestamp = now.time,
                date = dateFormat.format(now),
                month = monthFormat.format(now),
                model = model,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                totalTokens = inputTokens + outputTokens,
                costAmount = costAmount
            )
        )
    }
}
