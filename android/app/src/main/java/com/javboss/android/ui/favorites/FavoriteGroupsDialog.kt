package com.javboss.android.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.FavoriteGroup
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

/**
 * 番号 → 收藏夹选择（对应 GET/PUT /jav/items/{id}/favorite-groups）。
 * 保存时整体替换所选分组。
 */
@Composable
fun FavoriteGroupsDialog(
    itemId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    /** 实体：jav / idol / studio / series */
    entity: String = "jav",
) {
    val segment = when (entity) {
        "idol" -> "idols"
        "studio" -> "studios"
        "series" -> "series"
        else -> "items"
    }
    var groups by remember { mutableStateOf<List<FavoriteGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val selection = remember { mutableStateMapOf<Long, Boolean>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        when (val groupsResult = AppGraph.jav.favoriteGroups(entity)) {
            is ApiResult.Success -> {
                groups = groupsResult.data
                when (val selected = AppGraph.jav.favoriteIdsFor(segment, itemId)) {
                    is ApiResult.Success -> {
                        selection.clear()
                        groups.forEach { selection[it.id] = selected.data.contains(it.id) }
                        error = null
                    }
                    is ApiResult.Failure -> error = selected.message
                }
            }
            is ApiResult.Failure -> error = groupsResult.message
        }
        loading = false
    }

    LaunchedEffect(itemId, entity) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(16.dp), strokeWidth = 2.dp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(groups) { group ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selection[group.id] == true,
                                onCheckedChange = { selection[group.id] = it },
                            )
                            Text(group.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("新建收藏夹") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newGroupName.isNotBlank(),
                        onClick = {
                            val name = newGroupName.trim()
                            newGroupName = ""
                            scope.launch {
                                when (val created = AppGraph.jav.createFavoriteGroup(entity, name)) {
                                    is ApiResult.Success -> {
                                        selection[created.data.id] = true
                                        reload()
                                    }
                                    is ApiResult.Failure -> error = created.message
                                }
                            }
                        },
                    ) { Text("添加") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ids = selection.filterValues { it }.keys.toList()
                        when (val result = AppGraph.jav.replaceFavoriteFor(segment, itemId, ids)) {
                            is ApiResult.Success -> {
                                saving = false
                                onSaved()
                                onDismiss()
                            }
                            is ApiResult.Failure -> {
                                error = result.message
                                saving = false
                            }
                        }
                    }
                },
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
