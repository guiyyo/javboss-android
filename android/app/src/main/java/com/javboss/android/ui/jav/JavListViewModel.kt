package com.javboss.android.ui.jav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.FavoriteGroup
import com.javboss.android.data.model.JavItem
import com.javboss.android.data.repo.JavQuery
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class JavSortOption(val value: String, val label: String)

val JavSortOptions = listOf(
    JavSortOption("recent", "最近添加"),
    JavSortOption("recent_asc", "最早添加"),
    JavSortOption("release", "发行日期↓"),
    JavSortOption("release_asc", "发行日期↑"),
    JavSortOption("code", "番号 A→Z"),
    JavSortOption("code_desc", "番号 Z→A"),
    JavSortOption("duration", "时长↓"),
    JavSortOption("duration_asc", "时长↑"),
    JavSortOption("watched", "观看最多"),
    JavSortOption("watched_asc", "观看最少"),
    JavSortOption("favorite_rating", "喜爱度↓"),
    JavSortOption("favorite_rating_asc", "喜爱度↑"),
)

class JavListViewModel : ViewModel() {

    var query by mutableStateOf(JavQuery())
        private set
    var items by mutableStateOf<List<JavItem>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set

    var favoriteGroups by mutableStateOf<List<FavoriteGroup>>(emptyList())
        private set

    /** 首页「继续观看」（本机计算：有进度且未看完） */
    var continueWatching by mutableStateOf<List<com.javboss.android.data.model.VideoRow>>(emptyList())
        private set

    /** 导航带条件进入时，禁止快照覆盖（否则「查看作品」不生效）。 */
    private var navFilterApplied = false
    private var initJob: kotlinx.coroutines.Job? = null

    init {
        initJob = viewModelScope.launch {
            val prefs = AppGraph.settings.uiPrefs.first()
            // 恢复上次的筛选/搜索
            val snapshot = AppGraph.settings.javQuerySnapshot.first()
            val restored = snapshot?.let {
                runCatching {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString(JavQuery.serializer(), it)
                }.getOrNull()
            }
            if (navFilterApplied) return@launch
            query = (restored ?: query).copy(pageSize = 24, sort = restored?.sort ?: prefs.javSort)
            refresh()
        }
        viewModelScope.launch {
            when (val result = AppGraph.video.list(offset = 0, limit = 20, sort = "watched")) {
                is ApiResult.Success -> {
                    continueWatching = result.data.items.filter { video ->
                        video.watchedMs > 5_000 &&
                            video.durationSec > 0 &&
                            video.watchedMs < video.durationSec * 900
                    }.take(10)
                }
                is ApiResult.Failure -> Unit
            }
        }
        viewModelScope.launch {
            when (val result = AppGraph.jav.favoriteGroups("jav")) {
                is ApiResult.Success -> favoriteGroups = result.data
                is ApiResult.Failure -> Unit
            }
        }
    }

    var selectedIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var batchMessage by mutableStateOf<String?>(null)
        private set

    fun toggleSelect(id: Long) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    fun addSelectedToGroups(groupIds: List<Long>) {
        if (selectedIds.isEmpty() || groupIds.isEmpty()) return
        viewModelScope.launch {
            when (val result = AppGraph.jav.addJavsToFavoriteGroups(selectedIds.toList(), groupIds)) {
                is ApiResult.Success -> {
                    batchMessage = "已把 ${selectedIds.size} 部加入 ${groupIds.size} 个收藏夹"
                    selectedIds = emptySet()
                    refresh()
                }
                is ApiResult.Failure -> batchMessage = result.message
            }
        }
    }

