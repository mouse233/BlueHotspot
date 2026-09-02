package io.github.mouse233.bluehotspot.server.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeBackend
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeState
import io.github.mouse233.bluehotspot.server.privilege.PrivilegeUiState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

@Composable
fun PrivilegeSettingsScreen(
    state: PrivilegeUiState,
    onSelectBackend: (PrivilegeBackend) -> Unit,
    onRequestAuthorization: (PrivilegeBackend) -> Unit,
    onRefresh: (PrivilegeBackend) -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("权限设置") }) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onContinue, enabled = state.canContinue) {
                    Text("继续")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "选择 App 获取高级系统权限的方式。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "请选择一种方式并完成可用性检测后继续。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(listOf(PrivilegeBackend.SHIZUKU, PrivilegeBackend.ROOT)) { backend ->
                val backendState = when (backend) {
                    PrivilegeBackend.SHIZUKU -> state.shizukuState
                    PrivilegeBackend.ROOT -> state.rootState
                }
                PrivilegeCard(
                    backend = backend,
                    selected = state.selectedBackend == backend,
                    state = backendState,
                    onSelect = { onSelectBackend(backend) },
                    onRequest = { onRequestAuthorization(backend) },
                    onRefresh = { onRefresh(backend) },
                )
            }
        }
    }
}

@Composable
private fun PrivilegeCard(
    backend: PrivilegeBackend,
    selected: Boolean,
    state: PrivilegeState,
    onSelect: () -> Unit,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val action = state.action()

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = backend.title(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (backend == PrivilegeBackend.SHIZUKU) {
                        Text(
                            text = "推荐",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = backend.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = state.statusColor(),
                    fontWeight = FontWeight.Medium,
                )
                if (action != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isBusy()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            androidx.compose.material3.TextButton(
                                onClick = if (action.isRequest) onRequest else onRefresh,
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                } else if (state.isBusy()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private data class PrivilegeAction(val label: String, val isRequest: Boolean)

private fun PrivilegeState.action(): PrivilegeAction? = when (this) {
    PrivilegeState.Requesting,
    PrivilegeState.Checking,
    -> null
    PrivilegeState.NotAuthorized,
    PrivilegeState.Denied,
    PrivilegeState.NotChecked,
    -> PrivilegeAction("请求权限", isRequest = true)
    PrivilegeState.ShizukuNotRunning,
    PrivilegeState.RootUnavailable,
    PrivilegeState.Available,
    is PrivilegeState.Failed,
    -> PrivilegeAction("重新测试", isRequest = false)
}

private fun PrivilegeState.isBusy(): Boolean =
    this == PrivilegeState.Requesting || this == PrivilegeState.Checking

private fun PrivilegeState.label(): String = when (this) {
    PrivilegeState.NotChecked -> "尚未检测"
    PrivilegeState.ShizukuNotRunning -> "Shizuku 未运行"
    PrivilegeState.RootUnavailable -> "无可用 Root"
    PrivilegeState.NotAuthorized -> "尚未授权"
    PrivilegeState.Requesting -> "正在请求权限"
    PrivilegeState.Checking -> "正在检测"
    PrivilegeState.Available -> "已授权并可用"
    PrivilegeState.Denied -> "权限被拒绝"
    is PrivilegeState.Failed -> "检测失败：$reason"
}

@Composable
private fun PrivilegeState.statusColor(): Color = when (this) {
    PrivilegeState.Available -> MaterialTheme.colorScheme.primary
    PrivilegeState.Denied,
    is PrivilegeState.Failed,
    -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun PrivilegeBackend.title(): String = when (this) {
    PrivilegeBackend.SHIZUKU -> "Shizuku"
    PrivilegeBackend.ROOT -> "Root"
}

private fun PrivilegeBackend.description(): String = when (this) {
    PrivilegeBackend.SHIZUKU -> "通过 Shizuku 获取所需系统权限，无需直接向 App 授予 Root 权限。"
    PrivilegeBackend.ROOT -> "直接通过 su 获取超级用户权限，适用于已经 Root 的设备。"
}
