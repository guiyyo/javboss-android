package com.javboss.android.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.android.data.subtitle.InstalledSubtitle
import com.javboss.android.data.subtitle.SubtitleCandidate
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 字幕「状态 + 偏移」浮动小块（右侧，半透明）：只管当前字幕与时间轴微调，
 * 选字幕请点「选择字幕」打开 [SubtitlePickerSheet]。
 */
@Composable
fun SubtitlePanel(
    code: String,
    currentName: String?,
    currentOffsetMs: Long,
    embeddedCount: Int = 0,
    usingEmbedded: Boolean = false,
    skipped: Boolean = false,
    onDismiss: () -> Unit,
    onShift: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDetach: () -> Unit,
    onUseEmbedded: () -> Unit,
    onToggleSkip: (Boolean) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wDim = Color.White.copy(alpha = 0.72f)
    Column(
        modifier
            .width(300.dp)
            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字幕", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                " · ${if (code.isBlank()) "未识别" else code}",
                color = wDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onDismiss,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("✕", color = Color.White) }
        }

        Text(
            currentName ?: (if (usingEmbedded) "使用内嵌字幕" else "未加载字幕"),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "偏移 ${formatOffset(currentOffsetMs)}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            Box(Modifier.weight(1f))
            PanelTextButton("归零", currentName != null && currentOffsetMs != 0L) { onResetOffset() }
            PanelTextButton("移除", currentName != null) { onDetach() }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OffsetButton("−1s", currentName != null) { onShift(-1000) }
            OffsetButton("−0.5s", currentName != null) { onShift(-500) }
            OffsetButton("−0.1s", currentName != null) { onShift(-100) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OffsetButton("+0.1s", currentName != null) { onShift(100) }
            OffsetButton("+0.5s", currentName != null) { onShift(500) }
            OffsetButton("+1s", currentName != null) { onShift(1000) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip(if (embeddedCount > 0) "内嵌 $embeddedCount" else "无内嵌", usingEmbedded, embeddedCount > 0) {
                onUseEmbedded()
            }
            DarkChip("本片无需字幕", skipped, true) { onToggleSkip(!skipped) }
        }

        // 选字幕单独一屏，避免这个小块塞太满
        Box(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .clickable { onOpenPicker() }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("选择字幕", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun formatOffset(ms: Long): String {
    val sign = if (ms >= 0) "+" else "−"
    val abs = kotlin.math.abs(ms)
    return "$sign${abs / 1000}.${(abs % 1000) / 100}s"
}

@Composable
internal fun PanelTextButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 24.dp),
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun OffsetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 26.dp),
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DarkChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.12f),
            labelColor = Color.White.copy(alpha = 0.85f),
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            selectedLabelColor = Color.White,
        ),
    )
}
