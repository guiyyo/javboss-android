package com.javboss.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** 统一网络图片加载：复用共享 OkHttpClient（自动携带会话 Cookie）。 */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** JAV 封面：竖版 3:4，按 cover_crop_position 做轻微聚焦（1=居中）。 */
@Composable
fun JavCover(
    url: String,
    modifier: Modifier = Modifier,
    cropPosition: Double = 1.0,
) {
    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        NetworkImage(url = url, modifier = Modifier.fillMaxSize())
        // 极轻的底部渐变，保证标题可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
        )
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}小时${m}分" else "${m}分"
}

fun formatUnixDate(unix: Long): String {
    if (unix <= 0) return "—"
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(unix * 1000))
}

/** 观看进度比例（0..1）；无进度返回 0。 */
fun watchedRatio(watchedMs: Long, durationSec: Long): Float {
    if (watchedMs <= 0 || durationSec <= 0) return 0f
    return (watchedMs.toFloat() / (durationSec * 1000f)).coerceIn(0f, 1f)
}

@Composable
fun ProgressBadge(ratio: Float, modifier: Modifier = Modifier) {
    if (ratio <= 0.001f) return
    androidx.compose.material3.LinearProgressIndicator(
        progress = { ratio },
        modifier = modifier.fillMaxWidth(),
    )
}

internal fun nearlyEqual(a: Double, b: Double) = abs(a - b) < 1e-9

@Suppress("unused")
internal val DefaultGridPadding = PaddingValues(12.dp)

@Suppress("unused")
internal val CardWidthHint = 120.dp

@Suppress("unused")
internal fun Modifier.cardWidth(): Modifier = this.width(CardWidthHint)

/** 分钟数 → 「1小时58分」/「58分」 */
fun formatMinutesHm(minutes: Int): String {
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分"
    }
}