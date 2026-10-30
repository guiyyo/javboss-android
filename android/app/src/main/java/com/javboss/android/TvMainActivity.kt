package com.javboss.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.javboss.android.di.AppGraph
import com.javboss.android.ui.theme.JavBossTheme
import com.javboss.android.ui.tv.TvAppRoot

/** Android TV 入口（遥控器交互）：桌面上与手机端并存。 */
class TvMainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // 遥控器“设置”键 → 应用设置页（在 Compose 之前拦截）
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            android.util.Log.d("TvKeys", "key=${event.keyCode}")
            if (event.keyCode == android.view.KeyEvent.KEYCODE_SETTINGS ||
                event.keyCode == android.view.KeyEvent.KEYCODE_MENU
            ) {
                com.javboss.android.ui.tv.TvGlobalNav.requestOpenSettings()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TV 端不启用 FLAG_SECURE（用户要求，便于截屏/分享）
        runCatching { AppGraph.init(applicationContext) }
        enableEdgeToEdge()
        val mode = runCatching {
            kotlinx.coroutines.runBlocking { AppGraph.settings.uiPrefs.first { true }.uiMode }
        }.getOrDefault("auto")
        setContent {
            JavBossTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 用户在本机设置里选了「手机 / 平板」模式时，即使从 TV 入口进来也渲染对应布局
                    if (mode == "phone" || mode == "tablet") {
                        com.javboss.android.ui.nav.AppRoot()
                    } else {
                        TvAppRoot()
                    }
                }
            }
        }
    }
}