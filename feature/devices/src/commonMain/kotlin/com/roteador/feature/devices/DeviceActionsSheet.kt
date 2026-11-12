package com.roteador.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roteador.core.designsystem.RoteadorTheme
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.MetricRow
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.Quality

/**
 * Everything the app can do with one device. Actions the connected router does
 * not support stay visible but disabled, with the reason, so it is clear what
 * exists and what is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceActionsSheet(
    device: ConnectedDevice,
    state: DevicesUiState,
    viewModel: DevicesViewModel,
    onDismiss: () -> Unit,
) {
    val reservation = state.controls?.reservationFor(device.mac)
    val block = state.controls?.blockFor(device.ipv4)
    val busy = state.action.busy
    var editingIp by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.displayName, style = MaterialTheme.typography.titleLarge)
                device.ipv4?.let { MetricRow("IP", it) }
                device.mac?.let { MetricRow("MAC", it.uppercase()) }
                MetricRow("Conexão", linkLabel(device.link))
                reservation?.let { MetricRow("IP fixo", it.ipv4) }
                if (block != null) MetricRow("Internet", "bloqueada", valueColor = RoteadorTheme.status.critical)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            state.action.message?.let {
                InlineMessage(it, if (state.action.failed) Quality.Critical else Quality.Good, Modifier.padding(16.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            CopyActions(device)

            val noMac = device.mac == null
            Action(
                title = if (reservation == null) "Reservar IP fixo" else "Mudar IP ou nome",
                detail = when {
                    !state.canReserve -> "Este roteador não permite pelo app."
                    noMac -> "O app não sabe o MAC deste aparelho."
                    reservation == null -> "O roteador sempre dará o mesmo IP a este aparelho."
                    else -> "Hoje reservado: ${reservation.ipv4}" + (reservation.name?.let { " ($it)" } ?: "")
                },
                enabled = state.canReserve && !noMac && !busy,
                onClick = { editingIp = true },
            )
            if (reservation != null) {
                Action(
                    title = "Remover IP fixo",
                    detail = "Volta a receber um IP qualquer do roteador.",
                    enabled = !busy && block == null,
                    onClick = { viewModel.removeReservation(device) },
                )
            }
            Action(
                title = if (block == null) "Bloquear internet" else "Liberar internet",
                detail = when {
                    !state.canBlock -> "Este roteador não permite pelo app."
                    device.ipv4 == null -> "O aparelho está sem IP."
                    block == null -> "Continua no Wi-Fi, mas sem acesso à internet. Reserva o IP atual se ainda não tiver."
                    else -> "Remove a regra de bloqueio do firewall."
                },
                enabled = state.canBlock && device.ipv4 != null && !busy,
                danger = block == null,
                onClick = { if (block == null) confirmBlock = true else viewModel.unblockInternet(device) },
            )
            Action(
                title = "Expulsar do Wi-Fi",
                detail = "Não disponível: o filtro de MAC deste roteador só funciona como lista de permitidos, " +
                    "e usá-lo derrubaria todos os outros aparelhos. Para tirar alguém de vez, troque a senha do Wi-Fi.",
                enabled = false,
                onClick = {},
            )
        }
    }

    if (editingIp) {
        ReservationDialog(
            device = device,
            initialName = reservation?.name ?: device.name.orEmpty(),
            initialIp = reservation?.ipv4 ?: device.ipv4.orEmpty(),
            rangeHint = state.controls?.dhcpRange?.let { "Entre ${it.start} e ${it.endInclusive}" },
            validate = { viewModel.validateReservation(device, it) },
            onDismiss = { editingIp = false },
            onSave = { name, ip ->
                editingIp = false
                viewModel.reserve(device, name, ip)
            },
        )
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Bloquear a internet de ${device.displayName}?") },
            text = { Text("O aparelho continua conectado ao Wi-Fi e à rede de casa, mas não acessa a internet até você liberar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBlock = false
                    viewModel.blockInternet(device)
                }) { Text("Bloquear") }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancelar") } },
        )
    }
}

@Suppress("DEPRECATION") // LocalClipboard needs platform ClipEntry builders; plain text is enough here.
@Composable
private fun CopyActions(device: ConnectedDevice) {
    val clipboard = LocalClipboardManager.current
    device.ipv4?.let { ip -> Action("Copiar IP", ip, enabled = true, onClick = { clipboard.setText(AnnotatedString(ip)) }) }
    device.mac?.let { mac -> Action("Copiar MAC", mac.uppercase(), enabled = true, onClick = { clipboard.setText(AnnotatedString(mac.uppercase())) }) }
}

@Composable
private fun Action(title: String, detail: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        danger -> RoteadorTheme.status.critical
        else -> MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = { Text(detail, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@Composable
private fun ReservationDialog(
    device: ConnectedDevice,
    initialName: String,
    initialIp: String,
    rangeHint: String?,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var ip by remember { mutableStateOf(initialIp) }
    val error = validate(ip)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("IP fixo de ${device.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filterNot { c -> c == '/' || c == '|' }.take(32) },
                    label = { Text("Nome") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("IP") },
                    isError = error != null,
                    supportingText = { Text(error ?: rangeHint.orEmpty()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    "O roteador passa a usar este IP quando o aparelho reconectar ao Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, ip) }, enabled = error == null) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun linkLabel(link: DeviceLink) = when (link) {
    is DeviceLink.Wifi -> "Wi-Fi ${link.band.label}"
    is DeviceLink.Wired -> "Cabo" + (link.port?.let { " (LAN $it)" } ?: "")
    DeviceLink.Offline -> "Desconectado"
    DeviceLink.Unknown -> "Na rede"
}
