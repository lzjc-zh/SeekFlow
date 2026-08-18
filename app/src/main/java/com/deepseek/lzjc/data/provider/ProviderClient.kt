package com.deepseek.lzjc.data.provider

import com.deepseek.lzjc.data.api.BalanceInfo
import com.deepseek.lzjc.data.api.BalanceResponse
import com.deepseek.lzjc.data.api.DeepSeekApi
import com.deepseek.lzjc.data.api.PlatformApi
import com.deepseek.lzjc.data.api.UserSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 供应商客户端：根据 ProviderConfig 动态构建网络请求，
 * 统一返回归一化的余额与用量数据。
 */
class ProviderClient(private val config: ProviderConfig) {

    private val baseClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val platformClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Referer", "https://platform.deepseek.com/")
                    .header("Origin", "https://platform.deepseek.com")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 以 /v1/ 结尾的 baseUrl，用于 OpenAI 兼容标准路径（models、organization 等）。
     * 若用户配置已含 /v1 则不重复添加。
     */
    private val v1BaseUrl: String
        get() {
            val url = config.normalizedBaseUrl
            return when {
                url.endsWith("/v1/") -> url
                url.endsWith("/v1") -> "$url/"
                else -> "${url}v1/"
            }
        }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    /** 查询余额（归一化结果） */
    suspend fun fetchBalance(): Result<ProviderBalanceResult> = runCatching {
        when (config.type) {
            ProviderType.DEEPSEEK_OFFICIAL -> fetchDeepSeekOfficialBalance()
            ProviderType.OPENAI_COMPATIBLE -> fetchCompatibleBalance()
        }
    }

    /** 查询当月用量明细（按 日期+模型 聚合）。不支持的供应商返回空列表。 */
    suspend fun fetchMonthlyUsage(year: Int, month: Int): List<UsageRecordData> =
        runCatching {
            when (config.type) {
                ProviderType.DEEPSEEK_OFFICIAL -> fetchDeepSeekOfficialUsage(year, month)
                ProviderType.OPENAI_COMPATIBLE -> emptyList()
            }
        }.getOrDefault(emptyList())

    /** 查询聊天可用模型列表 */
    suspend fun fetchModels(): Result<List<String>> = runCatching {
        when (config.type) {
            ProviderType.DEEPSEEK_OFFICIAL -> listOf("deepseek-chat", "deepseek-reasoner")
            ProviderType.OPENAI_COMPATIBLE -> {
                val api = retrofit(v1BaseUrl, baseClient)
                    .create(OpenAiCompatibleApi::class.java)
                val resp = api.getModels("Bearer ${config.apiKey}")
                resp.data.map { it.id }
            }
        }
    }

    // ===== DeepSeek 官方 =====

    private suspend fun fetchDeepSeekOfficialBalance(): ProviderBalanceResult {
        if (config.userToken.isNotBlank()) {
            // 优先走平台 API，数据更完整
            runCatching {
                val summary = getDeepSeekUserSummary()
                if (summary != null) {
                    val normal = summary.normalWallets.firstOrNull()?.balance?.toDoubleOrNull() ?: 0.0
                    val bonus = summary.bonusWallets.firstOrNull()?.balance?.toDoubleOrNull() ?: 0.0
                    return ProviderBalanceResult(
                        currency = summary.normalWallets.firstOrNull()?.currency ?: config.currency,
                        totalBalance = String.format("%.2f", normal),
                        grantedBalance = String.format("%.2f", bonus),
                        toppedUpBalance = String.format("%.2f", normal)
                    )
                }
            }
        }
        // 回退到 API Key 余额接口
        if (config.apiKey.isBlank()) throw IllegalStateException("API Key not set")
        val api = retrofit(DEEPSEEK_API_BASE, baseClient).create(DeepSeekApi::class.java)
        val resp = api.getBalance("Bearer ${config.apiKey}")
        val info = resp.balanceInfos.firstOrNull()
            ?: throw IllegalStateException("Empty balance response")
        return ProviderBalanceResult(
            currency = info.currency,
            totalBalance = info.totalBalance,
            grantedBalance = info.grantedBalance,
            toppedUpBalance = info.toppedUpBalance
        )
    }

    private suspend fun getDeepSeekUserSummary(): UserSummary? {
        if (config.userToken.isBlank()) return null
        val api = retrofit(DEEPSEEK_PLATFORM_BASE, platformClient).create(PlatformApi::class.java)
        val resp = api.getUserSummary("Bearer ${config.userToken}")
        return if (resp.code == 0) resp.data?.bizData else null
    }

