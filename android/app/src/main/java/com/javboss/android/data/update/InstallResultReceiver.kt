package com.javboss.android.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** 接收 PackageInstaller 会话的安装结果（只用于记录日志/状态，不弹任何 UI）。 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) ?: -999
        val msg = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        android.util.Log.i("JavBossUpdate", "install session result: status=$status msg=$msg")
        InstallSessionBridge.status.value = status

        // 关键：部分系统（含 MIUI TV）不会自动弹确认框，需要由接收方拉起 EXTRA_INTENT
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null && context != null) {
                runCatching {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                    android.util.Log.i("JavBossUpdate", "install confirm activity launched")
                }.onFailure {
                    android.util.Log.w("JavBossUpdate", "launch confirm failed: ${it.message}")
                }
            } else {
                android.util.Log.w("JavBossUpdate", "PENDING_USER_ACTION but no EXTRA_INTENT")
            }
        }
    }
}
