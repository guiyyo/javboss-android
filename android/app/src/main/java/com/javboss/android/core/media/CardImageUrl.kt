package com.javboss.android.core.media

/**
 * 视频类卡片的取图规则。
 *
 * 用户诉求（2026-11-07）：「只要能显示封面的地方，都优先封面」。
 * 视频截图（/videos/{id}/thumbnail）经常是糊的、或者根本没生成（灰块），
 * 而作品封面是高清图 —— 所以**只要该视频关联了番号，就用作品封面**，没有关联才回落到视频截图。
 *
 * 三端（手机 / 平板 / 电视）共用这一份规则，避免各处写死导致行为不一致。
 */
object CardImageUrl {

    fun javCover(baseUrl: String, code: String): String =
        baseUrl.trimEnd('/') + "/jav/" + code + "/cover"

    fun videoThumbnail(baseUrl: String, videoId: Long): String =
        baseUrl.trimEnd('/') + "/videos/" + videoId + "/thumbnail"

    /** 继续观看 / 观看记录 / 视频库等「视频卡片」：优先作品封面。 */
    fun forVideo(baseUrl: String, javCode: String?, videoId: Long): String =
        if (!javCode.isNullOrBlank()) javCover(baseUrl, javCode) else videoThumbnail(baseUrl, videoId)
}
