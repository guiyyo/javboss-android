package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.TagCategory
import com.javboss.android.data.model.TagCount
import com.javboss.android.data.remote.AssignCategoryRequest
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.remote.NameRequest
import com.javboss.android.data.remote.TagIdsRequest
import com.javboss.android.data.remote.VideoTagAddRequest
import com.javboss.android.data.remote.VideoTagsReplaceRequest
import com.javboss.android.data.settings.SettingsStore

/** 视频标签与分类的增删改查。 */
class TagRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun tags(hideJav: Boolean? = null): ApiResult<List<TagCount>> =
        apiCall { api().videoTags(hideJav) }

    suspend fun categories(): ApiResult<List<TagCategory>> = apiCall { api().videoTagCategories() }

    suspend fun createTag(name: String): ApiResult<TagCount> = apiCall { api().createTag(NameRequest(name)) }

    suspend fun renameTag(id: Long, name: String): ApiResult<Unit> =
        apiCall { api().renameTag(id, NameRequest(name)); Unit }

    suspend fun deleteTag(id: Long): ApiResult<Unit> = apiCall { api().deleteTag(id); Unit }

    suspend fun deleteTags(ids: List<Long>): ApiResult<Unit> =
        apiCall { api().deleteTags(TagIdsRequest(ids)); Unit }

    suspend fun createCategory(name: String): ApiResult<TagCategory> =
        apiCall { api().createTagCategory(NameRequest(name)) }

    suspend fun renameCategory(id: Long, name: String): ApiResult<Unit> =
        apiCall { api().renameTagCategory(id, NameRequest(name)); Unit }

    suspend fun deleteCategory(id: Long): ApiResult<Unit> =
        apiCall { api().deleteTagCategory(id); Unit }

    suspend fun assignCategory(tagIds: List<Long>, categoryId: Long?): ApiResult<Unit> =
        apiCall { api().assignTagsCategory(AssignCategoryRequest(tagIds, categoryId)); Unit }

    suspend fun replaceVideoTags(videoIds: List<Long>, tagIds: List<Long>): ApiResult<Unit> =
        apiCall { api().replaceVideoTags(VideoTagsReplaceRequest(videoIds, tagIds)); Unit }

    suspend fun addTagToVideos(videoIds: List<Long>, tagId: Long): ApiResult<Unit> =
        apiCall { api().addTagToVideos(VideoTagAddRequest(videoIds, tagId)); Unit }
}
