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

/** 字幕筛选维度（S6） */
enum class SubtitleFilter(val label: String) {
    ALL("全部"),
    HAS("已匹配"),
    NO_SUBTITLE("无字幕"),
    NO_NEED("无需字幕"),
}

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

    /** 字幕维度筛选（客户端过滤当前已加载条目） */
    var subtitleFilter by mutableStateOf(SubtitleFilter.ALL)
        private set

    /** 改名避免与属性 setter 的 JVM 签名冲突 */
    fun applySubtitleFilter(value: SubtitleFilter) {
        subtitleFilter = value
    }

    /** 应用字幕筛选后的条目 */
    val itemsFiltered: List<JavItem>
        get() {
            if (subtitleFilter == SubtitleFilter.ALL) return items
            return items.filter { item -> matchesFilter(item.code) }
        }

    private fun matchesFilter(code: String?): Boolean {
        val st = com.javboss.android.data.subtitle.SubtitleIndex.get(code)
        return when (subtitleFilter) {
            SubtitleFilter.ALL -> true
            SubtitleFilter.HAS -> st?.hasSubtitle == true
            SubtitleFilter.NO_SUBTITLE -> st?.noSubtitle == true
            SubtitleFilter.NO_NEED -> st?.noNeedSubtitle == true
        }
    }

    /** 当前已加载条目的字幕统计：已配 / 缺字幕 / 无需字幕 */
    /**
     * 全库口径统计：已配 / 缺字幕 / 无字幕 / 无需字幕
     * 「缺字幕」= 全库总数 - 已配 - 无字幕 - 无需（未处理的全部算缺字幕）
     */
    fun subtitleStats(): List<Int> {
        val (online, noSub, noNeed) = com.javboss.android.data.subtitle.SubtitleIndex.counts()
        return listOf(online, noSub, noNeed)
    }

    var favoriteGroups by mutableStateOf<List<FavoriteGroup>>(emptyList())
        private set

    /** 首页「继续观看」（本机计算：有进度且未看完） */
    var continueWatching by mutableStateOf<List<com.javboss.android.data.model.VideoRow>>(emptyList())
        private set

    /** 导航带条件进入时，禁止快照覆盖（否则「查看作品」不生效）。 */
    private var navFilterApplied = false
    private var initJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            AppGraph.settings.searchHistory.collect { searchHistory = it }
        }
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

    /** 搜索历史（最近在前，最多 12 条） */
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set

    /** 「随便看看」失败/为空的提示 */
    var randomHint by mutableStateOf<String?>(null)
        private set

    fun dismissRandomHint() {
        randomHint = null
    }

    /**
     * 提交搜索：先做番号归一化（`snos079` → `SNOS-079`，服务端对连字符敏感），
     * 再记一条搜索历史。
     */
    fun setSearch(text: String) {
        val normalized = com.javboss.android.core.text.SearchText.forSearch(text)
        if (query.search == normalized) {
            if (normalized.isNotBlank()) recordSearch(normalized)
            return
        }
        query = query.copy(search = normalized)
        if (normalized.isNotBlank()) recordSearch(normalized)
        refresh()
    }

    private fun recordSearch(text: String) {
        viewModelScope.launch { AppGraph.settings.addSearchHistory(text) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { AppGraph.settings.clearSearchHistory() }
    }

    /** 是否处于「随机 10 部」视图（列表被随机结果临时替换） */
    var randomMode by mutableStateOf(false)
        private set

    /**
     * 盲盒（改版）：随机取 10 部**直接替换当前列表**，由用户自己点——不弹窗、不自动进详情。
     * 点卡片与平时一样进详情；点「随机 10 部 ✕」清除即恢复原列表。
     */
    fun randomTen(count: Int = 10) {
        viewModelScope.launch {
            randomHint = null
            val seed = kotlin.random.Random.nextLong(1L, 1_000_000L)
            val q = query.copy(
                pageSize = count.coerceIn(4, 60),
                sort = "random",
                seed = seed,
                watchedState = null,
            )
            when (val result = AppGraph.jav.list(q, offset = 0)) {
                is ApiResult.Success -> {
                    val list = result.data.items
                    if (list.isEmpty()) {
                        randomHint = "当前筛选下没有可随机的作品"
                        return@launch
                    }
                    generation++          // 作废在飞的 loadMore，避免旧结果混进来
                    items = list
                    total = list.size.toLong()
                    endReached = true     // 随机视图只展示这一批，不再无限翻页
                    error = null
                    randomMode = true
                }
                is ApiResult.Failure -> randomHint = "随机失败：" + result.message
            }
        }
    }

    /** 退出随机视图，回到正常列表。 */
    fun clearRandom() {
        if (!randomMode) return
        randomMode = false
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

    /**
     * 查询代际：refresh 时自增。
     * loadMore 在飞的请求如果属于旧代际，结果必须丢弃 —— 否则切筛选/改排序后，
     * 旧的下一页会把上一组条件的结果拼进新列表（TvGridViewModel 早就有这道防护，手机端之前漏了）。
     */
    private var generation = 0

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
        generation++
        val gen = generation
        viewModelScope.launch {
            var collected = emptyList<JavItem>()
            var attempts = 0
            while (true) {
                if (gen != generation) return@launch   // 期间又发起了一次 refresh：本次结果作废
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
            if (gen != generation) return@launch
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
            com.javboss.android.core.log.AppLog.d("JavBossFilter", "refresh watched=${query.watchedState} collected=${collected.size} serverTotal=$serverTotal")
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
        val gen = generation
        viewModelScope.launch {
            var added = emptyList<JavItem>()
            var attempts = 0
            while (true) {
                if (gen != generation) return@launch   // 期间换了筛选条件：这页数据属于旧查询，丢弃
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
            if (gen != generation) { loadingMore = false; return@launch }
            items = items + added
            total = if (query.watchedState != null) items.size.toLong() else serverTotal
            loadingMore = false
        }
    }
}
