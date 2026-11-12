package com.roteador.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.pollEvery
import com.roteador.core.data.userMessage
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.WifiBand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.seconds

data class DeviceGroup(val title: String, val devices: List<ConnectedDevice>)

data class DevicesUiState(
    val loading: Boolean = true,
    val groups: List<DeviceGroup> = emptyList(),
    val onlineCount: Int = 0,
    val error: String? = null,
)

class DevicesViewModel(repository: RouterRepository) : ViewModel() {
    val state: StateFlow<DevicesUiState> = pollEvery(5.seconds) { repository.devices() }
        .scan(DevicesUiState()) { acc, result ->
            result.fold(
                onSuccess = { devices -> DevicesUiState(loading = false, groups = group(devices), onlineCount = devices.count { it.online }) },
                onFailure = { acc.copy(loading = false, error = it.userMessage()) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    companion object {
        fun group(devices: List<ConnectedDevice>): List<DeviceGroup> = listOf(
            DeviceGroup("Wi-Fi 5 GHz", devices.filter { it.link == DeviceLink.Wifi(WifiBand.Band5GHz) }),
            DeviceGroup("Wi-Fi 2,4 GHz", devices.filter { it.link == DeviceLink.Wifi(WifiBand.Band2_4GHz) }),
            DeviceGroup("Cabo", devices.filter { it.link is DeviceLink.Wired }),
            DeviceGroup("Desconectados recentemente", devices.filter { it.link == DeviceLink.Offline }),
        ).filter { it.devices.isNotEmpty() }
    }
}
