package com.javboss.android.data.update

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 测速结果 */
data class MirrorSpeed(
    val mirror: String,
    val label: String,
    val ok: Boolean,
    /** 平均下载速度（KB/s），失败为 0 */
    val kbps: Int = 0,
    /** 首字节延迟（ms） */
    val ttfbMs: Long = 0,
    val error: String? = null,
)

/**
 * 下载镜像测速：对同一资源（最新 release 的 APK）分段读取一小段，
 * 比较「首字节延迟 + 平均速度」，帮用户挑最快的镜像。
 */
object UpdateMirrorTester {

    private const val MAX_BYTES = 1_500_000      // 最多读 1.5MB
    private const val BUDGET_MS = 2000L          // 每个镜像最多测 2s
    private const val UA = "JavBoss-Android"

    private val client by lazy {
        com.javboss.android.core.net.ApiClient.okHttp.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    suspend fun test(mirror: String, apkUrl: String): MirrorSpeed = withContext(Dispatchers.IO) {
        val url = UpdateMirror.wrap(apkUrl, mirror)
        val label = UpdateMirror.label(mirror)
        android.util.Log.i("JavBossUpdate", "speedtest: $url")
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Range", "bytes=0-$MAX_BYTES")
                .build()

            val start = SystemClock.elapsedRealtime()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext MirrorSpeed(mirror, label, false, error = "HTTP ${resp.code}")
                }
                val body = resp.body ?: return@withContext MirrorSpeed(mirror, label, false, error = "无响应体")
                val input = body.byteStream()
                val buf = ByteArray(32 * 1024)
                var read = 0
                var ttfb = -1L
                var afterTtfbBytes = 0
                var afterTtfbStart = 0L
                while (read < MAX_BYTES) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val now = SystemClock.elapsedRealtime()
                    if (ttfb < 0) {
                        ttfb = now - start
                        afterTtfbStart = now
                    } else {
                        afterTtfbBytes += n
                    }
                    read += n
                    if (now - start > BUDGET_MS) break
                }
                val now = SystemClock.elapsedRealtime()
                if (ttfb < 0) return@withContext MirrorSpeed(mirror, label, false, error = "无数据")
                val transferMs = (now - afterTtfbStart).coerceAtLeast(1)
                val kbps = if (afterTtfbBytes > 0) {
                    ((afterTtfbBytes / 1024.0) / (transferMs / 1000.0)).toInt()
                } else 0
                MirrorSpeed(mirror, label, true, kbps = kbps, ttfbMs = ttfb)
            }
        }.getOrElse { e ->
            MirrorSpeed(mirror, label, false, error = e.message ?: "失败")
        }
    }
}
