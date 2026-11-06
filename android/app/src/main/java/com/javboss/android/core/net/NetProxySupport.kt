package com.javboss.android.core.net

import com.javboss.android.di.AppGraph
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 网络代理统一入口：配置了「网络代理」后，所有外网下载都走它。 */
object NetProxySupport {

    suspend fun current(): java.net.Proxy? {
        val p = runCatching { AppGraph.settings.netProxy() }.getOrNull()
        com.javboss.android.core.log.AppLog.i(
            "JavBossProxy",
            "proxy resolved: " + (p?.let { it.type().name + " " + it.address() } ?: "direct"),
        )
        return p
    }

    /** 基于 ApiClient 的基础 client，按需挂上代理与超时。 */
    fun build(
        proxy: java.net.Proxy?,
        connectSeconds: Long = 10,
        readSeconds: Long = 60,
        callSeconds: Long = 600,
    ): OkHttpClient {
        val b = ApiClient.okHttp.newBuilder()
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .callTimeout(callSeconds, TimeUnit.SECONDS)
        if (proxy != null) b.proxy(proxy)
        return b.build()
    }
}
