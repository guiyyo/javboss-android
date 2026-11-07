package com.javboss.android.data.repo

import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.ApiResult
import com.javboss.android.core.net.apiCall
import com.javboss.android.data.model.AuthStatus
import com.javboss.android.data.remote.JavBossApi
import com.javboss.android.data.remote.ChangePasswordRequest
import com.javboss.android.data.remote.LoginRequest
import com.javboss.android.data.model.ExtensionToken
import com.javboss.android.data.settings.SettingsStore

/**
 * 鉴权仓库：Client 模式语义 —— 直连远端服务，用密码换 Cookie 会话。
 * 会话 Cookie 由 [com.javboss.android.core.net.SessionCookieJar] 持久化，App 重启仍有效。
 */
class AuthRepository(private val settings: SettingsStore) {

    private fun api(): JavBossApi =
        ApiClient.createApi(settings.currentServerUrlBlocking(), JavBossApi::class.java)

    suspend fun status(): ApiResult<AuthStatus> = apiCall { api().authStatus() }

    /** 用指定地址探测服务端是否可达（不保存地址、不影响现有会话） */
    suspend fun health(serverUrl: String): ApiResult<com.javboss.android.data.model.StatusResponse> =
        apiCall { ApiClient.createApi(serverUrl, JavBossApi::class.java).health() }

    suspend fun login(serverUrl: String, password: String): ApiResult<AuthStatus> {
        settings.setServerUrl(serverUrl)
        return apiCall { api().login(LoginRequest(password)) }
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> = apiCall {
        api().changePassword(ChangePasswordRequest(current, new))
        Unit
    }

    suspend fun extensionTokens(): ApiResult<List<ExtensionToken>> = apiCall { api().extensionTokens().items }

    suspend fun logout(): ApiResult<Unit> = apiCall {
        runCatching { api().logout() }
        logoutLocally()
        Unit
    }

    /**
     * 本地登出（不发网络请求）：清会话 Cookie + Cookie 存储 + 运行日志 + 崩溃记录。
     *
     * 安全审计 A-2：「TV 端退出登录无效」—— 原来 TvApp 只是把 UI 切回登录页，
     * Cookie 还在，冷启动又被 `authStatus()` 判成已登录；手机端靠 SettingsViewModel 额外清，
     * 两端行为不一致。现在统一走这里。
     * 另外退出登录后不清日志的话，同进程内换账号导出的诊断报告仍带前一个账号的番号/标题/URL。
     */
    suspend fun logoutLocally() {
        runCatching { settings.setSessionCookie(null) }
        runCatching { com.javboss.android.di.AppGraph.cookieStore.clear() }
        runCatching { com.javboss.android.core.log.AppLog.clear() }
        runCatching {
            com.javboss.android.core.log.CrashLogger.clear(com.javboss.android.di.AppGraph.context)
        }
        // 注意：不清 SubtitleFill 的「待复核」列表 —— 那是用户数据，重新登录后应该还在
    }
}

/** 便于在非挂起上下文获取 URL 的小工具（仅用于构造 Retrofit）。 */
internal fun SettingsStore.currentServerUrlBlocking(): String =
    kotlinx.coroutines.runBlocking { currentServerUrl() }
