package com.deepseek.lzjc.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 供应商配置的持久化存储。
 * 以 JSON 列表形式保存在 DataStore 中，兼容旧的单供应商配置（自动迁移）。
 */
@Singleton
class ProviderStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<ProviderConfig>>() {}.type

    val providers: Flow<List<ProviderConfig>> = dataStore.data.map { prefs ->
        val json = prefs[KEY_PROVIDERS]
        if (!json.isNullOrBlank()) {
            runCatching { gson.fromJson<List<ProviderConfig>>(json, listType) }
                .getOrNull() ?: emptyList()
        } else {
            // 迁移旧版单供应商配置
            migrateLegacy(prefs)
        }
    }

    val enabledProviders: Flow<List<ProviderConfig>> =
        providers.map { list -> list.filter { it.enabled } }

    suspend fun getProviders(): List<ProviderConfig> = providers.first()

    suspend fun getEnabledProviders(): List<ProviderConfig> =
        getProviders().filter { it.enabled }

    suspend fun getById(id: String): ProviderConfig? =
        getProviders().firstOrNull { it.id == id }

    suspend fun save(provider: ProviderConfig) {
        val list = getProviders().toMutableList()
        val index = list.indexOfFirst { it.id == provider.id }
        if (index >= 0) list[index] = provider else list.add(provider)
        persist(list)
    }

    suspend fun delete(id: String) {
        persist(getProviders().filterNot { it.id == id })
    }

    private suspend fun persist(list: List<ProviderConfig>) {
        dataStore.edit { it[KEY_PROVIDERS] = gson.toJson(list) }
    }

    /** 旧版只存了一个 DeepSeek key/token，升级为 ProviderConfig 列表 */
    private fun migrateLegacy(prefs: Preferences): List<ProviderConfig> {
        val key = prefs[stringPreferencesKey("api_key")] ?: ""
        val token = prefs[stringPreferencesKey("user_token")] ?: ""
        if (key.isBlank() && token.isBlank()) return emptyList()
        return listOf(
            ProviderConfig(
                id = "deepseek_default",
                name = "DeepSeek",
                type = ProviderType.DEEPSEEK_OFFICIAL,
                baseUrl = "https://api.deepseek.com/",
                apiKey = key,
                userToken = token,
                chatModel = "deepseek-chat"
            )
        )
    }

    companion object {
        val KEY_PROVIDERS = stringPreferencesKey("providers_json")
    }
}
