package com.roteador.feature.signal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.pollEvery
import com.roteador.core.model.WifiLink
import com.roteador.core.platform.WifiLinkProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

data class SignalUiState(
    val link: WifiLink? = null,
    /** RSSI in dBm, one point per second, oldest first. */
    val history: List<Int> = emptyList(),
) {
    val best: Int? get() = history.maxOrNull()
    val worst: Int? get() = history.minOrNull()

    fun next(link: WifiLink): SignalUiState = copy(
        link = link,
        history = link.rssiDbm?.let { (history + it).takeLast(HISTORY) } ?: history,
    )

    companion object {
        /** Two minutes: enough to walk around the house. */
        const val HISTORY = 120
    }
}

/** Reads this phone's own link every second; no router calls involved. */
class SignalViewModel(provider: WifiLinkProvider) : ViewModel() {
    val state: StateFlow<SignalUiState> = pollEvery(1.seconds) { provider.current() }
        .scan(SignalUiState()) { acc, r -> r.getOrNull()?.let(acc::next) ?: acc }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalUiState())
}
