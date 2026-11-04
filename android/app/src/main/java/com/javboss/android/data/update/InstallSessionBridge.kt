package com.javboss.android.data.update

import kotlinx.coroutines.flow.MutableStateFlow

/** PackageInstaller 会话应答的桥（接收器写、安装流程读）。 */
object InstallSessionBridge {
    /** 最近一次安装会话状态：null=尚未应答 */
    val status = MutableStateFlow<Int?>(null)

    fun reset() {
        status.value = null
    }
}
