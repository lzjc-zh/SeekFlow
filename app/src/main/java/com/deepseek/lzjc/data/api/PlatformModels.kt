package com.deepseek.lzjc.data.api

import com.google.gson.annotations.SerializedName

// ===== 通用响应包装 =====

/** 平台 API 统一响应外层 */
data class PlatformResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: PlatformData<T>?
)

data class PlatformData<T>(
    @SerializedName("biz_code") val bizCode: Int,
    @SerializedName("biz_msg") val bizMsg: String,
    @SerializedName("biz_data") val bizData: T?
)

// ===== 用户概览 (get_user_summary) =====

data class UserSummary(
    @SerializedName("current_token") val currentToken: Long,
    @SerializedName("monthly_usage") val monthlyUsage: String,
    @SerializedName("total_usage") val totalUsage: Long,
    @SerializedName("normal_wallets") val normalWallets: List<WalletInfo>,
    @SerializedName("bonus_wallets") val bonusWallets: List<WalletInfo>,
    @SerializedName("total_available_token_estimation") val totalAvailableTokenEstimation: String,
    @SerializedName("monthly_costs") val monthlyCosts: List<MonthlyCost>,
    @SerializedName("monthly_token_usage") val monthlyTokenUsage: String
)

data class WalletInfo(
    @SerializedName("currency") val currency: String,
    @SerializedName("balance") val balance: String,
    @SerializedName("token_estimation") val tokenEstimation: String
)

data class MonthlyCost(
    @SerializedName("currency") val currency: String,
    @SerializedName("amount") val amount: String
)

// ===== 用量统计 (usage/amount) =====

data class UsageAmountData(
    @SerializedName("total") val total: List<ModelUsage>,
    @SerializedName("days") val days: List<DailyUsage>
)

data class ModelUsage(
    @SerializedName("model") val model: String,
    @SerializedName("usage") val usage: List<UsageItem>
)

data class UsageItem(
    @SerializedName("type") val type: String,
    @SerializedName("amount") val amount: String
)

data class DailyUsage(
    @SerializedName("date") val date: String,
    @SerializedName("data") val data: List<ModelUsage>
)

// ===== 费用统计 (usage/cost) =====

data class UsageCostData(
    @SerializedName("total") val total: List<ModelUsage>,
    @SerializedName("days") val days: List<DailyUsage>,
    @SerializedName("currency") val currency: String
)

// ===== 聚合后的展示数据 =====

/** 每日汇总（token + 费用） */
data class DailySummary(
    @SerializedName("date") val date: String,
    @SerializedName("totalTokens") val totalTokens: Long,
    @SerializedName("totalCost") val totalCost: Double,
    @SerializedName("modelTokens") val modelTokens: Map<String, Long>
)

/** 模型汇总 */
data class ModelSummary(
    @SerializedName("model") val model: String,
    @SerializedName("totalTokens") val totalTokens: Long,
    @SerializedName("totalCost") val totalCost: Double
)
