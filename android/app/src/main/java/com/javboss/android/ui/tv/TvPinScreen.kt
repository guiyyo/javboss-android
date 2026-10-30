package com.javboss.android.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** TV 密码锁：数字键盘输入，输满位数自动解锁；连续失败 5 次锁定 30 秒。 */
@Composable
fun TvPinScreen(onUnlocked: () -> Unit) {
    val prefs by AppGraph.settings.uiPrefs.collectAsState(initial = com.javboss.android.data.settings.UiPrefs())
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var fails by remember { mutableStateOf(0) }
    var lockRemainSec by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val targetLen = prefs.tvPinLength.takeIf { it in 4..6 } ?: 6

    // 锁定倒计时
    LaunchedEffect(lockRemainSec) {
        if (lockRemainSec > 0) {
            delay(1000)
            lockRemainSec -= 1
        }
    }

    fun submit() {
        if (busy || pin.isBlank() || lockRemainSec > 0) return
        busy = true
        scope.launch {
            val ok = runCatching { AppGraph.settings.verifyTvPin(pin) }.getOrDefault(false)
            busy = false
            if (ok) {
                onUnlocked()
            } else {
                pin = ""
                fails += 1
                if (fails >= 5) {
                    fails = 0
                    lockRemainSec = 30
                    error = "密码错误次数过多，请等待 30 秒"
                } else {
                    error = "密码不对，请重试（还可尝试 ${5 - fails} 次）"
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(460.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("JavBoss TV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "请输入密码解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvPinPad(
                pin = pin,
                onPinChange = { input ->
                    if (lockRemainSec > 0) return@TvPinPad
                    val next = input.filter { it.isDigit() }.take(6)
                    pin = next
                    error = null
                    // 输满位数自动尝试解锁
                    if (next.length == targetLen) submit()
                },
                actionLabel = if (busy) "..." else "解锁",
                onAction = { submit() },
                enabled = lockRemainSec == 0 && !busy,
            )
            when {
                lockRemainSec > 0 -> Text(
                    "已锁定，${lockRemainSec} 秒后可重试",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "在「设置 → 密码锁」里可修改或关闭",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}