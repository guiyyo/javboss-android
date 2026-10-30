package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.remote.PossibleCodes
import com.javboss.android.data.model.VideoRow
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.remote.LinkJavRequest
import com.javboss.android.data.remote.ScrapeModeRequest
import com.javboss.android.data.settings.SettingsStore

/** 视频刮削（视频 ↔ 番号关联）。 */
class ScrapeRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun setMode(videoId: Long, body: ScrapeModeRequest): ApiResult<VideoRow> =
        apiCall { api().setScrapeMode(videoId, body) }

    suspend fun possibleCodes(videoId: Long): ApiResult<PossibleCodes> =
        apiCall { api().scrapePossibleCodes(videoId) }

    suspend fun link(videoId: Long, locationId: Long, code: String): ApiResult<VideoRow> =
        apiCall { api().linkExistingJav(videoId, LinkJavRequest(locationId, code)) }
}
