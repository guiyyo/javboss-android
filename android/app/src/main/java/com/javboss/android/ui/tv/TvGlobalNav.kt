package com.javboss.android.ui.tv

import kotlinx.coroutines.flow.MutableSharedFlow

/** TV 端全局按键事件（遥控器“设置”键等由 Activity 转发到 Compose）。 */
object TvGlobalNav {

    /** 是否正在播放（播放中不触发超时锁屏） */
    val playing = kotlinx.coroutines.flow.MutableStateFlow(false)

    val openSettings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestOpenSettings() {
        openSettings.tryEmit(Unit)
    }
}