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
        settings.setSessionCookie(null)
        Unit
    }
}

/** 便于在非挂起上下文获取 URL 的小工具（仅用于构造 Retrofit）。 */
internal fun SettingsStore.currentServerUrlBlocking(): String =
    kotlinx.coroutines.runBlocking { currentServerUrl() }
