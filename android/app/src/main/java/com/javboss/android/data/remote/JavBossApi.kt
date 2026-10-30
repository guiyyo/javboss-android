package com.javboss.android.data.remote

import com.javboss.android.data.model.AuthStatus
import com.javboss.android.data.model.AvailabilityProviderItem
import com.javboss.android.data.model.AvailabilityResult
import com.javboss.android.data.model.ExtensionToken
import com.javboss.android.data.model.DirectoryRef
import com.javboss.android.data.model.FavoriteGroup
import com.javboss.android.data.model.FavoriteSelection
import com.javboss.android.data.model.JavFilterOptions
import com.javboss.android.data.model.IdolRef
import com.javboss.android.data.model.JavItem
import com.javboss.android.data.model.JavPrefixSummary
import com.javboss.android.data.model.JavSeriesSummary
import com.javboss.android.data.model.JavStudioSummary
import com.javboss.android.data.model.JavTagCategoryRef
import com.javboss.android.data.model.JavTagCount
import com.javboss.android.data.model.DownloaderSettings
import com.javboss.android.data.model.DownloadsPage
import com.javboss.android.data.model.Page
import com.javboss.android.data.model.ResourceSnapshot
import com.javboss.android.data.model.ToolsResponse
import com.javboss.android.data.model.TranslationBatchStatus
import com.javboss.android.data.model.TranslationResult
import com.javboss.android.data.model.PlaybackInfo
import com.javboss.android.data.model.PlaybackSession
import com.javboss.android.data.model.ScreenshotInfo
import com.javboss.android.data.model.StatusResponse
import com.javboss.android.data.model.TagCategory
import com.javboss.android.data.model.TagCount
import com.javboss.android.data.model.VideoRow
import com.javboss.android.data.model.WatchedTimeSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class PlaybackSessionRequest(
    @SerialName("location_id") val locationId: Long = 0,
)

@Serializable
data class WatchedRequest(
    @SerialName("watched_ms") val watchedMs: Long,
)

@Serializable
data class ScreenshotListResponse(
    val items: List<ScreenshotInfo> = emptyList(),
)

@Serializable
data class ScreenshotCreateRequest(val second: Double)

@Serializable
data class CoverRequest(
    @SerialName("screenshot_name") val screenshotName: String,
)

@Serializable
data class PlayCountResponse(val status: String = "")

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class ExtensionTokensResponse(
    val items: List<ExtensionToken> = emptyList(),
)

@Serializable
data class FavoriteGroupsResponse(
    val items: List<FavoriteGroup> = emptyList(),
)

@Serializable
data class NameRequest(val name: String)

@Serializable
data class CreateDownloadRequest(
    @SerialName("magnet_url") val magnetUrl: String,
)

@Serializable
data class VideoTagAddRequest(
    @SerialName("video_ids") val videoIds: List<Long>,
    @SerialName("tag_id") val tagId: Long,
)

@Serializable
data class JavTagsAddRequest(
    @SerialName("tag_id") val tagId: Long,
    @SerialName("jav_ids") val javIds: List<Long>,
)

@Serializable
data class TagIdsRequest(@SerialName("tag_ids") val tagIds: List<Long>)

@Serializable
data class AssignCategoryRequest(
    @SerialName("tag_ids") val tagIds: List<Long>,
    @SerialName("category_id") val categoryId: Long? = null,
)

@Serializable
data class VideoTagsReplaceRequest(
    @SerialName("video_ids") val videoIds: List<Long>,
    @SerialName("tag_ids") val tagIds: List<Long>,
)

@Serializable
data class ScrapeModeRequest(
    val mode: String,
    val code: String = "",
)

@Serializable
data class LinkJavRequest(
    @SerialName("location_id") val locationId: Long,
    val code: String,
)

@Serializable
data class PossibleCodes(
    val filename: String = "",
    @SerialName("possible_codes") val possibleCodes: List<String> = emptyList(),
)

