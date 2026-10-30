package com.javboss.android.core.net

import android.content.Context
import com.javboss.android.BuildConfig
import com.javboss.android.data.model.ApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 统一的接口结果包装，避免 UI 层直接处理异常。 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val status: Int, val message: String, val cause: Throwable? = null) : ApiResult<Nothing>()

    fun getOrNull(): T? = (this as? Success)?.data
}

class ApiException(val status: Int, val messageZh: String, val messageEn: String) :
    IOException(messageZh.ifBlank { messageEn })

/**
 * 网络基座。
 *
 * 关键约束（来自服务端实现）：
 *  - 只使用 Cookie 会话鉴权，**绝不发送 Authorization 头**（非扩展接口会直接 403）；
 *  - 非 GET 请求**不要**带 Origin / Sec-Fetch-Site（不匹配即 403）；
 *  - 所有图片/视频流请求必须复用同一个 OkHttpClient 才能带上 Cookie。
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 120L
    private const val WRITE_TIMEOUT_SECONDS = 120L

    lateinit var okHttp: OkHttpClient
        private set

    private var cachedBaseUrl: String? = null
    private var cachedRetrofit: Retrofit? = null

    private val contentType = "application/json".toMediaType()

    fun init(context: Context, cookieStore: CookieStore) {
        if (::okHttp.isInitialized) return
        val builder = OkHttpClient.Builder()
            .cookieJar(SessionCookieJar(cookieStore))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .addInterceptor { chain ->
                val request: Request = chain.request().newBuilder()
                    .header("User-Agent", "JavBossAndroid/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        okHttp = builder.build()
    }

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) url = "http://192.168.1.1:8655"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url = "$url/"
        return url
    }

    fun <T> createApi(baseUrl: String, service: Class<T>): T {
        val normalized = normalizeBaseUrl(baseUrl)
        val retrofit = if (cachedBaseUrl == normalized && cachedRetrofit != null) {
            cachedRetrofit!!
        } else {
            val created = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
            cachedBaseUrl = normalized
            cachedRetrofit = created
            created
        }
        return retrofit.create(service)
    }

    /** 把服务端相对路径（如 `/videos/12/stream`）拼成绝对 URL。 */
    fun absoluteUrl(baseUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = normalizeBaseUrl(baseUrl)
        return if (path.startsWith("/")) base.trimEnd('/') + path else base + path
    }

    fun parseErrorMessage(body: String?): Pair<String, String> {
        if (body.isNullOrBlank()) return "" to ""
        return runCatching {
            val err = json.decodeFromString(ApiError.serializer(), body)
            err.messageZh to err.messageEn
        }.getOrElse { body.take(200) to "" }
    }
}

/** 统一执行一次接口调用并把异常翻译成 [ApiResult]。 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: HttpException) {
    val (zh, en) = ApiClient.parseErrorMessage(e.response()?.errorBody()?.string())
    ApiResult.Failure(e.code(), zh.ifBlank { en.ifBlank { "HTTP ${e.code()}" } }, e)
} catch (e: ApiException) {
    ApiResult.Failure(e.status, e.messageZh.ifBlank { e.messageEn }, e)
} catch (e: IOException) {
    ApiResult.Failure(0, "网络连接失败：${e.message ?: "请检查服务器地址与网络"}", e)
} catch (e: Exception) {
    ApiResult.Failure(0, e.message ?: "未知错误", e)
}

/** 把 map 编码为服务端 PATCH /config 需要的 JSON（值可能是 bool/number/string）。 */
fun Map<String, JsonElement>.toJsonElement(): JsonElement =
    kotlinx.serialization.json.JsonObject(this)
