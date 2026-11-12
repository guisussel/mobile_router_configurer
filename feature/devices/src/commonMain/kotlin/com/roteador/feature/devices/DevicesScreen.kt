package com.roteador.feature.devices

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roteador.core.designsystem.Format
import com.roteador.core.designsystem.RoteadorIcons
import com.roteador.core.designsystem.RoteadorTheme
import com.roteador.core.designsystem.component.ErrorState
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.LoadingState
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceControls
import com.roteador.core.model.DeviceLink
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DevicesScreen(modifier: Modifier = Modifier, viewModel: DevicesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }

    when {
        state.loading -> Box(modifier) { LoadingState(text = "Procurando aparelhos…") }
        state.groups.isEmpty() && state.error != null -> Box(modifier) { ErrorState(state.error!!) }
        else -> LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Text(
                    "${state.onlineCount} conectados agora",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "Toque e segure um aparelho para ver as opções.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                state.error?.let { InlineMessage("Última leitura falhou: $it", modifier = Modifier.padding(16.dp)) }
            }
            state.groups.forEach { group ->
                item(key = group.title) {
                    Text(
                        "${group.title} (${group.devices.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(group.devices, key = { group.title + it.id }) { device ->
                    DeviceRow(device, state.controls, onOpen = { selected = device.id })
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }

    val device = state.groups.flatMap { it.devices }.firstOrNull { it.id == selected }
    if (device != null) {
        DeviceActionsSheet(
            device = device,
            state = state,
            viewModel = viewModel,
            onDismiss = {
                selected = null
                viewModel.clearMessage()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(device: ConnectedDevice, controls: DeviceControls?, onOpen: () -> Unit) {
    val details = buildList {
        device.ipv4?.let(::add)
        if (device.name != null) device.mac?.let { add(it.uppercase()) }
        (device.link as? DeviceLink.Wired)?.port?.let { add("LAN $it") }
    }.joinToString(" · ")
    val badges = buildList {
        if (controls?.reservationFor(device.mac) != null) add("IP fixo")
        if (controls?.blockFor(device.ipv4) != null) add("internet bloqueada")
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onOpen),
        leadingContent = {
            Icon(
                if (device.link is DeviceLink.Wifi) RoteadorIcons.Wifi else RoteadorIcons.Devices,
                contentDescription = null,
                tint = if (device.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        headlineContent = { Text(device.name ?: "Sem nome · ${device.mac?.uppercase() ?: device.ipv4.orEmpty()}") },
        supportingContent = {
            Text(
                details + if (badges.isNotEmpty()) "\n" + badges.joinToString(" · ") else "",
                color = if (controls?.blockFor(device.ipv4) != null) RoteadorTheme.status.critical else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = device.connectedFor?.let { { Text(Format.duration(it), style = MaterialTheme.typography.labelMedium) } },
    )
}
