package com.javboss.android.di

import android.content.Context
import com.javboss.android.core.net.ApiClient
import com.javboss.android.core.net.CookieStore
import com.javboss.android.data.repo.AuthRepository
import com.javboss.android.data.repo.ConfigRepository
import com.javboss.android.data.repo.JavRepository
import com.javboss.android.data.repo.ScrapeRepository
import com.javboss.android.data.repo.ServerRepository
import com.javboss.android.data.repo.TagRepository
import com.javboss.android.data.repo.VideoRepository
import com.javboss.android.data.settings.SettingsStore

/**
 * 极简服务定位器：聚合应用级单例（设置、会话、仓库）。
 */
object AppGraph {
    private lateinit var appContext: Context

    val context: Context get() = appContext

    lateinit var settings: SettingsStore
        private set
    lateinit var cookieStore: CookieStore
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var jav: JavRepository
        private set
    lateinit var video: VideoRepository
        private set
    lateinit var config: ConfigRepository
        private set
    lateinit var server: ServerRepository
        private set
    lateinit var tags: TagRepository
        private set
    lateinit var scrape: ScrapeRepository
        private set
    lateinit var lock: com.javboss.android.core.lock.AppLockManager
        private set

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        settings = SettingsStore(appContext)
        cookieStore = CookieStore(appContext)
        ApiClient.init(appContext, cookieStore)
        auth = AuthRepository(settings)
        jav = JavRepository(settings)
        video = VideoRepository(settings)
        config = ConfigRepository(settings)
        server = ServerRepository(settings)
        tags = TagRepository(settings)
        scrape = ScrapeRepository(settings)
        lock = com.javboss.android.core.lock.AppLockManager(appContext)
        lock.start()
    }
}
