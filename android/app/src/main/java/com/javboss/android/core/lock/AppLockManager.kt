package com.javboss.android.core.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 应用锁状态机。
 *
 * 按需求只在两种情况触发：
 *  1) 冷启动（进程首次进入且开关开启）；
 *  2) 设备锁屏后重新进入（收到 ACTION_SCREEN_OFF 后回到前台）。
 * 后台超时不锁，播放中切后台也不锁（播放本身会在后台暂停，见播放器）。
 */
class AppLockManager(private val context: Context) {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** 设备是否已经锁屏（收到 SCREEN_OFF 后置 true，解锁成功或回到前台处理完置 false）。 */
    private var screenOffSinceEnter = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffSinceEnter = true
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                screenReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private var coldStartHandled = false

    /** 冷启动时调用：开关开启则上锁（同一进程只执行一次，避免切主题/模式时重复上锁）。 */
    suspend fun onColdStart() {
        if (coldStartHandled) return
        coldStartHandled = true
        val enabled = AppGraph.settings.uiPrefs.first().appLockEnabled
        _locked.value = enabled && BiometricGate.isAvailable(context)
    }

    /** 回到前台时调用：只有「设备锁屏过」才要求重新解锁。 */
    suspend fun onForeground() {
        if (!_locked.value && screenOffSinceEnter) {
            val enabled = AppGraph.settings.uiPrefs.first().appLockEnabled
            if (enabled && BiometricGate.isAvailable(context)) {
                _locked.value = true
            }
        }
        screenOffSinceEnter = false
    }

    fun unlock() {
        _locked.value = false
        screenOffSinceEnter = false
    }

    /** 打开/关闭开关后同步状态。 */
    fun syncWithPreference(enabled: Boolean) {
        if (!enabled) unlock()
    }
}
