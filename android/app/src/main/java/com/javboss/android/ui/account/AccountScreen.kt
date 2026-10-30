package com.javboss.android.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.android.core.net.ApiResult
import com.javboss.android.data.model.ExtensionToken
import com.javboss.android.di.AppGraph
import kotlinx.coroutines.launch

class AccountViewModel : ViewModel() {
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var tokens by mutableStateOf<List<ExtensionToken>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            when (val result = AppGraph.auth.extensionTokens()) {
                is ApiResult.Success -> tokens = result.data
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun submitPassword() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val result = AppGraph.auth.changePassword(currentPassword, newPassword)) {
                is ApiResult.Success -> {
                    message = "密码已修改，请在其他设备重新登录"
                    currentPassword = ""
                    newPassword = ""
                }
                is ApiResult.Failure -> message = result.message
            }
            busy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, vm: AccountViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("修改访问密码", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = vm.currentPassword,
                onValueChange = { vm.currentPassword = it },
                label = { Text("当前密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.newPassword,
                onValueChange = { vm.newPassword = it },
                label = { Text("新密码（6-20 字符）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::submitPassword,
                enabled = !vm.busy && vm.currentPassword.isNotBlank() && vm.newPassword.length >= 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.busy) "提交中…" else "修改密码") }

            vm.message?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("浏览器扩展令牌（只读）", style = MaterialTheme.typography.titleSmall)
            Text(
                "令牌仅能访问扩展接口（下载、状态、归属查询），不用于本 App。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.tokens.isEmpty()) {
                Text("暂无令牌", style = MaterialTheme.typography.bodySmall)
            } else {
                vm.tokens.forEach { token ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(token.name.ifBlank { "未命名" }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (token.expiresAt.isBlank()) "长期" else token.expiresAt.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "创建 ${token.createdAt.take(10)}" +
                                    (token.lastUsedAt?.let { " · 最近使用 ${it.take(10)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
