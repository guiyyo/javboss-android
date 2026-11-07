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

    /** 刚在本会话里设置/修改了 TV 密码：置 true 后主界面直接把当前会话视为已解锁，
     *  否则用户刚设完密码就被立刻要求再输一遍（0.36.14 修）。 */
    val pinJustSet = kotlinx.coroutines.flow.MutableStateFlow(false)
}