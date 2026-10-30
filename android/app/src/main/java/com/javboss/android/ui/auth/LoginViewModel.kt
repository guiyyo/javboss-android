package com.javboss.android.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.settings.SettingsStore
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    var serverUrl by mutableStateOf(SettingsStore.DEFAULT_SERVER_URL)
        private set
    var password by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    /** 首次会话检测是否完成（TV 登录页据此区分「检测中」与「未登录」） */
    var checking by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            val saved = AppGraph.settings.currentServerUrl()
            if (saved.isNotBlank()) serverUrl = saved
            // 已有有效会话则直接进入
            val status = AppGraph.auth.status()
            if (status is ApiResult.Success && status.data.authenticated) loggedIn = true
            checking = false
        }
    }

    fun onServerUrlChange(value: String) {
        serverUrl = value
        error = null
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun submit() {
        if (loading) return
        if (password.isBlank()) {
            error = "请输入访问密钥"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            when (val result = AppGraph.auth.login(serverUrl.trim(), password)) {
                is ApiResult.Success -> {
                    if (result.data.authenticated) loggedIn = true else error = "登录失败：服务器未返回有效会话"
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun markLoggedOut() {
        loggedIn = false
        password = ""
    }
}
