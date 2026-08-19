package com.deepseek.lzjc.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deepseek.lzjc.data.api.BalanceInfo
import com.deepseek.lzjc.data.api.BalanceResponse
import com.deepseek.lzjc.data.api.DeepSeekApi
import com.deepseek.lzjc.data.api.PlatformApi
import com.deepseek.lzjc.data.api.UserSummary
import com.deepseek.lzjc.data.db.DailyModelBreakdown
import com.deepseek.lzjc.data.db.DailyUsageSummary
import com.deepseek.lzjc.data.db.EnhancedDailySummary
import com.deepseek.lzjc.data.db.EnhancedModelCostSummary
import com.deepseek.lzjc.data.db.ModelCostSummary
import com.deepseek.lzjc.data.db.UsageDao
import com.deepseek.lzjc.data.db.UsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val api: DeepSeekApi,
    private val platformApi: PlatformApi,
    private val usageDao: UsageDao,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_USER_TOKEN = stringPreferencesKey("user_token")
        val KEY_LAST_BALANCE = doublePreferencesKey("last_balance")
        val KEY_LAST_TOTAL_BALANCE = doublePreferencesKey("last_total_balance")

        const val MODEL_FLASH = "deepseek-v4-flash"
        const val MODEL_PRO = "deepseek-v4-pro"
        const val MODEL_CHAT_REASONER = "deepseek-chat & deepseek-reasoner"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    val apiKey: Flow<String> = dataStore.data.map { it[KEY_API_KEY] ?: "" }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[KEY_API_KEY] = key }
    }

    val userToken: Flow<String> = dataStore.data.map { it[KEY_USER_TOKEN] ?: "" }

    suspend fun saveUserToken(token: String) {
        dataStore.edit { it[KEY_USER_TOKEN] = token }
    }

    suspend fun fetchBalance(): Result<BalanceResponse> {
        return try {
            val key = dataStore.data.first()[KEY_API_KEY] ?: ""
            if (key.isBlank()) {
                return Result.failure(Exception("Please set API Key first"))
            }

            val response = api.getBalance("Bearer $key")
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserSummary(): Result<UserSummary> {
        return try {
            val token = dataStore.data.first()[KEY_USER_TOKEN] ?: ""
            if (token.isBlank()) {
                return Result.failure(Exception("Please set User Token first"))
            }

            val response = platformApi.getUserSummary("Bearer $token")
            if (response.code != 0 || response.data?.bizData == null) {
                return Result.failure(
                    Exception(response.msg.ifBlank { "Failed to fetch user summary" })
                )
            }

            Result.success(response.data.bizData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMonthlyUsage(year: Int, month: Int): Result<Unit> {
        return try {
            val token = dataStore.data.first()[KEY_USER_TOKEN] ?: ""
            if (token.isBlank()) {
                return Result.failure(Exception("Please set User Token first"))
            }

            val auth = "Bearer $token"
            val amountResp = platformApi.getUsageAmount(auth, month, year)
            val costResp = platformApi.getUsageCost(auth, month, year)

            if (amountResp.code != 0) {
                return Result.failure(Exception("Failed to fetch usage amount: ${amountResp.msg}"))
            }
            if (costResp.code != 0) {
                return Result.failure(Exception("Failed to fetch usage cost: ${costResp.msg}"))
            }

            val amountData = amountResp.data?.bizData
            val costDataList = costResp.data?.bizData
            val costMap = mutableMapOf<String, Double>()

            costDataList?.forEach { currencyData ->
                currencyData.days.forEach { day ->
                    day.data.forEach { modelUsage ->
                        val totalCost = modelUsage.usage.sumOf {
                            it.amount.toDoubleOrNull() ?: 0.0
                        }
                        costMap["${day.date}|${modelUsage.model}"] = totalCost
                    }
                }
            }

            val monthStr = String.format("%04d-%02d", year, month)
            amountData?.days?.forEach { day ->
                day.data.forEach { modelUsage ->
                    // 按 token 类型分别提取
                    var cacheHit = 0L
                    var cacheMiss = 0L
                    var outputTokens = 0L
                    var requestCount = 0L
                    var totalTokens = 0L

                    modelUsage.usage.forEach { item ->
                        val amount = item.amount.toLongOrNull() ?: 0L
                        when (item.type) {
                            "PROMPT_CACHE_HIT_TOKEN" -> cacheHit = amount
                            "PROMPT_CACHE_MISS_TOKEN" -> cacheMiss = amount
                            "RESPONSE_TOKEN" -> outputTokens = amount
                            "REQUEST" -> requestCount = amount
                            else -> totalTokens += amount  // PROMPT_TOKEN 等其他类型
                        }
                    }
                    // totalTokens = cacheHit + cacheMiss + outputTokens (精确计算)
                    val computedTotal = cacheHit + cacheMiss + outputTokens
                    if (computedTotal > 0) totalTokens = computedTotal

                    val cost = costMap["${day.date}|${modelUsage.model}"] ?: 0.0

                    usageDao.deleteByDateAndModel(day.date, modelUsage.model)
                    usageDao.insert(
                        UsageEntity(
                            timestamp = System.currentTimeMillis(),
                            date = day.date,
                            month = monthStr,
                            model = modelUsage.model,
                            inputTokens = cacheHit + cacheMiss,
                            outputTokens = outputTokens,
                            totalTokens = totalTokens,
                            costAmount = cost,
                            cacheHitTokens = cacheHit,
                            cacheMissTokens = cacheMiss,
                            requestCount = requestCount
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAndRecord(): Result<BalanceResponse> {
        val token = dataStore.data.first()[KEY_USER_TOKEN] ?: ""

        if (token.isNotBlank()) {
            return try {
                val summaryResp = platformApi.getUserSummary("Bearer $token")
                if (summaryResp.code == 0 && summaryResp.data?.bizData != null) {
                    val summary = summaryResp.data.bizData
                    val normalBalance = summary.normalWallets.firstOrNull()?.balance ?: "0"
                    val bonusBalance = summary.bonusWallets.firstOrNull()?.balance ?: "0"

                    val cal = Calendar.getInstance()
                    fetchMonthlyUsage(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

                    Result.success(
                        BalanceResponse(
                            isAvailable = true,
                            balanceInfos = listOf(
                                BalanceInfo(
                                    currency = summary.normalWallets.firstOrNull()?.currency ?: "CNY",
                                    totalBalance = String.format(
                                        "%.2f",
                                        normalBalance.toDoubleOrNull() ?: 0.0
                                    ),
                                    grantedBalance = String.format(
                                        "%.2f",
                                        bonusBalance.toDoubleOrNull() ?: 0.0
                                    ),
                                    toppedUpBalance = String.format(
                                        "%.2f",
                                        normalBalance.toDoubleOrNull() ?: 0.0
                                    )
                                )
                            )
                        )
                    )
                } else {
                    fetchBalance()
                }
            } catch (_: Exception) {
                fetchBalance()
            }
        }

        val result = fetchBalance()
        result.onSuccess { response ->
            val info = response.balanceInfos.firstOrNull()
            val currentTotal = info?.totalBalance?.toDoubleOrNull() ?: return@onSuccess
            val lastTotal = dataStore.data.first()[KEY_LAST_TOTAL_BALANCE] ?: currentTotal
            val delta = lastTotal - currentTotal

            if (delta > 0.001) {
                val today = dateFormat.format(Date())
                val month = monthFormat.format(Date())
                usageDao.deleteByDateAndModel(today, "balance-delta")
                usageDao.insert(
                    UsageEntity(
                        timestamp = System.currentTimeMillis(),
                        date = today,
                        month = month,
                        model = "balance-delta",
                        totalTokens = 0,
                        costAmount = delta
                    )
                )
            }

            dataStore.edit { it[KEY_LAST_TOTAL_BALANCE] = currentTotal }
        }
        return result
    }

    suspend fun getDailyCost(date: String = dateFormat.format(Date())): Double {
        return usageDao.getDailyCost(date)
    }

    suspend fun getMonthlyCost(month: String = monthFormat.format(Date())): Double {
        return usageDao.getMonthlyCost(month)
    }

    suspend fun getDailyTotalTokens(date: String = dateFormat.format(Date())): Long {
        return usageDao.getDailyTotalTokens(date)
    }

    suspend fun getMonthlyTotalTokens(month: String = monthFormat.format(Date())): Long {
        return usageDao.getMonthlyTotalTokens(month)
    }

    suspend fun getDailyModelTokens(model: String, date: String = dateFormat.format(Date())): Long {
        return usageDao.getDailyModelTokens(date, model)
    }

    suspend fun getMonthlyModelTokens(model: String, month: String = monthFormat.format(Date())): Long {
        return usageDao.getMonthlyModelTokens(month, model)
    }

    fun getDailyUsageSince(fromDate: String): Flow<List<DailyUsageSummary>> {
        return usageDao.getDailyUsageSince(fromDate)
    }

    suspend fun addManualRecord(
        model: String,
        inputTokens: Long,
        outputTokens: Long,
        costAmount: Double
    ) {
        val now = Date()
        usageDao.insert(
            UsageEntity(
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

    // ===== 鍒嗘瀽鐩稿叧 =====

    /** 鑾峰彇鏈€杩慛澶╃殑姣忔棩娑堣€楀垪琛紙涓嶉€氳繃Flow锛岀洿鎺ヨ繑鍥烇級 */
    suspend fun getDailyCostList(days: Int = 30): List<DailyUsageSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyCostListSince(fromDate)
    }

    /** 鑾峰彇鎸夋ā鍨嬫眹鎬荤殑娑堣垂 */
    suspend fun getModelCosts(days: Int = 30): List<ModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getModelCostSince(fromDate)
    }

    /** 鑾峰彇鏃ュ潎娑堣€?*/
    suspend fun getAvgDailyCost(days: Int = 7): Double {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getAvgDailyCostSince(fromDate)
    }

    // ===== v2: 缓存命中率 & 请求次数 =====

    /** 获取本月缓存命中率 (0.0 ~ 1.0) */
    suspend fun getMonthlyCacheHitRate(month: String = monthFormat.format(Date())): Double {
        val hit = usageDao.getMonthlyCacheHitTokens(month)
        val miss = usageDao.getMonthlyCacheMissTokens(month)
        val total = hit + miss
        return if (total > 0) hit.toDouble() / total else 0.0
    }

    /** 获取本月缓存命中/未命中 token */
    suspend fun getMonthlyCacheTokens(month: String = monthFormat.format(Date())): Pair<Long, Long> {
        val hit = usageDao.getMonthlyCacheHitTokens(month)
        val miss = usageDao.getMonthlyCacheMissTokens(month)
        return hit to miss
    }

    /** 获取今日请求次数 */
    suspend fun getDailyRequestCount(date: String = dateFormat.format(Date())): Long {
        return usageDao.getDailyRequestCount(date)
    }

    /** 获取本月请求次数 */
    suspend fun getMonthlyRequestCount(month: String = monthFormat.format(Date())): Long {
        return usageDao.getMonthlyRequestCount(month)
    }

    /** 获取增强版每日数据（含缓存和请求次数） */
    suspend fun getEnhancedDailyData(days: Int = 30): List<EnhancedDailySummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getEnhancedDailySince(fromDate)
    }

    /** 获取增强版模型汇总（含缓存和请求次数） */
    suspend fun getEnhancedModelCosts(days: Int = 30): List<EnhancedModelCostSummary> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getEnhancedModelCostSince(fromDate)
    }

    /** 获取每日每模型明细（用于柱状图点触弹窗） */
    suspend fun getDailyModelBreakdowns(days: Int = 7): List<DailyModelBreakdown> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val fromDate = dateFormat.format(cal.time)
        return usageDao.getDailyModelBreakdownSince(fromDate)
    }

}
