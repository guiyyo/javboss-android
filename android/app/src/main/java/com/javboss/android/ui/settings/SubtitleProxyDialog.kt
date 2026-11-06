package com.javboss.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javboss.android.data.settings.UiPrefs
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

/**
 * 「字幕代理」弹窗：与 TV 端一致 —— 两个开关，打开才显示 IP/端口。
 *
 * ① 网络代理：让字幕搜索/检查更新走本机代理（Clash 等）
 * ② 字幕源反代：请求转发到自己搭的反向代理
 * 两个都关 = 直连。
 */
@Composable
fun SubtitleProxyDialog(
    prefs: UiPrefs,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var netEnabled by remember { mutableStateOf(prefs.netProxyHost.isNotBlank()) }
    var netHost by remember { mutableStateOf(prefs.netProxyHost) }
    var netPort by remember { mutableStateOf(prefs.netProxyPort.toString()) }
    var netSocks by remember { mutableStateOf(prefs.netProxyType.equals("socks", true)) }

    var revEnabled by remember { mutableStateOf(prefs.subtitleProxyHost.isNotBlank()) }
    var revHost by remember { mutableStateOf(prefs.subtitleProxyHost) }
    var revPort by remember { mutableStateOf(prefs.subtitleProxyPort.toString()) }
    var revHttps by remember { mutableStateOf(prefs.subtitleProxyHttps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字幕代理") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // ① 网络代理
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("网络代理", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = netEnabled, onCheckedChange = {
                        netEnabled = it
                        if (it) revEnabled = false   // 二选一：开网络代理就关反代
                    })
                }
                if (netEnabled) {
                    OutlinedTextField(
                        value = netHost,
                        onValueChange = { netHost = it },
                        label = { Text("IP / 主机") },
                        placeholder = { Text("192.168.1.1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = netPort,
                        onValueChange = { v -> netPort = v.filter { it.isDigit() }.take(5) },
                        label = { Text("端口") },
                        placeholder = { Text("7890") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !netSocks, onClick = { netSocks = false }, label = { Text("HTTP") })
                        FilterChip(selected = netSocks, onClick = { netSocks = true }, label = { Text("SOCKS5") })
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                // ② 字幕源反向代理
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("字幕源反代", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = revEnabled, onCheckedChange = {
                        revEnabled = it
                        if (it) netEnabled = false   // 二选一：开反代就关网络代理
                    })
                }
                if (revEnabled) {
                    OutlinedTextField(
                        value = revHost,
                        onValueChange = { revHost = it },
                        label = { Text("IP / 主机") },
                        placeholder = { Text("192.168.1.1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = revPort,
                        onValueChange = { v -> revPort = v.filter { it.isDigit() }.take(5) },
                        label = { Text("端口") },
                        placeholder = { Text("8899") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !revHttps, onClick = { revHttps = false }, label = { Text("HTTP") })
                        FilterChip(selected = revHttps, onClick = { revHttps = true }, label = { Text("HTTPS") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                scope.launch {
                    AppGraph.settings.setNetProxyHost(if (netEnabled) netHost.trim() else "")
                    AppGraph.settings.setNetProxyPort(netPort.toIntOrNull() ?: 7890)
                    AppGraph.settings.setNetProxyType(if (netSocks) "socks" else "http")
                    AppGraph.settings.setSubtitleProxyHost(if (revEnabled) revHost.trim() else "")
                    AppGraph.settings.setSubtitleProxyPort(revPort.toIntOrNull() ?: 8899)
                    AppGraph.settings.setSubtitleProxyHttps(revHttps)
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
