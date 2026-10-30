package com.javboss.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import com.javboss.android.data.model.IdolRef
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.common.ErrorBox
import com.javboss.android.ui.common.LoadingBox
import com.javboss.android.ui.common.NetworkImage
import com.javboss.android.ui.favorites.FavoriteGroupsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdolDetailScreen(
    idolId: Long,
    onBack: () -> Unit,
    onOpenWorks: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var idol by remember { mutableStateOf<IdolRef?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favoriteOpen by remember { mutableStateOf(false) }
    val baseUrl = remember { AppGraph.video.baseUrl().trimEnd('/') }

    LaunchedEffect(idolId) {
        when (val result = AppGraph.jav.idol(idolId)) {
            is ApiResult.Success -> idol = result.data
            is ApiResult.Failure -> error = result.message
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(idol?.let { it.chineseName.ifBlank { it.name } } ?: "女优") },
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
                idol == null -> LoadingBox(Modifier.fillMaxWidth())
                else -> {
                    val item = idol!!
                    if (item.coverCode.isNotBlank()) {
                        NetworkImage(
                            url = "$baseUrl/jav/${item.coverCode}/cover",
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                    Text(
                        item.chineseName.ifBlank { item.name },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (item.romanName.isNotBlank()) {
                        Text(item.romanName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        item.heightCm?.let { InfoPair("身高", "$it cm") }
                        item.bust?.let { InfoPair("胸围", "$it") }
                        item.waist?.let { InfoPair("腰围", "$it") }
                        item.hips?.let { InfoPair("臀围", "$it") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        item.cup?.let { InfoPair("罩杯", "$it") }
                        item.birthDate?.let { InfoPair("生日", it.take(10)) }
                        InfoPair("作品", "${item.workCount}")
                        InfoPair("收藏", "${item.favoriteCount}")
                    }
                    if (item.aliases.isNotEmpty()) {
                        Text("别名：${item.aliases.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Button(onClick = { onOpenWorks(item.id) }, modifier = Modifier.weight(1f)) {
                            Text("查看作品（${item.workCount}）")
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { favoriteOpen = true }) {
                            Text("收藏夹")
                        }
                    }
                    if (favoriteOpen) {
                        FavoriteGroupsDialog(
                            itemId = item.id,
                            entity = "idol",
                            onDismiss = { favoriteOpen = false },
                            onSaved = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPair(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
