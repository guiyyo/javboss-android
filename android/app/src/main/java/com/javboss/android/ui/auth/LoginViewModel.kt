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

    /** 服务器地址：默认为空，由用户填写（本项目是自建服务端） */
    var serverUrl by mutableStateOf("")
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
    /** 「测试连接」进行中 */
    var testing by mutableStateOf(false)
        private set
    /** 测试结果（成功/失败文案） */
    var testResult by mutableStateOf<String?>(null)
        private set
    var testOk by mutableStateOf(false)
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
        testResult = null
    }

    /** 地址里是否显式写了端口（`host:port`） */
    val hasExplicitPort: Boolean
        get() {
            val s = serverUrl.trim()
            if (s.isEmpty()) return true
            val noScheme = s.substringAfter("://", s)
            return noScheme.substringBefore('/').contains(':')
        }

    /** 补上 JavBoss 默认端口 8655 */
    fun applyDefaultPort() {
        if (serverUrl.isBlank() || hasExplicitPort) return
        serverUrl = serverUrl.trim().trimEnd('/') + ":8655"
        error = null
        testResult = null
    }

    /** 测试连接：只请求 /healthz，不保存地址、不换会话 */
    fun testConnection() {
        if (testing) return
        if (serverUrl.isBlank()) {
            error = "请先填写服务器地址"
            return
        }
        val candidate = if (hasExplicitPort) serverUrl.trim()
        else serverUrl.trim().trimEnd('/') + ":8655"
        testing = true
        testResult = null
        error = null
        viewModelScope.launch {
            when (val r = AppGraph.auth.health(candidate)) {
                is ApiResult.Success -> {
                    testOk = true
                    testResult = "连接成功：$candidate"
                }
                is ApiResult.Failure -> {
                    testOk = false
                    testResult = "无法连接到 " + candidate + "：" + r.message +
                        "（请确认服务端已启动、手机与服务器在同一网络、端口是否为 8655）"
                }
            }
            testing = false
        }
    }

    fun onPasswordChange(value: String) {
        password = value
        error = null
    }

    fun submit() {
        if (loading) return
        if (serverUrl.isBlank()) {
            error = "请先填写服务器地址，例如 http://192.168.1.1:8655"
            return
        }
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
