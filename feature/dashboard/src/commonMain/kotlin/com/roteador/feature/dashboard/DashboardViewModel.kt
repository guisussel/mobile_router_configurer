package com.roteador.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.ConnectionState
import com.roteador.core.data.LiveTraffic
import com.roteador.core.data.LiveTrafficMonitor
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.pollEvery
import com.roteador.core.data.userMessage
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiLink
import com.roteador.core.platform.WifiLinkProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

data class DashboardUiState(
    val router: RouterInfo? = null,
    val status: RouterStatus? = null,
    val statusError: String? = null,
    val traffic: TrafficState = TrafficState(),
    val wifiLink: WifiLink? = null,
)

data class TrafficState(
    val current: LiveTraffic? = null,
    val download: List<Double> = emptyList(),
    val upload: List<Double> = emptyList(),
    val error: String? = null,
) {
    fun next(result: Result<LiveTraffic>): TrafficState = result.fold(
        onSuccess = { t ->
            copy(
                current = t,
                download = (download + t.total.downloadBitsPerSecond).takeLast(HISTORY),
                upload = (upload + t.total.uploadBitsPerSecond).takeLast(HISTORY),
                error = null,
            )
        },
        onFailure = { copy(error = it.userMessage()) },
    )

    companion object {
        /** One point per second: the chart shows the last minute. */
        const val HISTORY = 60
    }
}

/**
 * Three independent streams, each at its own pace: router status every 5 s,
 * traffic every 1 s, this phone's Wi-Fi link every 1 s. They only run while the
 * screen is visible (`WhileSubscribed`).
 */
class DashboardViewModel(
    repository: RouterRepository,
    trafficMonitor: LiveTrafficMonitor,
    wifiLinkProvider: WifiLinkProvider,
) : ViewModel() {

    private val status = pollEvery(5.seconds) { repository.status() }
    private val traffic = trafficMonitor.stream(1.seconds).scan(TrafficState()) { acc, r -> acc.next(r) }
    private val link = pollEvery(1.seconds) { wifiLinkProvider.current() }.map { it.getOrNull() }
    private val router = repository.connection.map { (it as? ConnectionState.Connected)?.info }

    val state: StateFlow<DashboardUiState> =
        combine(router, status.scan(StatusAcc()) { acc, r -> acc.next(r) }, traffic, link) { info, s, t, l ->
            DashboardUiState(router = info, status = s.value, statusError = s.error, traffic = t, wifiLink = l)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Keeps the last good status on screen when one poll fails. */
    private data class StatusAcc(val value: RouterStatus? = null, val error: String? = null) {
        fun next(r: Result<RouterStatus>) = r.fold({ StatusAcc(it, null) }, { copy(error = it.userMessage()) })
    }
}
