package com.javboss.android.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.android.core.net.ApiResult
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

/** 设置页状态：服务器地址、退出登录、提示信息。（展示类配置项直接读写 ConfigRepository） */
class SettingsViewModel : ViewModel() {

    var serverUrl by mutableStateOf("")
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var loggingOut by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            serverUrl = AppGraph.settings.currentServerUrl()
        }
    }

    fun onServerUrlInput(value: String) {
        serverUrl = value
        message = null
    }

    fun saveServerUrl() {
        viewModelScope.launch {
            AppGraph.settings.setServerUrl(serverUrl.trim())
            message = "服务器地址已保存；如无法访问请重新登录"
            when (val result = AppGraph.auth.status()) {
                is ApiResult.Success ->
                    if (!result.data.authenticated) message = "地址已保存，当前会话在该地址无效，请重新登录"
                is ApiResult.Failure -> message = "地址已保存，但连接失败：${result.message}"
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        if (loggingOut) return
        loggingOut = true
        viewModelScope.launch {
            AppGraph.auth.logout()
            AppGraph.cookieStore.clear()
            loggingOut = false
            onDone()
        }
    }
}
