package com.roteador.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roteador.core.data.ConnectionState
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.MetricRow
import com.roteador.core.designsystem.component.SectionCard
import com.roteador.core.model.Quality
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReboot by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(state.loggedOut) { if (state.loggedOut) onLoggedOut() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (state.connection as? ConnectionState.Connected)?.let { connected ->
            SectionCard("Roteador") {
                MetricRow("Modelo", "${connected.info.manufacturer} ${connected.info.model}")
                MetricRow("Firmware", connected.info.firmware)
                connected.info.hardware?.let { MetricRow("Hardware", it) }
                MetricRow("Endereço", connected.host)
                MetricRow("Usuário", connected.username)
            }
        }

        SectionCard("Reiniciar") {
            Text(
                "Ajuda quando o roteador está travado ou lento depois de muitos dias ligado. " +
                    "Não melhora sinal fraco nem o limite do plano. Internet, Wi-Fi e telefone caem por 2 a 3 minutos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (val phase = state.reboot) {
                RebootPhase.Idle -> Button(onClick = { confirmReboot = true }, modifier = Modifier.fillMaxWidth()) { Text("Reiniciar roteador") }
                RebootPhase.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is RebootPhase.Waiting -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Reiniciando… ${phase.elapsedSeconds} s. O celular pode trocar de rede nesse meio tempo.")
                }
                is RebootPhase.Back -> InlineMessage("O roteador voltou em ${phase.tookSeconds} s.", Quality.Good)
                is RebootPhase.Failed -> {
                    InlineMessage(phase.message, Quality.Critical)
                    OutlinedButton(onClick = { confirmReboot = true }) { Text("Tentar de novo") }
                }
            }
        }

        SectionCard("Sessão") {
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
        }
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Reiniciar o roteador?") },
            text = { Text("Todos os aparelhos ficam sem internet por 2 a 3 minutos.") },
            confirmButton = { TextButton(onClick = { confirmReboot = false; viewModel.reboot() }) { Text("Reiniciar") } },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancelar") } },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair do roteador?") },
            text = { Text("Você pode manter a senha salva para entrar de novo com um toque.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; viewModel.logout(forgetPassword = false) }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false; viewModel.logout(forgetPassword = true) }) { Text("Sair e esquecer a senha") } },
        )
    }
}
