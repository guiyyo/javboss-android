package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.AvailabilityProviderItem
import com.javboss.android.data.model.AvailabilityResult
import com.javboss.android.data.model.DirectoryRef
import com.javboss.android.data.model.DownloaderSettings
import com.javboss.android.data.model.DownloadsPage
import com.javboss.android.data.model.ResourceSnapshot
import com.javboss.android.data.model.ToolsResponse
import com.javboss.android.data.remote.CreateDownloadRequest
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.settings.SettingsStore
import kotlinx.serialization.json.JsonElement

/** 服务器侧管理：目录、下载、资源看板、工具状态。 */
class ServerRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun directories(): ApiResult<List<DirectoryRef>> = apiCall { api().directories() }

    suspend fun updateDirectory(id: Long, body: Map<String, JsonElement>): ApiResult<DirectoryRef> =
        apiCall { api().updateDirectory(id, kotlinx.serialization.json.JsonObject(body)) }

    suspend fun scanDirectory(id: Long): ApiResult<Unit> = apiCall { api().scanDirectory(id); Unit }

    suspend fun downloads(limit: Int = 30, offset: Int = 0): ApiResult<DownloadsPage> =
        apiCall { api().downloads(limit, offset) }

    suspend fun createDownload(magnetUrl: String): ApiResult<Unit> = apiCall { api().createDownload(CreateDownloadRequest(magnetUrl)); Unit }

    suspend fun retryDownload(id: Long): ApiResult<Unit> = apiCall { api().retryDownload(id); Unit }

    suspend fun cancelDownload(id: Long): ApiResult<Unit> = apiCall { api().cancelDownload(id); Unit }

    suspend fun deleteDownload(id: Long): ApiResult<Unit> = apiCall { api().deleteDownload(id); Unit }

    suspend fun resources(): ApiResult<ResourceSnapshot> = apiCall { api().resources() }

    suspend fun downloaderSettings(): ApiResult<DownloaderSettings> = apiCall { api().downloaderSettings() }

    suspend fun updateDownloaderSettings(concurrency: Int, minSizeMb: Long): ApiResult<DownloaderSettings> = apiCall {
        api().updateDownloaderSettings(
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "local_concurrency" to kotlinx.serialization.json.JsonPrimitive(concurrency),
                    "min_video_size_mb" to kotlinx.serialization.json.JsonPrimitive(minSizeMb),
                )
            )
        )
    }

    suspend fun providers(): ApiResult<List<AvailabilityProviderItem>> = apiCall { api().availabilityProviders() }

    suspend fun checkProvider(id: Int): ApiResult<AvailabilityResult> = apiCall { api().checkAvailability(id) }

    suspend fun tools(): ApiResult<ToolsResponse> = apiCall { api().tools() }
}
