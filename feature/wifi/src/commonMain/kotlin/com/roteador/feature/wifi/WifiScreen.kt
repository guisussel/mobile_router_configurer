package com.roteador.feature.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roteador.core.designsystem.RoteadorTheme
import com.roteador.core.designsystem.component.ErrorState
import com.roteador.core.designsystem.component.OpenRouterPageButton
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.LoadingState
import com.roteador.core.designsystem.component.MetricRow
import com.roteador.core.designsystem.component.SectionCard
import com.roteador.core.model.Quality
import com.roteador.core.model.WifiSecurity
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun WifiScreen(modifier: Modifier = Modifier, viewModel: WifiViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<WifiDraft?>(null) }

    when {
        state.unsupportedUrl != null -> Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Wi-Fi") {
                Text(
                    "Este roteador ainda não tem driver no app, então nome, senha, canal e WPS só podem ser trocados pela página dele.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OpenRouterPageButton(state.unsupportedUrl!!)
            }
        }
        state.loading -> Box(modifier) { LoadingState() }
        state.drafts.isEmpty() -> Box(modifier) { ErrorState(state.error ?: "Sem dados de Wi-Fi.", onRetry = viewModel::load) }
        else -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { InlineMessage(it, Quality.Good) }
            state.error?.let { InlineMessage(it, Quality.Critical) }
            state.drafts.forEach { draft ->
                BandCard(
                    draft = draft,
                    applying = state.applying == draft.original.band,
                    busy = state.applying != null,
                    onEdit = { transform -> viewModel.edit(draft.original.band, transform) },
                    onDiscard = { viewModel.discard(draft.original.band) },
                    onApply = { confirming = draft },
                )
            }
        }
    }

    confirming?.let { draft ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Aplicar na rede ${draft.original.band.label}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    draft.summary.forEach { Text("• $it") }
                    Text(
                        "\nO Wi-Fi reinicia e os aparelhos ficam alguns segundos sem conexão." +
                            if (draft.change?.touchesCredentials == true) " Quem usa esta rede vai precisar da senha nova." else "",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.apply(draft.original.band)
                    confirming = null
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BandCard(
    draft: WifiDraft,
    applying: Boolean,
    busy: Boolean,
    onEdit: ((WifiDraft) -> WifiDraft) -> Unit,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
) {
    val config = draft.original
    var showPassword by remember { mutableStateOf(false) }
    SectionCard("Wi-Fi ${config.band.label}", trailing = {
        Switch(checked = draft.enabled, onCheckedChange = { v -> onEdit { it.copy(enabled = v) } }, enabled = !busy)
    }) {
        if (config.credentialsEditable) {
            OutlinedTextField(
                value = draft.ssid,
                onValueChange = { v -> onEdit { it.copy(ssid = v) } },
                label = { Text("Nome da rede") },
                isError = draft.ssidError != null,
                supportingText = draft.ssidError?.let { { Text(it) } },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.password,
                onValueChange = { v -> onEdit { it.copy(password = v) } },
                label = { Text("Senha") },
                isError = draft.passwordError != null,
                supportingText = draft.passwordError?.let { { Text(it) } },
                singleLine = true,
                enabled = !busy && draft.security != WifiSecurity.Open,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Ocultar" else "Mostrar") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Choice(
                label = "Segurança",
                options = listOf(WifiSecurity.Wpa2, WifiSecurity.Wpa2Wpa3, WifiSecurity.Wpa3),
                selected = draft.security,
                text = { it.label },
                enabled = !busy,
                onSelect = { v -> onEdit { it.copy(security = v) } },
            )
        } else {
            MetricRow("Nome da rede", config.ssid)
            Text(
                "Neste roteador, nome e senha desta rede acompanham a rede 2,4 GHz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Choice(
            label = "Canal" + (config.currentChannel?.let { " (em uso: $it)" } ?: ""),
            options = listOf<Int?>(null) + config.supportedChannels,
            selected = draft.channel,
            text = { it?.toString() ?: "Automático" },
            enabled = !busy,
            onSelect = { v -> onEdit { it.copy(channel = v) } },
        )
        Choice(
            label = "Largura do canal",
            options = config.supportedWidths,
            selected = draft.width,
            text = { it.label },
            enabled = !busy,
            onSelect = { v -> onEdit { it.copy(width = v) } },
        )
        WpsCard(
            enabled = draft.wpsEnabled,
            hidden = draft.hidden,
            busy = busy,
            onChange = { v -> onEdit { it.copy(wpsEnabled = v) } },
        )
        Toggle("Ocultar o nome da rede", draft.hidden, enabled = !busy) { v -> onEdit { it.copy(hidden = v) } }
        Text("Potência: ${draft.txPowerPercent}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = draft.txPowerPercent.toFloat(),
            onValueChange = { v -> onEdit { it.copy(txPowerPercent = (v / 10).roundToInt() * 10) } },
            valueRange = 10f..100f,
            steps = 8,
            enabled = !busy,
        )

        if (draft.change != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Desfazer") }
                Button(onClick = onApply, enabled = !busy && draft.valid, modifier = Modifier.weight(1f)) {
                    if (applying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Aplicar")
                }
            }
        }
    }
}

/** WPS gets its own highlighted block: it is a security choice, not a detail. */
@Composable
private fun WpsCard(enabled: Boolean, hidden: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    val color = if (enabled) RoteadorTheme.status.warning else RoteadorTheme.status.good
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WPS", style = MaterialTheme.typography.titleSmall)
                Text(if (enabled) "Ligado" else "Desligado", style = MaterialTheme.typography.labelMedium, color = color)
            }
            Switch(checked = enabled, onCheckedChange = onChange, enabled = !busy && !hidden)
        }
        Text(
            when {
                hidden -> "Indisponível com a rede oculta."
                enabled -> "Permite conectar aparelhos apertando o botão WPS do roteador, sem senha. É uma brecha conhecida: desligar é mais seguro."
                else -> "Aparelhos novos entram só com a senha. O roteador não aceita pareamento WPS pelo app, só pelo botão físico."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<T>,
    selected: T,
    text: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled) { Text(text(selected)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                        onSelect(option)
                        open = false
                    })
                }
            }
        }
    }
}

