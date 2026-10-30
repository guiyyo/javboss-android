package com.javboss.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.javboss.android.core.net.ApiClient
import com.javboss.android.di.AppGraph

/**
 * 应用入口。
 *
 * 关键点：Coil 默认使用自己的 OkHttpClient，**不会带上会话 Cookie**，
 * 导致封面/缩略图/截图全部 401（表现为「所有图片都空白」）。
 * 这里把 Coil 的 OkHttp 客户端换成应用的共享客户端。
 */
class JavBossApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        com.javboss.android.core.log.CrashLogger.install(this)
        AppGraph.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { ApiClient.okHttp }
        .crossfade(true)
        .respectCacheHeaders(false)
        // 老旧电视盒子 / 低端 GPU：避免硬件位图走 gralloc 大纹理分配（会 native 崩溃）
        .allowHardware(false)
        .build()
}