/** PUT /jav/items/{id} 请求体（字段为可空，未传则不改）。 */
@Serializable
data class JavItemUpdateRequest(
    val title: String? = null,
    @SerialName("zh_title") val zhTitle: String? = null,
    @SerialName("studio_id") val studioId: Long? = null,
    @SerialName("series_id") val seriesId: Long? = null,
    @SerialName("idol_ids") val idolIds: List<Long>? = null,
    @SerialName("tag_ids") val tagIds: List<Long>? = null,
    @SerialName("scraped_tag_ids") val scrapedTagIds: List<Long>? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("favorite_rating") val favoriteRating: Double? = null,
    @SerialName("cover_crop_position") val coverCropPosition: Double? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
)

@Serializable
data class TranslateRequest(
    val title: String? = null,
    val thinking: Boolean? = null,
)

@Serializable
data class BatchStartRequest(
    val scope: String = "missing",
    @SerialName("confirm_overwrite") val confirmOverwrite: Boolean = false,
)

@Serializable
data class GroupIdsRequest(
    @SerialName("group_ids") val groupIds: List<Long>,
)

@Serializable
data class CreateGroupRequest(val name: String)

@Serializable
data class AddToGroupsRequest(
    @SerialName("jav_ids") val javIds: List<Long>,
    @SerialName("group_ids") val groupIds: List<Long>,
)

/**
 * JavBoss 服务端 REST 接口（对应 docs/api-contract.md）。
 * 所有路径相对于配置的 baseUrl，鉴权由 CookieJar 自动完成。
 */
interface JavBossApi {

    /* ---------------------------------------------------------- 基础 */

    @GET("healthz")
    suspend fun health(): StatusResponse

    @GET("auth/status")
    suspend fun authStatus(): AuthStatus

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthStatus

    @POST("auth/logout")
    suspend fun logout()

