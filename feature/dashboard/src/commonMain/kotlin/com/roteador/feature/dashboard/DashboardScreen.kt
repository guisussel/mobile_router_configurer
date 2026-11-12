package com.roteador.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roteador.core.designsystem.Format
import com.roteador.core.designsystem.RoteadorTheme
import com.roteador.core.designsystem.component.ChartSeries
import com.roteador.core.designsystem.component.ChartTick
import com.roteador.core.designsystem.component.OpenRouterPageButton
import com.roteador.core.designsystem.component.InlineMessage
import com.roteador.core.designsystem.component.LineChart
import com.roteador.core.designsystem.component.LoadingState
import com.roteador.core.designsystem.component.MetricRow
import com.roteador.core.designsystem.component.RangeGauge
import com.roteador.core.designsystem.component.SectionCard
import com.roteador.core.designsystem.component.StatusPill
import com.roteador.core.model.OpticalPower
import com.roteador.core.model.Quality
import com.roteador.core.model.RouterStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status = state.status
    if (status == null) {
        Box(modifier) { LoadingState(text = state.statusError ?: "Lendo o roteador…") }
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HealthHeader(status, state.router?.let { "${it.manufacturer} ${it.model}" })
        state.statusError?.let { InlineMessage("Última leitura falhou: $it") }
        state.connection?.takeIf { it.limited }?.let { GenericModeCard(it.webUrl) }
        TrafficCard(state.traffic)
        FiberCard(status.optical)
        InternetCard(status)
        WifiCard(status)
    }
}

@Composable
private fun HealthHeader(status: RouterStatus, routerName: String?) {
    val (quality, text) = when {
        !status.internet.connected -> Quality.Critical to "Sem internet"
        status.optical?.rxQuality == Quality.Critical -> Quality.Critical to "Sinal da fibra fora da faixa"
        status.optical?.rxQuality == Quality.Warning -> Quality.Warning to "Funcionando, sinal da fibra no limite"
        else -> Quality.Good to "Tudo certo"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        routerName?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(text, style = MaterialTheme.typography.headlineSmall, color = RoteadorTheme.status.of(quality))
    }
}

@Composable
private fun TrafficCard(traffic: TrafficState) {
    val down = MaterialTheme.colorScheme.primary
    val up = RoteadorTheme.status.warning
    SectionCard("Tráfego agora") {
        val current = traffic.current
        if (current == null) {
            Text(
                traffic.error ?: "Medindo… o roteador atualiza os contadores a cada ~5 s.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RateLabel("Download", Format.bitrate(current.total.downloadBitsPerSecond), down)
                RateLabel("Upload", Format.bitrate(current.total.uploadBitsPerSecond), up)
            }
            LineChart(
                series = listOf(ChartSeries(traffic.download, down), ChartSeries(traffic.upload, up)),
                capacity = TrafficState.HISTORY,
                minimumTop = 1_000_000.0,
                ticks = { top -> listOf(ChartTick(top, Format.bitrate(top)), ChartTick(top / 2, Format.bitrate(top / 2))) },
            )
            Text(
                "Soma do Wi-Fi e dos cabos, último minuto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenericModeCard(url: String) {
    SectionCard("Modo básico") {
        Text(
            "Ainda não há um driver para este roteador. O app mostra o que dá para ver de qualquer rede: " +
                "internet no ar, aparelhos encontrados e o sinal do celular. Para trocar senha, canal ou reiniciar, use a página dele.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OpenRouterPageButton(url)
    }
}

@Composable
private fun RateLabel(label: String, value: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FiberCard(optical: OpticalPower?) {
    if (optical == null) return
    val quality = optical.rxQuality
    SectionCard("Sinal da fibra", trailing = {
        StatusPill(quality, when (quality) {
            Quality.Good -> "Bom"
            Quality.Warning -> "No limite"
            Quality.Critical -> "Ruim"
        })
    }) {
        Text(Format.dbm(optical.rxDbm), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        RangeGauge(
            value = optical.rxDbm,
            min = -30.0,
            max = -5.0,
            comfortStart = OpticalPower.RX_COMFORT_DBM,
            comfortEnd = OpticalPower.RX_MAX_DBM,
            markerColor = RoteadorTheme.status.of(quality),
        )
        Text(
            "Faixa ideal: ${Format.dbm(OpticalPower.RX_MAX_DBM)} a ${Format.dbm(OpticalPower.RX_COMFORT_DBM)}. " +
                "Abaixo de ${Format.dbm(OpticalPower.RX_MIN_DBM)} a conexão começa a cair.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (quality != Quality.Good) {
            Text(
                "Vale abrir um chamado na operadora pedindo verificação do sinal óptico.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InternetCard(status: RouterStatus) {
    val internet = status.internet
    SectionCard("Internet") {
        MetricRow("Conexão", if (internet.connected) "Conectada" else "Desconectada",
            valueColor = RoteadorTheme.status.of(if (internet.connected) Quality.Good else Quality.Critical))
        internet.uptime?.let { MetricRow("Ligada há", Format.duration(it)) }
        internet.publicIpv4?.let { MetricRow("IP público", it) }
        if (internet.dnsServers.isNotEmpty()) MetricRow("DNS", internet.dnsServers.joinToString("\n"))
        if (internet.uptime == null && internet.publicIpv4 == null) {
            Text(
                "Detalhes como IP público e tempo ligado aparecem se o roteador tiver UPnP ligado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status.lanPorts.isNotEmpty()) {
            MetricRow("Portas com cabo", status.lanPorts.filter { it.linkUp }.joinToString { "LAN ${it.number}" }.ifEmpty { "nenhuma" })
        }
    }
}

@Composable
private fun WifiCard(status: RouterStatus) {
    if (status.radios.isEmpty()) return
    SectionCard("Redes Wi-Fi") {
        status.radios.forEach { radio ->
            MetricRow(
                "${radio.band.label} · ${radio.ssid}",
                if (radio.enabled) "canal ${radio.currentChannel ?: "auto"}" else "desligada",
            )
        }
    }
}
