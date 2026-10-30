package com.javboss.android.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.javboss.android.ui.auth.LoginViewModel

/** TV 登录页：遥控器点进输入框后由电视输入法输入。 */
@Composable
fun TvLoginScreen(
    vm: LoginViewModel,
    onLoggedIn: () -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(620.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("JavBoss TV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "输入服务器地址与访问密钥后连接",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TvInputField(
                value = vm.serverUrl,
                onValueChange = vm::onServerUrlChange,
                placeholder = "服务器地址",
                label = "服务器地址",
                modifier = Modifier.fillMaxWidth(),
            )
            TvInputField(
                value = vm.password,
                onValueChange = vm::onPasswordChange,
                placeholder = "访问密钥",
                label = "访问密钥",
                password = !showKey,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvFocusable(onClick = { showKey = !showKey }, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (showKey) "隐藏密钥" else "显示密钥",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TvFocusable(onClick = { vm.submit() }, shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.loading) {
                            CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                            Text("  连接中…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("连接", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            androidx.compose.runtime.LaunchedEffect(vm.loggedIn) {
                if (vm.loggedIn) onLoggedIn()
            }

            Text(
                "v${com.javboss.android.BuildConfig.VERSION_NAME} · 遥控器：↑↓ 选择，OK 确认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}