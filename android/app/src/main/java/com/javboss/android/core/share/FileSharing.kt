package com.javboss.android.core.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把应用内生成的文件（字幕 ZIP / 索引 JSON / 备份 JSON / 诊断日志）交给系统分享面板。
 *
 * 统一走 FileProvider + ACTION_SEND，文件先写到 cacheDir/export/（已在 file_paths.xml 暴露），
 * 这样「导出到文件夹」和「直接分享」是两条路，互不影响。
 */
object FileSharing {

    /** 分享目录（cache/export/），调用方把文件写进来即可。 */
    fun exportDir(context: Context): File =
        File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }

    /** @return true = 已成功拉起分享面板 */
    fun share(context: Context, file: File, mime: String, subject: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            com.javboss.android.core.log.AppLog.i("JavBossShare", "share " + file.name + " (" + file.length() + "B)")
            true
        }.getOrDefault(false)
    }

    /** 把文本写成文件再分享（JSON / TXT 都用它）。 */
    fun shareText(context: Context, fileName: String, text: String, mime: String, subject: String): Boolean =
        runCatching {
            val f = File(exportDir(context), fileName)
            f.writeText(text)
            share(context, f, mime, subject)
        }.getOrDefault(false)
}
