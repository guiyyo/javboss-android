package com.javboss.android.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** 是否电视设备（Android TV / 电视盒子 / 智能电视）。 */
fun Context.isTvDevice(): Boolean {
    val pm = packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
    if (pm.hasSystemFeature("android.hardware.type.television")) return true
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
