package com.roteador.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.ConnectionState
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.runCatchingCancellable
import com.roteador.core.data.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface RebootPhase {
    data object Idle : RebootPhase
    data object Sending : RebootPhase
    data class Waiting(val elapsedSeconds: Long) : RebootPhase
    data class Back(val tookSeconds: Long) : RebootPhase
    data class Failed(val message: String) : RebootPhase
}

data class SettingsUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val reboot: RebootPhase = RebootPhase.Idle,
    val loggedOut: Boolean = false,
)

class SettingsViewModel(
    private val repository: RouterRepository,
    private val pollInterval: Duration = 3.seconds,
    private val giveUpAfter: Duration = 5.minutes,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(repository.connection, local) { connection, s -> s.copy(connection = connection) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(connection = repository.connection.value))

    /** Sends the reboot, then polls until the router answers again (the repository logs in again by itself). */
    fun reboot() {
        if (local.value.reboot is RebootPhase.Sending || local.value.reboot is RebootPhase.Waiting) return
        local.update { it.copy(reboot = RebootPhase.Sending) }
        viewModelScope.launch {
            val sent = runCatchingCancellable { repository.reboot() }
            sent.exceptionOrNull()?.let { e ->
                local.update { it.copy(reboot = RebootPhase.Failed(e.userMessage())) }
                return@launch
            }
            val started = TimeSource.Monotonic.markNow()
            // Give it time to actually go down before probing.
            delay(pollInterval * 5)
            while (started.elapsedNow() < giveUpAfter) {
                local.update { it.copy(reboot = RebootPhase.Waiting(started.elapsedNow().inWholeSeconds)) }
                if (runCatchingCancellable { repository.status() }.isSuccess) {
                    local.update { it.copy(reboot = RebootPhase.Back(started.elapsedNow().inWholeSeconds)) }
                    return@launch
                }
                delay(pollInterval)
            }
            local.update { it.copy(reboot = RebootPhase.Failed("O roteador não voltou em ${giveUpAfter.inWholeMinutes} minutos.")) }
        }
    }

    fun logout(forgetPassword: Boolean) {
        viewModelScope.launch {
            repository.disconnect(forgetPassword)
            local.update { it.copy(loggedOut = true) }
        }
    }
}
