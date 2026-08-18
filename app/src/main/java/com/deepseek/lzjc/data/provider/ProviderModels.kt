package com.deepseek.lzjc.data.provider

/** 供应商类型 */
enum class ProviderType(val key: String) {
    /** DeepSeek 官方：支持 API Key 查余额 + 平台 Token 查用量明细 */
    DEEPSEEK_OFFICIAL("deepseek_official"),

    /** OpenAI 兼容端点：官方 OpenAI 兼容接口或中转站（One-API / New-API 等） */
    OPENAI_COMPATIBLE("openai_compatible");

    companion object {
        fun fromKey(key: String): ProviderType =
            entries.firstOrNull { it.key == key } ?: OPENAI_COMPATIBLE
    }
}

/** 供应商配置 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    /** 仅 DeepSeek 官方使用（platform.deepseek.com 的 userToken，用于用量明细） */
    val userToken: String = "",
    /** 聊天使用的模型名 */
    val chatModel: String = "",
    /** 货币代码：CNY / USD */
    val currency: String = "CNY",
    val enabled: Boolean = true
) {
    val currencySymbol: String
        get() = if (currency == "USD") "$" else "¥"

    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    /** 规范化 baseUrl，保证以 / 结尾 */
    val normalizedBaseUrl: String
        get() {
            val url = baseUrl.trim()
            return if (url.endsWith("/")) url else "$url/"
        }
}

/** 归一化后的余额查询结果 */
data class ProviderBalanceResult(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String = "0.00",
    val toppedUpBalance: String = "0.00"
)
