package com.javboss.android.core.task

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 任务进度通知（**不使用前台服务**）。
 *
 * 历史：v0.36.0 用 ForegroundService 保活，真机 Android 16 上启动时抛
 * `ForegroundServiceDidNotStartInTimeException` 直接把进程杀掉（用户点「开始补齐」即闪退）。
 * v0.36.1 改为：任务仍在应用级作用域里跑（切后台继续），只发一条**普通通知**显示进度；
 * 进程被杀后由 `fill.json` 的自动续跑恢复。这样既不崩溃，也保留了后台继续的能力。
 */
object TaskForegroundService {

    private const val CHANNEL_ID = "javboss_tasks"
    private const val NOTIFICATION_ID = 0x4A42

    private var enabled = true

    fun start(context: Context, title: String, text: String) {
        val ctx = context.applicationContext
        enabled = true
        runCatching {
            ensureChannel(ctx)
            val nm = NotificationManagerCompat.from(ctx)
            if (!nm.areNotificationsEnabled()) return@runCatching
            nm.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build(),
            )
        }.onFailure {
            com.javboss.android.core.log.AppLog.w("JavBossTask", "notify failed: " + it.message)
        }
    }

    fun update(context: Context, title: String, text: String) {
        if (!enabled) return
        start(context, title, text)
    }

    fun stop(context: Context) {
        enabled = false
        runCatching {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "后台任务", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