    @PUT("auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("auth/extension-tokens")
    suspend fun extensionTokens(): ExtensionTokensResponse

    @GET("config")
    suspend fun config(): Map<String, String>

    @PATCH("config")
    suspend fun patchConfig(@Body body: JsonElement): Map<String, String>

    /* ---------------------------------------------------------- JAV */

    @GET("jav")
    suspend fun javList(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
        @Query("prefix") prefix: String? = null,
        @Query("idol_ids") idolIds: String? = null,
        @Query("tag_ids") tagIds: String? = null,
        @Query("studio_id") studioId: Long? = null,
        @Query("series_id") seriesId: Long? = null,
        @Query("solo") solo: Boolean? = null,
        @Query("favorite_group_id") favoriteGroupId: Long? = null,
        @Query("favorite_rating_min") ratingMin: Double? = null,
        @Query("favorite_rating_max") ratingMax: Double? = null,
        @Query("seed") seed: Long? = null,
    ): Page<JavItem>

    @GET("jav/items/{id}")
    suspend fun javItem(@Path("id") id: Long): JavItem

    @PUT("jav/items/{id}")
    suspend fun updateJavItem(@Path("id") id: Long, @Body body: JavItemUpdateRequest): JavItem

    @POST("jav/items/{id}/title-translation")
    suspend fun translateJavTitle(
        @Path("id") id: Long,
        @Body body: TranslateRequest,
    ): TranslationResult

    @GET("jav/title-translation/batch")
    suspend fun titleTranslationBatch(): TranslationBatchStatus

    @POST("jav/title-translation/batch")
    suspend fun startTitleTranslationBatch(@Body body: BatchStartRequest): TranslationBatchStatus

    @DELETE("jav/title-translation/batch")
    suspend fun stopTitleTranslationBatch()

    @GET("jav/providers")
    suspend fun availabilityProviders(): List<AvailabilityProviderItem>

    /** 注意：路径参数是数据源的**数字 id**，不是名称。 */
    @POST("jav/providers/{id}/availability")
    suspend fun checkAvailability(@Path("id") id: Int): AvailabilityResult

    @GET("jav/filter-options")
    suspend fun javFilterOptions(): JavFilterOptions

    @GET("jav/prefixes")
    suspend fun javPrefixes(): List<JavPrefixSummary>

    @GET("jav/tags")
    suspend fun javTags(): List<JavTagCount>

    @GET("jav/tag-categories")
    suspend fun javTagCategories(): List<JavTagCategoryRef>

    @GET("jav/studios")
    suspend fun javStudios(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
    ): Page<JavStudioSummary>

    @GET("jav/studios/{id}")
    suspend fun javStudio(@Path("id") id: Long): JavStudioSummary

    @GET("jav/series/{id}")
    suspend fun javSeriesDetail(@Path("id") id: Long): JavSeriesSummary

    @POST("jav/tags/add")
    suspend fun addJavTags(@Body body: JavTagsAddRequest)

    @GET("jav/series")
    suspend fun javSeries(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
        @Query("studio_id") studioId: Long? = null,
    ): Page<JavSeriesSummary>

    @GET("jav/idols")
    suspend fun javIdols(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
    ): Page<IdolRef>

    @GET("jav/idols/{id}")
    suspend fun javIdol(@Path("id") id: Long): IdolRef

    /* ---------------------------------------------------------- 收藏分组 */

    @GET("jav/{entity}-favorite-groups")
    suspend fun favoriteGroups(@Path("entity") entity: String): FavoriteGroupsResponse

    @PATCH("jav/{entity}-favorite-groups/{id}")
    suspend fun renameFavoriteGroup(
        @Path("entity") entity: String,
        @Path("id") id: Long,
        @Body body: NameRequest,
    )

    @DELETE("jav/{entity}-favorite-groups/{id}")
    suspend fun deleteFavoriteGroup(@Path("entity") entity: String, @Path("id") id: Long)

    @PUT("jav/{entity}-favorite-groups/order")
    suspend fun reorderFavoriteGroups(@Path("entity") entity: String, @Body body: GroupIdsRequest)

    @POST("jav/{entity}-favorite-groups")
    suspend fun createFavoriteGroup(
        @Path("entity") entity: String,
        @Body body: CreateGroupRequest,
    ): FavoriteGroup

    @GET("jav/items/{id}/favorite-groups")
    suspend fun javFavoriteSelection(@Path("id") id: Long): FavoriteSelection

    /** 通用实体收藏夹：segment = items(番号) / idols / studios / series。 */
    @GET("jav/{segment}/{id}/favorite-groups")
    suspend fun favoriteSelectionFor(
        @Path("segment") segment: String,
        @Path("id") id: Long,
    ): FavoriteSelection

    @PUT("jav/{segment}/{id}/favorite-groups")
    suspend fun replaceFavoriteFor(
        @Path("segment") segment: String,
        @Path("id") id: Long,
        @Body body: GroupIdsRequest,
    )

    @POST("downloads")
    suspend fun createDownload(@Body body: CreateDownloadRequest)

    @PUT("jav/items/{id}/favorite-groups")
    suspend fun replaceJavFavoriteGroups(
        @Path("id") id: Long,
        @Body body: GroupIdsRequest,
    )

    @POST("jav/items/favorite-groups/add")
    suspend fun addJavsToFavoriteGroups(@Body body: AddToGroupsRequest)

    /* ---------------------------------------------------------- 视频库 */

    @GET("videos")
    suspend fun videos(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("search") search: String? = null,
        @Query("tags") tags: String? = null,
        @Query("hide_jav") hideJav: Boolean? = null,
        @Query("seed") seed: Long? = null,
    ): Page<VideoRow>

    @GET("videos/{id}")
    suspend fun video(@Path("id") id: Long): VideoRow

    @GET("videos/{id}/streams")
    suspend fun videoStreams(
        @Path("id") id: Long,
        @Query("location_id") locationId: Long? = null,
    ): PlaybackInfo

    @POST("videos/{id}/play")
    suspend fun incrementPlayCount(@Path("id") id: Long): PlayCountResponse

    @POST("videos/{id}/playback-sessions")
    suspend fun createPlaybackSession(
        @Path("id") id: Long,
        @Body body: PlaybackSessionRequest,
    ): PlaybackSession

    @PUT("videos/{id}/playback-sessions/{sessionId}")
    suspend fun reportPlaybackSession(
        @Path("id") id: Long,
        @Path("sessionId") sessionId: String,
        @Body body: WatchedRequest,
    )

    @GET("videos/watched-time")
    suspend fun watchedTime(
        @Query("video_ids") videoIds: String? = null,
        @Query("jav_ids") javIds: String? = null,
    ): WatchedTimeSnapshot

    @GET("videos/{id}/screenshots")
    suspend fun screenshots(@Path("id") id: Long): ScreenshotListResponse

    @POST("videos/{id}/screenshots")
    suspend fun createScreenshot(
        @Path("id") id: Long,
        @Body body: ScreenshotCreateRequest,
    ): ScreenshotInfo

    @PUT("videos/{id}/screenshots/{name}")
    suspend fun uploadScreenshot(
        @Path("id") id: Long,
        @Path("name") name: String,
        @Body body: okhttp3.RequestBody,
    ): ScreenshotInfo

    @DELETE("videos/{id}/screenshots/{name}")
    suspend fun deleteScreenshot(@Path("id") id: Long, @Path("name") name: String)

    @PUT("videos/{id}/cover")
    suspend fun setCover(@Path("id") id: Long, @Body body: CoverRequest): VideoRow

    @DELETE("videos/{id}/cover")
    suspend fun resetCover(@Path("id") id: Long): VideoRow

    /* ---------------------------------------------------------- 标签 */

    @GET("tags")
    suspend fun videoTags(@Query("hide_jav") hideJav: Boolean? = null): List<TagCount>

    @POST("tags")
    suspend fun createTag(@Body body: NameRequest): TagCount

    @PATCH("tags/{id}")
    suspend fun renameTag(@Path("id") id: Long, @Body body: NameRequest)

    @DELETE("tags/{id}")
    suspend fun deleteTag(@Path("id") id: Long)

    @POST("tags/batch_delete")
    suspend fun deleteTags(@Body body: TagIdsRequest)

    @GET("tags/categories")
    suspend fun videoTagCategories(): List<TagCategory>

    @POST("tags/categories")
    suspend fun createTagCategory(@Body body: NameRequest): TagCategory

    @PATCH("tags/categories/{id}")
    suspend fun renameTagCategory(@Path("id") id: Long, @Body body: NameRequest)

    @DELETE("tags/categories/{id}")
    suspend fun deleteTagCategory(@Path("id") id: Long)

    @POST("tags/category")
    suspend fun assignTagsCategory(@Body body: AssignCategoryRequest)

    @POST("videos/tags/replace")
    suspend fun replaceVideoTags(@Body body: VideoTagsReplaceRequest)

    @POST("videos/tags/add")
    suspend fun addTagToVideos(@Body body: VideoTagAddRequest)

    /* ---------------------------------------------------------- 刮削 */

    @PATCH("videos/{id}/jav-scrape")
    suspend fun setScrapeMode(@Path("id") id: Long, @Body body: ScrapeModeRequest): VideoRow

    @GET("videos/{id}/jav-scrape/possible-codes")
    suspend fun scrapePossibleCodes(@Path("id") id: Long): PossibleCodes

    @POST("videos/{id}/jav-scrape/link")
    suspend fun linkExistingJav(@Path("id") id: Long, @Body body: LinkJavRequest): VideoRow

    /* ---------------------------------------------------------- 目录 / 系统 */

    @GET("directories")
    suspend fun directories(): List<DirectoryRef>

    @PATCH("directories/{id}")
    suspend fun updateDirectory(@Path("id") id: Long, @Body body: JsonElement): DirectoryRef

    @POST("directories/{id}/scan")
    suspend fun scanDirectory(@Path("id") id: Long)

    /* ---------------------------------------------------------- 下载 */

    @GET("downloads")
    suspend fun downloads(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): DownloadsPage

    @POST("downloads/{id}/retry")
    suspend fun retryDownload(@Path("id") id: Long)

    @POST("downloads/{id}/cancel")
    suspend fun cancelDownload(@Path("id") id: Long)

    @DELETE("downloads/{id}")
    suspend fun deleteDownload(@Path("id") id: Long)

    /* ---------------------------------------------------------- 系统 */

    @GET("downloader/settings")
    suspend fun downloaderSettings(): DownloaderSettings

    @PUT("downloader/settings")
    suspend fun updateDownloaderSettings(@Body body: JsonElement): DownloaderSettings

    @GET("system/resources")
    suspend fun resources(): ResourceSnapshot

    @GET("tools")
    suspend fun tools(): ToolsResponse
}
