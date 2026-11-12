package com.roteador.feature.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.ConnectionState
import com.roteador.core.data.RouterRepository
import com.roteador.core.model.RouterCapability
import com.roteador.core.data.runCatchingCancellable
import com.roteador.core.data.userMessage
import com.roteador.core.model.WifiBand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class WifiUiState(
    /** Set when the connected router has no Wi-Fi driver: the screen offers its web page instead. */
    val unsupportedUrl: String? = null,
    val loading: Boolean = true,
    val drafts: List<WifiDraft> = emptyList(),
    val applying: WifiBand? = null,
    val message: String? = null,
    val error: String? = null,
)

class WifiViewModel(
    private val repository: RouterRepository,
    /** Time the radio needs to restart before its new settings can be read back. */
    private val settleTime: Duration = 10.seconds,
) : ViewModel() {
    private val _state = MutableStateFlow(WifiUiState())
    val state: StateFlow<WifiUiState> = _state.asStateFlow()

    init {
        val connection = repository.connection.value as? ConnectionState.Connected
        if (connection != null && RouterCapability.WifiSettings !in connection.capabilities) {
            _state.value = WifiUiState(unsupportedUrl = connection.webUrl, loading = false)
        } else {
            load()
        }
    }

    fun load() {
        _state.update { it.copy(loading = it.drafts.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatchingCancellable { repository.wifiConfig() }
                .onSuccess { configs -> _state.update { it.copy(loading = false, drafts = configs.map(::WifiDraft)) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun edit(band: WifiBand, transform: (WifiDraft) -> WifiDraft) = _state.update { state ->
        state.copy(drafts = state.drafts.map { if (it.original.band == band) transform(it) else it }, message = null)
    }

    fun discard(band: WifiBand) = edit(band) { WifiDraft(it.original) }

    fun apply(band: WifiBand) {
        val draft = _state.value.drafts.firstOrNull { it.original.band == band } ?: return
        val change = draft.change ?: return
        if (!draft.valid) return
        _state.update { it.copy(applying = band, message = null, error = null) }
        viewModelScope.launch {
            runCatchingCancellable { repository.applyWifi(change) }
                .onSuccess {
                    _state.update {
                        it.copy(message = "Enviado. A rede ${band.label} reinicia em alguns segundos; se o celular desconectar, ele volta sozinho.")
                    }
                    delay(settleTime)
                    reloadAfterRestart()
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
            _state.update { it.copy(applying = null) }
        }
    }

    /** The radio restart can drop us for a while; keep trying for about a minute. */
    private suspend fun reloadAfterRestart() {
        repeat(6) {
            val result = runCatchingCancellable { repository.wifiConfig() }
            result.getOrNull()?.let { configs ->
                _state.update { it.copy(drafts = configs.map(::WifiDraft)) }
                return
            }
            delay(settleTime)
        }
    }
}