    fun addTagToSelected(tagId: Long) {
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            when (val result = AppGraph.jav.addTagToItems(selectedIds.toList(), tagId)) {
                is ApiResult.Success -> {
                    batchMessage = "已为 ${selectedIds.size} 部添加标签"
                    selectedIds = emptySet()
                    refresh()
                }
                is ApiResult.Failure -> batchMessage = result.message
            }
        }
    }

    fun toggleFavoriteGroup(groupId: Long) {
        query = query.copy(favoriteGroupId = if (query.favoriteGroupId == groupId) null else groupId)
        refresh()
    }

    fun setSearch(text: String) {
        if (query.search == text) return
        query = query.copy(search = text)
        refresh()
    }

    fun setSort(sort: String) {
        if (query.sort == sort) return
        query = query.copy(sort = sort)
        refresh()
    }

    fun applyFilter(newQuery: JavQuery) {
        query = newQuery.copy(pageSize = query.pageSize, sort = query.sort)
        refresh()
    }

    /**
     * 导航带来的筛选（女优/片商/系列/番号字典）必须从干净条件开始，
     * 否则会叠加之前残留的 studioId / idolIds，导致结果为空。
     */
    private var lastNavSignature: String? = null

    fun applyNavigationFilter(
        studioId: Long?,
        seriesId: Long?,
        idolIds: List<Long>,
        prefix: String?,
        tagIds: List<Long> = emptyList(),
    ) {
        // 同一组导航参数只应用一次（页面返回时 LaunchedEffect 可能再次触发，避免“自动筛选”）
        val signature = "$studioId|$seriesId|${idolIds.joinToString(",")}|$prefix|${tagIds.joinToString(",")}"
        if (signature == lastNavSignature) return
        lastNavSignature = signature
        navFilterApplied = true
        initJob?.cancel()   // 取消还没跑完的「上次筛选」恢复，避免覆盖导航条件
        query = JavQuery(
            search = "",
            sort = query.sort,
            prefix = prefix,
            idolIds = idolIds,
            tagIds = tagIds,
            studioId = studioId,
            seriesId = seriesId,
            pageSize = query.pageSize,
        )
        refresh()
    }

    fun clearFilter() {
        navFilterApplied = false
        query = JavQuery(pageSize = query.pageSize, sort = query.sort)
        refresh()
    }

    private var pendingRefresh = false

    /** 服务端游标（本地筛选会让 items.size 与 offset 不一致，必须分开计数）。 */
    private var serverOffset = 0
    private var serverTotal = 0L

    private fun applyWatchedFilter(list: List<JavItem>): List<JavItem> = when (query.watchedState) {
        "watched" -> list.filter { it.watchedMs > 0 }
        "unwatched" -> list.filter { it.watchedMs <= 0 }
        else -> list
    }

    fun refresh() {
        if (loading) {
            pendingRefresh = true
            return
        }
        loading = true
        error = null
        endReached = false
        serverOffset = 0
        serverTotal = 0
        viewModelScope.launch {
            var collected = emptyList<JavItem>()
            var attempts = 0
            while (true) {
                when (val result = AppGraph.jav.list(query, offset = serverOffset)) {
                    is ApiResult.Success -> {
                        val raw = result.data.items
                        serverTotal = result.data.total
                        serverOffset += raw.size
                        collected = collected + applyWatchedFilter(raw)
                        endReached = serverOffset >= serverTotal || raw.isEmpty()
                    }
                    is ApiResult.Failure -> {
                        error = result.message
                        endReached = true
                    }
                }
                attempts++
                // 本地筛选后为空时，自动多翻几页，避免出现空白列表
                if (collected.isNotEmpty() || endReached || attempts >= 4) break
            }
            items = collected
            if (!navFilterApplied) {
                runCatching {
                    AppGraph.settings.setJavQuerySnapshot(
                        kotlinx.serialization.json.Json.encodeToString(JavQuery.serializer(), query)
                    )
                }
            }
            // 本地筛选时用已加载的匹配数作为总数，避免显示服务端总数造成误解
            total = if (query.watchedState != null) collected.size.toLong() else serverTotal
            android.util.Log.d("JavBossFilter", "refresh watched=${query.watchedState} collected=${collected.size} serverTotal=$serverTotal")
            loading = false
            if (pendingRefresh) {
                pendingRefresh = false
                refresh()
            }
        }
    }

    fun loadMore() {
        if (loading || loadingMore || endReached) return
        loadingMore = true
        viewModelScope.launch {
            var added = emptyList<JavItem>()
            var attempts = 0
            while (true) {
                when (val result = AppGraph.jav.list(query, offset = serverOffset)) {
                    is ApiResult.Success -> {
                        val raw = result.data.items
                        serverTotal = result.data.total
                        serverOffset += raw.size
                        added = added + applyWatchedFilter(raw)
                        if (raw.isEmpty() || serverOffset >= serverTotal) endReached = true
                    }
                    is ApiResult.Failure -> {
                        error = result.message
                        endReached = true
                    }
                }
                attempts++
                if (added.isNotEmpty() || endReached || attempts >= 4) break
            }
            items = items + added
            total = if (query.watchedState != null) items.size.toLong() else serverTotal
            loadingMore = false
        }
    }
}