    private suspend fun fetchDeepSeekOfficialUsage(year: Int, month: Int): List<UsageRecordData> {
        if (config.userToken.isBlank()) return emptyList()

        val api = retrofit(DEEPSEEK_PLATFORM_BASE, platformClient).create(PlatformApi::class.java)
        val auth = "Bearer ${config.userToken}"

        return coroutineScope {
            val amountDeferred = async { api.getUsageAmount(auth, month, year) }
            val costDeferred = async { api.getUsageCost(auth, month, year) }
            val amountResp = amountDeferred.await()
            val costResp = costDeferred.await()

            if (amountResp.code != 0 || costResp.code != 0) {
                return@coroutineScope emptyList<UsageRecordData>()
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

            val records = mutableListOf<UsageRecordData>()
            amountData?.days?.forEach { day ->
                day.data.forEach { modelUsage ->
                    val totalTokens = modelUsage.usage.sumOf {
                        it.amount.toLongOrNull() ?: 0L
                    }
                    records.add(
                        UsageRecordData(
                            date = day.date,
                            model = modelUsage.model,
                            totalTokens = totalTokens,
                            costAmount = costMap["${day.date}|${modelUsage.model}"] ?: 0.0
                        )
                    )
                }
            }
            records
        }
    }

    // ===== OpenAI 兼容 / 中转站 =====

    /**
     * 余额查询策略（按序尝试）：
     * 1. New-API / One-API 系中转站的通用接口 /api/user/self
     * 2. OpenAI 官方订阅接口 /v1/organization/subscription（兼容格式）
     */
    private suspend fun fetchCompatibleBalance(): ProviderBalanceResult {
        val api = retrofit(v1BaseUrl, baseClient).create(OpenAiCompatibleApi::class.java)
        val auth = "Bearer ${config.apiKey}"

        // 策略 1：New-API / One-API
        runCatching {
            val resp = api.getSelfInfo(auth)
            if (resp.success == true && resp.data != null) {
                return ProviderBalanceResult(
                    currency = "USD",
                    totalBalance = String.format("%.2f", resp.data.quota / QUOTA_UNIT),
                    grantedBalance = "0.00",
                    toppedUpBalance = String.format("%.2f", resp.data.quota / QUOTA_UNIT)
                )
            }
        }

        // 策略 2：OpenAI 官方订阅余额
        runCatching {
            val resp = api.getSubscription(auth)
            if (resp.hasError != true) {
                val hardLimit = resp.hardLimitUsd ?: 0.0
                val softLimit = resp.softLimitUsd ?: 0.0
                val limit = if (softLimit > 0) softLimit else hardLimit
                if (limit > 0) {
                    val cal = Calendar.getInstance()
                    val start = cal.getActualMinimum(Calendar.DAY_OF_MONTH)
                    val end = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val startSec = startOfDayEpoch(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), start)
                    val endSec = endOfDayEpoch(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), end)
                    runCatching {
                        val usageResp = api.getUsage(auth, startSec, endSec)
                        val usedUsd = (usageResp.totalUsage ?: 0.0) / 100.0
                        return ProviderBalanceResult(
                            currency = "USD",
                            totalBalance = String.format("%.2f", (limit - usedUsd).coerceAtLeast(0.0)),
                            grantedBalance = String.format("%.2f", limit),
                            toppedUpBalance = String.format("%.2f", usedUsd)
                        )
                    }
                    return ProviderBalanceResult(
                        currency = "USD",
                        totalBalance = String.format("%.2f", limit),
                        grantedBalance = String.format("%.2f", limit)
                    )
                }
            }
        }

        throw IllegalStateException(
            "This provider does not expose a balance API. " +
                "Usage can still be recorded manually or via the balance-delta tracker."
        )
    }

    private fun startOfDayEpoch(year: Int, month0: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            clear(); set(year, month0, day, 0, 0, 0)
        }
        return cal.timeInMillis / 1000
    }

    private fun endOfDayEpoch(year: Int, month0: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            clear(); set(year, month0, day, 23, 59, 59)
        }
        return cal.timeInMillis / 1000
    }

    companion object {
        const val DEEPSEEK_API_BASE = "https://api.deepseek.com/"
        const val DEEPSEEK_PLATFORM_BASE = "https://platform.deepseek.com/"

        /** New-API 配额单位：500000 quota = $1 */
        private const val QUOTA_UNIT = 500000.0
    }
}

/** 用量明细记录（写入数据库前的中间数据） */
data class UsageRecordData(
    val date: String,
    val model: String,
    val totalTokens: Long,
    val costAmount: Double
)
