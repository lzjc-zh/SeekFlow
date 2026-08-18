package com.deepseek.lzjc.data.provider

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * OpenAI 兼容端点的查询接口：
 * - /v1/models：模型列表（OpenAI 兼容标准）
 * - /api/user/self：New-API / One-API 中转站的用户信息（含 quota 余额）
 * - /v1/organization/subscription 与 /v1/organization/usage：OpenAI 官方余额接口
 */
interface OpenAiCompatibleApi {

    /** baseUrl 应为 .../v1/（见 ProviderClient.v1BaseUrl） */
    @GET("models")
    suspend fun getModels(
        @Header("Authorization") auth: String
    ): ModelsResponse

    /** 使用根绝对路径，兼容 baseUrl 带 /v1 后缀的中转站 */
    @GET("/api/user/self")
    suspend fun getSelfInfo(
        @Header("Authorization") auth: String
    ): SelfInfoResponse

    /** baseUrl 应为 .../v1/ */
    @GET("organization/subscription")
    suspend fun getSubscription(
        @Header("Authorization") auth: String
    ): SubscriptionResponse

    /** baseUrl 应为 .../v1/ */
    @GET("organization/usage")
    suspend fun getUsage(
        @Header("Authorization") auth: String,
        @Query("start_time") startTime: Long,
        @Query("end_time") endTime: Long
    ): OrgUsageResponse
}

data class ModelsResponse(
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String
)

/** New-API / One-API 用户信息响应 */
data class SelfInfoResponse(
    val success: Boolean?,
    val message: String?,
    val data: SelfInfoData?
)

data class SelfInfoData(
    val id: Long?,
    val username: String?,
    @SerializedName("display_name") val displayName: String?,
    val quota: Long,
    @SerializedName("used_quota") val usedQuota: Long = 0
)

/** OpenAI 组织订阅响应 */
data class SubscriptionResponse(
    @SerializedName("has_error") val hasError: Boolean?,
    @SerializedName("soft_limit_usd") val softLimitUsd: Double?,
    @SerializedName("hard_limit_usd") val hardLimitUsd: Double?
)

/** OpenAI 组织用量响应（total_usage 单位：美分） */
data class OrgUsageResponse(
    @SerializedName("total_usage") val totalUsage: Double?
)
