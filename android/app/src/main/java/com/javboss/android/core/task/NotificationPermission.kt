package com.javboss.android.core.task

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Android 13+ 的 POST_NOTIFICATIONS 运行时授权。
 *
 * 之前只在 Manifest 里声明了权限，**从未在运行时申请** → 系统默认拒绝 →
 * TaskForegroundService 里的 areNotificationsEnabled() 为 false 直接 return，
 * 结果就是「批量补齐/扫描在跑，但通知栏什么都看不到」（不崩溃，纯功能缺失）。
 */
object NotificationPermission {

    fun ensure(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching {
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                com.javboss.android.core.log.AppLog.i("JavBossTask", "POST_NOTIFICATIONS granted=" + ok)
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }.onFailure {
            com.javboss.android.core.log.AppLog.w("JavBossTask", "request POST_NOTIFICATIONS failed: " + it.message)
        }
    }
}
