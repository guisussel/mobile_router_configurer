package com.roteador.feature.signal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roteador.core.designsystem.Format
import com.roteador.core.designsystem.RoteadorTheme
import com.roteador.core.designsystem.component.ChartSeries
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.LineChart
import com.roteador.core.designsystem.component.MetricRow
import com.roteador.core.designsystem.component.SectionCard
import com.roteador.core.designsystem.component.StatusPill
import com.roteador.core.model.Quality
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignalScreen(modifier: Modifier = Modifier, viewModel: SignalViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val link = state.link
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            link == null -> Text("Lendo o Wi-Fi do celular…")
            !link.connected -> InlineMessage("O celular não está em uma rede Wi-Fi.", Quality.Critical)
            link.limitedByPlatform -> InlineMessage(
                "O iOS não deixa apps lerem a força do sinal. No iPhone, esta tela mostra só o que o sistema libera.",
            )
            else -> {
                val rssi = link.rssiDbm
                val quality = link.signalQuality
                SectionCard("Sinal neste ponto", trailing = {
                    if (quality != null) StatusPill(quality, qualityLabel(quality))
                }) {
                    Text(
                        rssi?.let { Format.dbm(it) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = quality?.let { RoteadorTheme.status.of(it) } ?: MaterialTheme.colorScheme.onSurface,
                    )
                    LineChart(
                        series = listOf(ChartSeries(state.history.map { it.toDouble() }, MaterialTheme.colorScheme.primary)),
                        capacity = SignalUiState.HISTORY,
                        minValue = -90.0,
                        maxValue = -20.0,
                        height = 160.dp,
                    )
                    Text(
                        "Ande pela casa com esta tela aberta: o gráfico mostra os últimos 2 minutos. " +
                            "Acima de -60 dBm é ótimo; abaixo de -72 dBm a conexão fica lenta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.best != null) {
                        MetricRow("Melhor / pior", "${Format.dbm(state.best!!)} / ${Format.dbm(state.worst!!)}")
                    }
                }
                SectionCard("Conexão") {
                    link.ssid?.let { MetricRow("Rede", it) }
                    link.band?.let { MetricRow("Banda", it.label) }
                    link.frequencyMhz?.let { MetricRow("Frequência", "$it MHz") }
                    link.linkSpeedMbps?.let { MetricRow("Velocidade do link", "$it Mbit/s") }
                    link.gatewayIpv4?.let { MetricRow("Roteador", it) }
                    if (link.ssid == null) {
                        Text(
                            "O Android só mostra o nome da rede com permissão de localização; não é preciso para medir o sinal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun qualityLabel(quality: Quality) = when (quality) {
    Quality.Good -> "Ótimo"
    Quality.Warning -> "Razoável"
    Quality.Critical -> "Fraco"
}
