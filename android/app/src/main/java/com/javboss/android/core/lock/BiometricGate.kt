package com.javboss.android.core.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 生物识别可用性判断 + 弹窗封装（失败回退系统锁屏凭据）。 */
object BiometricGate {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        // API < 30 不支持 BIOMETRIC_WEAK|DEVICE_CREDENTIAL 组合，只能用生物识别单体判断
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun hasBiometricOnly(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String = "解锁 JavBoss",
        subtitle: String = "使用指纹或锁屏密码继续",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("界面已关闭，请重试")
            return
        }
        runCatching { promptInternal(activity, title, subtitle, onSuccess, onError) }
            .onFailure { onError(it.message ?: "指纹验证失败") }
    }

    private fun promptInternal(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(AUTHENTICATORS)
        } else {
            // 老系统：生物识别 + 负按钮取消（不能带 DEVICE_CREDENTIAL）
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            builder.setNegativeButtonText("取消")
        }
        val info = builder.build()
        runCatching { prompt.authenticate(info) }
            .onFailure { onError(it.message ?: "无法启动指纹验证") }
    }
}
