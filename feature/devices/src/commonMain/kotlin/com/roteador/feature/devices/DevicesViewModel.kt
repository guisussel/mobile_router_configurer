package com.roteador.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roteador.core.data.ConnectionState
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.pollEvery
import com.roteador.core.data.runCatchingCancellable
import com.roteador.core.data.userMessage
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceControls
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.Ipv4
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.WifiBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class DeviceGroup(val title: String, val devices: List<ConnectedDevice>)

data class DevicesUiState(
    val loading: Boolean = true,
    val groups: List<DeviceGroup> = emptyList(),
    val onlineCount: Int = 0,
    val error: String? = null,
    val capabilities: Set<RouterCapability> = emptySet(),
    /** Reservations and blocks; null until loaded (or when the router has none of these features). */
    val controls: DeviceControls? = null,
    val action: ActionState = ActionState(),
) {
    val canReserve: Boolean get() = RouterCapability.IpReservation in capabilities
    val canBlock: Boolean get() = RouterCapability.InternetBlock in capabilities
}

/** Progress and outcome of the last action from the device menu. */
data class ActionState(val busy: Boolean = false, val message: String? = null, val failed: Boolean = false)

class DevicesViewModel(private val repository: RouterRepository) : ViewModel() {
    private val capabilities = (repository.connection.value as? ConnectionState.Connected)?.capabilities.orEmpty()
    private val controls = MutableStateFlow<DeviceControls?>(null)
    private val action = MutableStateFlow(ActionState())

    private val list = pollEvery(5.seconds) { repository.devices() }
        .scan(DevicesUiState(capabilities = capabilities)) { acc, result ->
            result.fold(
                onSuccess = { devices -> acc.copy(loading = false, groups = group(devices), onlineCount = devices.count { it.online }, error = null) },
                onFailure = { acc.copy(loading = false, error = it.userMessage()) },
            )
        }

    val state: StateFlow<DevicesUiState> = combine(list, controls, action) { s, c, a -> s.copy(controls = c, action = a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState(capabilities = capabilities))

    init {
        refreshControls()
    }

    fun refreshControls() {
        if (!state.value.canReserve && !state.value.canBlock) return
        viewModelScope.launch {
            runCatchingCancellable { repository.deviceControls() }.onSuccess { controls.value = it }
        }
    }

    fun clearMessage() = action.update { ActionState() }

    /** Returns an error message, or null when [ip] can be reserved for [device]. */
    fun validateReservation(device: ConnectedDevice, ip: String): String? {
        val address = Ipv4.parse(ip) ?: return "Digite um IP no formato 192.168.15.50."
        controls.value?.dhcpRange?.let { range ->
            if (address !in range) return "Use um IP entre ${range.start} e ${range.endInclusive}."
        }
        val taken = controls.value?.reservations?.firstOrNull { it.ipv4 == ip && !it.mac.equals(device.mac, ignoreCase = true) }
        if (taken != null) return "Este IP já está reservado para ${taken.name ?: taken.mac}."
        return null
    }

    fun reserve(device: ConnectedDevice, name: String, ip: String) {
        val mac = device.mac ?: return
        if (validateReservation(device, ip) != null) return
        run("IP $ip reservado para ${name.ifBlank { device.displayName }}. Vale quando o aparelho reconectar ao Wi-Fi.") {
            repository.reserveIp(mac, name.trim(), ip)
        }
    }

    fun removeReservation(device: ConnectedDevice) {
        val reservation = controls.value?.reservationFor(device.mac) ?: return
        run("Reserva removida. O aparelho volta a receber um IP qualquer ao reconectar.") {
            repository.removeReservation(reservation.id)
        }
    }

    /**
     * The block is a firewall rule on the device's IP. If the IP is not reserved,
     * it is reserved first (same address), so the block keeps working after the
     * device reconnects.
     */
    fun blockInternet(device: ConnectedDevice) {
        val ip = device.ipv4 ?: return
        run("Internet de ${device.displayName} bloqueada. Ele continua no Wi-Fi, mas sem acesso à internet.") {
            val mac = device.mac
            if (mac != null && controls.value?.reservationFor(mac) == null && state.value.canReserve) {
                repository.reserveIp(mac, device.name.orEmpty(), ip)
            }
            repository.blockInternet(ip, mac)
        }
    }

    fun unblockInternet(device: ConnectedDevice) {
        val block = controls.value?.blockFor(device.ipv4) ?: return
        run("Internet de ${device.displayName} liberada.") { repository.unblockInternet(block.id) }
    }

    private fun run(success: String, block: suspend () -> Unit) {
        if (action.value.busy) return
        action.value = ActionState(busy = true)
        viewModelScope.launch {
            runCatchingCancellable { block() }
                .onSuccess { action.value = ActionState(message = success) }
                .onFailure { action.value = ActionState(message = it.userMessage(), failed = true) }
            runCatchingCancellable { repository.deviceControls() }.onSuccess { controls.value = it }
        }
    }

    companion object {
        fun group(devices: List<ConnectedDevice>): List<DeviceGroup> = listOf(
            DeviceGroup("Wi-Fi 5 GHz", devices.filter { it.link == DeviceLink.Wifi(WifiBand.Band5GHz) }),
            DeviceGroup("Wi-Fi 2,4 GHz", devices.filter { it.link == DeviceLink.Wifi(WifiBand.Band2_4GHz) }),
            DeviceGroup("Cabo", devices.filter { it.link is DeviceLink.Wired }),
            DeviceGroup("Na rede", devices.filter { it.link == DeviceLink.Unknown }),
            DeviceGroup("Desconectados recentemente", devices.filter { it.link == DeviceLink.Offline }),
        ).filter { it.devices.isNotEmpty() }
    }
}
