package com.javboss.android

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.javboss.android.ui.common.isTvDevice
import com.javboss.android.ui.nav.AppRoot
import com.javboss.android.ui.theme.JavBossTheme

class MainActivity : FragmentActivity() {

    /** 按「隐私模式」设置决定是否禁止截屏 / 任务预览 */
    private fun applyPrivacyMode() {
        // 调试包不启用 FLAG_SECURE，便于截图/录屏验证 UI（正式包仍按设置生效）
        if (BuildConfig.DEBUG) return
        val enabled = runCatching {
            com.javboss.android.di.AppGraph.settings.uiPrefsSnapshot().privacyMode
        }.getOrDefault(true)
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程被系统回收后重建时兜底初始化（init 内部幂等）
        runCatching { com.javboss.android.di.AppGraph.init(applicationContext) }

        // 「自动」模式在电视设备上直接切换到 TV 版页面（否则会被当成大屏平板）
        val mode = runCatching {
            com.javboss.android.di.AppGraph.settings.uiPrefsSnapshot().uiMode
        }.getOrDefault("auto")
        if ((mode.isBlank() || mode == "auto") && this.isTvDevice()) {
            startActivity(android.content.Intent(this, TvMainActivity::class.java))
            finish()
            return
        }

        applyPrivacyMode()
        com.javboss.android.core.task.NotificationPermission.ensure(this)
        enableEdgeToEdge()
        setContent {
            JavBossTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}
