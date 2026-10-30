package com.javboss.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.JavSeriesSummary
import com.javboss.android.data.model.JavStudioSummary
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.favorites.FavoriteGroupsDialog

/** 片商详情：名称、别名、作品数、系列数、收藏数 + 跳转作品列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDetailScreen(
    studioId: Long,
    onBack: () -> Unit,
    onOpenWorks: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var studio by remember { mutableStateOf<JavStudioSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favoriteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(studioId) {
        when (val result = AppGraph.jav.studioDetail(studioId)) {
            is ApiResult.Success -> studio = result.data
            is ApiResult.Failure -> error = result.message
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(studio?.name ?: "片商") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                error != null -> ErrorBox(error!!)
                studio == null -> LoadingBox(Modifier.fillMaxWidth())
                else -> {
                    val item = studio!!
                    Text(item.name, style = MaterialTheme.typography.headlineSmall)
                    if (item.aliases.isNotEmpty()) {
                        Text(
                            "别名：${item.aliases.joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("作品", "${item.workCount}")
                        Stat("系列", "${item.seriesCount}")
                        Stat("收藏", "${item.favoriteCount}")
                    }
                    if (item.sampleCode.isNotBlank()) {
                        Text("示例番号：${item.sampleCode}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.prefixes.isNotEmpty()) {
                        Text(
                            "番号前缀：${item.prefixes.take(12).joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Button(onClick = { onOpenWorks(item.id) }, modifier = Modifier.weight(1f)) {
                            Text("查看作品（${item.workCount}）")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { favoriteOpen = true }) { Text("收藏夹") }
                    }
                    if (favoriteOpen) {
                        FavoriteGroupsDialog(
                            itemId = item.id,
                            entity = "studio",
                            onDismiss = { favoriteOpen = false },
                        )
                    }
                }
            }
        }
    }
}

/** 系列详情：原名/译名、片商、作品数 + 跳转作品列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onBack: () -> Unit,
    onOpenWorks: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var series by remember { mutableStateOf<JavSeriesSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favoriteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        when (val result = AppGraph.jav.seriesDetail(seriesId)) {
            is ApiResult.Success -> series = result.data
            is ApiResult.Failure -> error = result.message
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(series?.zhName?.ifBlank { series?.name } ?: "系列") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                error != null -> ErrorBox(error!!)
                series == null -> LoadingBox(Modifier.fillMaxWidth())
                else -> {
                    val item = series!!
                    Text(
                        item.zhName.ifBlank { item.name },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (item.zhName.isNotBlank() && item.name.isNotBlank()) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("作品", "${item.workCount}")
                        Stat("收藏", "${item.favoriteCount}")
                        if (item.studioName.isNotBlank()) Stat("片商", item.studioName)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Button(onClick = { onOpenWorks(item.id) }, modifier = Modifier.weight(1f)) {
                            Text("查看作品（${item.workCount}）")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { favoriteOpen = true }) { Text("收藏夹") }
                    }
                    if (favoriteOpen) {
                        FavoriteGroupsDialog(
                            itemId = item.id,
                            entity = "studio",
                            onDismiss = { favoriteOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
