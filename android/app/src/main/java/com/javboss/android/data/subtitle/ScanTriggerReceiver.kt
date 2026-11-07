package com.javboss.android.data.subtitle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 调试用：adb 广播触发批量补齐（正式包不响应）。 */
class ScanTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!com.javboss.android.BuildConfig.DEBUG) return
        if (intent.action == "com.javboss.android.DEBUG_MARK_NONE") {
            com.javboss.android.core.log.AppLog.i("JavBossFill", "DEBUG_MARK_NONE broadcast")
            com.javboss.android.di.AppGraph.appScope.launch {
                val n = SubtitleFill.markAllManualNoSubtitle(context.applicationContext)
                com.javboss.android.core.log.AppLog.i("JavBossFill", "marked noNeed: $n")
            }
            return
        }
        if (intent.action == "com.javboss.android.DEBUG_RETRY") {
            val interval = intent.getLongExtra("interval", 2000L)
            com.javboss.android.core.log.AppLog.i("JavBossFill", "DEBUG_RETRY broadcast interval=$interval")
            runCatching {
                SubtitleFill.retryManual(context.applicationContext, intervalMs = interval)
            }.onFailure { com.javboss.android.core.log.AppLog.e("JavBossFill", "retry start failed", it) }
            return
        }
        if (intent.action == "com.javboss.android.DEBUG_FILL") {
            val limit = intent.getIntExtra("limit", 5)
            val interval = intent.getLongExtra("interval", 1500L)
            com.javboss.android.core.log.AppLog.i("JavBossFill", "DEBUG_FILL broadcast, limit=$limit interval=$interval")
            runCatching {
                SubtitleFill.start(context.applicationContext, limit = limit, intervalMs = interval)
            }.onFailure { com.javboss.android.core.log.AppLog.e("JavBossFill", "fill start failed", it) }
        }
    }
}
