package com.roteador.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.ConnectionState
import com.roteador.core.data.LoginRequest
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.runCatchingCancellable
import com.roteador.core.data.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val host: String = "",
    val username: String = "admin",
    val password: String = "",
    val remember: Boolean = true,
    val phase: Phase = Phase.Editing,
    val error: String? = null,
) {
    enum class Phase { Restoring, Editing, Connecting, Connected }

    /** The password may stay empty: routers without a driver open in basic mode without one. */
    val canSubmit: Boolean get() = phase == Phase.Editing && host.isNotBlank()
}

class LoginViewModel(private val repository: RouterRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(host = repository.suggestedHost(), username = repository.savedLogin()?.username ?: "admin"),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private var started = false

    /** Called once by the screen. With [autoLogin], a saved password logs in without a tap. */
    fun start(autoLogin: Boolean) {
        if (started) return
        started = true
        when {
            repository.connection.value is ConnectionState.Connected -> _state.update { it.copy(phase = LoginUiState.Phase.Connected) }
            autoLogin && repository.savedLogin()?.hasPassword == true -> restore()
        }
    }

    fun onHostChange(value: String) = _state.update { it.copy(host = value.trim(), error = null) }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value.trim(), error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onRememberChange(value: Boolean) = _state.update { it.copy(remember = value) }

    fun connect() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(phase = LoginUiState.Phase.Connecting, error = null) }
        viewModelScope.launch {
            runCatchingCancellable {
                repository.connect(LoginRequest(current.host, current.username, current.password, current.remember))
            }.onSuccess {
                _state.update { it.copy(phase = LoginUiState.Phase.Connected, password = "") }
            }.onFailure { e ->
                _state.update { it.copy(phase = LoginUiState.Phase.Editing, error = e.userMessage()) }
            }
        }
    }

    private fun restore() {
        _state.update { it.copy(phase = LoginUiState.Phase.Restoring) }
        viewModelScope.launch {
            runCatchingCancellable { repository.restoreSession() }
                .onSuccess { ok -> _state.update { it.copy(phase = if (ok) LoginUiState.Phase.Connected else LoginUiState.Phase.Editing) } }
                .onFailure { e -> _state.update { it.copy(phase = LoginUiState.Phase.Editing, error = e.userMessage()) } }
        }
    }
}
