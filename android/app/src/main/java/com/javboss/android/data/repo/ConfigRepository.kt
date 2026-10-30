package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.DirectoryRef
import com.javboss.android.data.model.TagCategory
import com.javboss.android.data.model.TagCount
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/**
 * 服务器配置（GET/PATCH /config，全部是字符串）。
 * 注意：本地播放器偏好不写这里（Client 模式语义），见 [SettingsStore]。
 */
class ConfigRepository(private val settings: SettingsStore) {

    private val _config = MutableStateFlow<Map<String, String>>(emptyMap())
    val config: StateFlow<Map<String, String>> = _config.asStateFlow()

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun refresh(): ApiResult<Map<String, String>> {
        val result = apiCall { api().config() }
        if (result is ApiResult.Success) _config.value = result.data
        return result
    }

    suspend fun patch(values: Map<String, JsonElement>): ApiResult<Map<String, String>> {
        val result = apiCall { api().patchConfig(kotlinx.serialization.json.JsonObject(values)) }
        when (result) {
            is ApiResult.Success -> {
                _config.value = result.data
                android.util.Log.d("JavBossConfig", "PATCH ok ${values.keys}")
            }
            is ApiResult.Failure ->
                android.util.Log.w("JavBossConfig", "PATCH failed ${result.status}: ${result.message}")
        }
        return result
    }

    suspend fun directories(): ApiResult<List<DirectoryRef>> = apiCall { api().directories() }

    suspend fun videoTags(): ApiResult<List<TagCount>> = apiCall { api().videoTags() }

    suspend fun videoTagCategories(): ApiResult<List<TagCategory>> = apiCall { api().videoTagCategories() }

    fun bool(key: String, default: Boolean = false): Boolean =
        _config.value[key]?.toBooleanStrictOrNull() ?: default

    fun int(key: String, default: Int = 0): Int =
        _config.value[key]?.toIntOrNull() ?: default

    fun string(key: String, default: String = ""): String = _config.value[key] ?: default
}
