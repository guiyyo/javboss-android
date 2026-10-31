package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.FavoriteGroup
import com.javboss.android.data.model.JavFilterOptions
import com.javboss.android.data.remote.AddToGroupsRequest
import com.javboss.android.data.remote.BatchStartRequest
import com.javboss.android.data.remote.CreateGroupRequest
import com.javboss.android.data.remote.NameRequest
import com.javboss.android.data.remote.JavItemUpdateRequest
import com.javboss.android.data.remote.JavTagsAddRequest
import com.javboss.android.data.remote.TranslateRequest
import com.javboss.android.data.remote.GroupIdsRequest
import com.javboss.android.data.model.IdolRef
import com.javboss.android.data.model.JavItem
import com.javboss.android.data.model.JavPrefixSummary
import com.javboss.android.data.model.JavSeriesSummary
import com.javboss.android.data.model.JavStudioSummary
import com.javboss.android.data.model.JavTagCategoryRef
import com.javboss.android.data.model.JavTagCount
import com.javboss.android.data.model.TranslationBatchStatus
import com.javboss.android.data.model.TranslationResult
import com.javboss.android.data.model.Page
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.remote.JavTagCategoryAssignRequest
import com.javboss.android.data.remote.TagIdsRequest
import com.javboss.android.data.settings.SettingsStore
import kotlinx.serialization.json.JsonElement

/** JAV 作品库查询条件（对应 GET /jav 的查询参数）。 */
@kotlinx.serialization.Serializable
data class JavQuery(
    val search: String = "",
    val sort: String = "recent",
    val prefix: String? = null,
    val idolIds: List<Long> = emptyList(),
    val tagIds: List<Long> = emptyList(),
    val studioId: Long? = null,
    val seriesId: Long? = null,
    val soloOnly: Boolean = false,
    val favoriteGroupId: Long? = null,
    val ratingMin: Double? = null,
    val ratingMax: Double? = null,
    val pageSize: Int = 24,
    /** 本地筛选（服务端不支持该条件）：null / "watched" 已播放 / "unwatched" 未播放 */
    val watchedState: String? = null,
) {
    fun hasFilter(): Boolean =
        search.isNotBlank() || prefix != null || idolIds.isNotEmpty() || tagIds.isNotEmpty() ||
            studioId != null || seriesId != null || soloOnly || favoriteGroupId != null ||
            ratingMin != null || ratingMax != null || watchedState != null
}

class JavRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun list(query: JavQuery, offset: Int): ApiResult<Page<JavItem>> = apiCall {
        api().javList(
            limit = query.pageSize,
            offset = offset,
            sort = query.sort.takeIf { it.isNotBlank() },
            search = query.search.takeIf { it.isNotBlank() },
            prefix = query.prefix,
            idolIds = query.idolIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            tagIds = query.tagIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            studioId = query.studioId,
            seriesId = query.seriesId,
            solo = if (query.soloOnly) true else null,
            favoriteGroupId = query.favoriteGroupId,
            ratingMin = query.ratingMin,
            ratingMax = query.ratingMax,
        )
    }

    suspend fun detail(id: Long): ApiResult<JavItem> = apiCall { api().javItem(id) }

    suspend fun prefixes(): ApiResult<List<JavPrefixSummary>> = apiCall { api().javPrefixes() }

    suspend fun tags(): ApiResult<List<JavTagCount>> = apiCall { api().javTags() }

    suspend fun tagCategories(): ApiResult<List<JavTagCategoryRef>> = apiCall { api().javTagCategories() }

    /* ---------- JAV 标签管理 ---------- */

    suspend fun createTag(name: String): ApiResult<Unit> = apiCall {
        api().createJavTag(NameRequest(name))
        Unit
    }

    suspend fun renameTag(id: Long, name: String): ApiResult<Unit> = apiCall {
        api().renameJavTag(id, NameRequest(name))
        Unit
    }

    suspend fun deleteTag(id: Long): ApiResult<Unit> = apiCall {
        api().deleteJavTag(id)
        Unit
    }

    suspend fun deleteTags(ids: List<Long>): ApiResult<Unit> = apiCall {
        api().deleteJavTags(TagIdsRequest(ids))
        Unit
    }

    suspend fun assignTagCategory(tagIds: List<Long>, categoryId: Long?): ApiResult<Unit> = apiCall {
        api().assignJavTagCategory(JavTagCategoryAssignRequest(tagIds, categoryId))
        Unit
    }

    suspend fun createTagCategory(name: String): ApiResult<Unit> = apiCall {
        api().createJavTagCategory(NameRequest(name))
        Unit
    }

    suspend fun deleteTagCategory(id: Long): ApiResult<Unit> = apiCall {
        api().deleteJavTagCategory(id)
        Unit
    }

    suspend fun studios(offset: Int, search: String = "", limit: Int = 60): ApiResult<Page<JavStudioSummary>> =
        apiCall { api().javStudios(limit = limit, offset = offset, search = search.takeIf { it.isNotBlank() }) }

    suspend fun seriesList(offset: Int, search: String = "", limit: Int = 60): ApiResult<Page<JavSeriesSummary>> =
        apiCall { api().javSeries(limit = limit, offset = offset, search = search.takeIf { it.isNotBlank() }) }

    suspend fun seriesDetail(id: Long): ApiResult<JavSeriesSummary> = apiCall { api().javSeriesDetail(id) }

    suspend fun studioDetail(id: Long): ApiResult<JavStudioSummary> = apiCall { api().javStudio(id) }

    /** 给多个番号添加同一个标签（服务端接口只接受单标签）。 */
    suspend fun addTagToItems(javIds: List<Long>, tagId: Long): ApiResult<Unit> = apiCall {
        api().addJavTags(JavTagsAddRequest(tagId, javIds))
        Unit
    }

    suspend fun idols(offset: Int, search: String = "", limit: Int = 60): ApiResult<Page<IdolRef>> =
        apiCall { api().javIdols(limit = limit, offset = offset, search = search.takeIf { it.isNotBlank() }) }

    suspend fun idol(id: Long): ApiResult<IdolRef> = apiCall { api().javIdol(id) }

    suspend fun favoriteGroups(entity: String): ApiResult<List<FavoriteGroup>> =
        apiCall { api().favoriteGroups(entity).items }

    suspend fun javFavoriteGroupIds(id: Long): ApiResult<List<Long>> =
        apiCall { api().javFavoriteSelection(id).selectedGroupIds }

    suspend fun replaceJavFavoriteGroups(id: Long, groupIds: List<Long>): ApiResult<Unit> = apiCall {
        api().replaceJavFavoriteGroups(id, GroupIdsRequest(groupIds))
        Unit
    }

    suspend fun addJavsToFavoriteGroups(javIds: List<Long>, groupIds: List<Long>): ApiResult<Unit> = apiCall {
        api().addJavsToFavoriteGroups(AddToGroupsRequest(javIds, groupIds))
        Unit
    }

    /** 实体收藏夹选择：segment = items / idols / studios / series。 */
    suspend fun favoriteIdsFor(segment: String, id: Long): ApiResult<List<Long>> =
        apiCall { api().favoriteSelectionFor(segment, id).selectedGroupIds }

    suspend fun replaceFavoriteFor(segment: String, id: Long, groupIds: List<Long>): ApiResult<Unit> = apiCall {
        api().replaceFavoriteFor(segment, id, GroupIdsRequest(groupIds))
        Unit
    }

    suspend fun createFavoriteGroup(entity: String, name: String): ApiResult<FavoriteGroup> =
        apiCall { api().createFavoriteGroup(entity, CreateGroupRequest(name)) }

    suspend fun renameFavoriteGroup(entity: String, id: Long, name: String): ApiResult<Unit> = apiCall {
        api().renameFavoriteGroup(entity, id, NameRequest(name))
        Unit
    }

    suspend fun deleteFavoriteGroup(entity: String, id: Long): ApiResult<Unit> = apiCall {
        api().deleteFavoriteGroup(entity, id)
        Unit
    }

    suspend fun reorderFavoriteGroups(entity: String, ids: List<Long>): ApiResult<Unit> = apiCall {
        api().reorderFavoriteGroups(entity, GroupIdsRequest(ids))
        Unit
    }

    suspend fun filterOptions(): ApiResult<JavFilterOptions> = apiCall { api().javFilterOptions() }

    suspend fun updateItem(id: Long, body: JavItemUpdateRequest): ApiResult<JavItem> =
        apiCall { api().updateJavItem(id, body) }

    suspend fun rate(id: Long, rating: Double): ApiResult<JavItem> =
        apiCall { api().updateJavItem(id, JavItemUpdateRequest(favoriteRating = rating)) }

    suspend fun translateTitle(id: Long, title: String? = null, thinking: Boolean? = null): ApiResult<TranslationResult> =
        apiCall { api().translateJavTitle(id, TranslateRequest(title, thinking)) }

    suspend fun translationBatch(): ApiResult<TranslationBatchStatus> =
        apiCall { api().titleTranslationBatch() }

    suspend fun startTranslationBatch(scope: String, confirmOverwrite: Boolean): ApiResult<TranslationBatchStatus> =
        apiCall { api().startTitleTranslationBatch(BatchStartRequest(scope, confirmOverwrite)) }

    suspend fun stopTranslationBatch(): ApiResult<Unit> = apiCall { api().stopTitleTranslationBatch(); Unit }
}
