package com.roteador.core.data

import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceControls
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * The single door from the UI to the router. Every method throws
 * [com.roteador.core.model.RouterException] on failure.
 */
interface RouterRepository {
    val connection: StateFlow<ConnectionState>

    /** Address to pre-fill on the login screen: saved one, else this Wi-Fi's gateway, else the usual default. */
    fun suggestedHost(): String

    fun savedLogin(): SavedLogin?

    suspend fun connect(request: LoginRequest): RouterInfo

    /** Logs in with the saved password, if any. Returns `false` when there is nothing saved. */
    suspend fun restoreSession(): Boolean

    suspend fun disconnect(forgetPassword: Boolean = false)

    suspend fun status(): RouterStatus

    suspend fun devices(): List<ConnectedDevice>

    suspend fun counters(): List<InterfaceCounters>

    suspend fun wifiConfig(): List<WifiBandConfig>

    suspend fun applyWifi(change: WifiBandChange)

    suspend fun reboot()

    suspend fun deviceControls(): DeviceControls

    suspend fun reserveIp(mac: String, name: String, ipv4: String)

    suspend fun removeReservation(reservationId: String)

    suspend fun blockInternet(ipv4: String, mac: String?)

    suspend fun unblockInternet(blockId: String)
}

data class LoginRequest(
    val host: String,
    val username: String,
    val password: String,
    val remember: Boolean,
)

data class SavedLogin(val host: String, val username: String, val hasPassword: Boolean)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data class Connected(
        val host: String,
        val info: RouterInfo,
        val username: String,
        val capabilities: Set<RouterCapability>,
    ) : ConnectionState {
        /** Address of the router's own web page, for things the app cannot do. */
        val webUrl: String get() = if (host.startsWith("http")) host else "http://$host"

        /** Generic mode: no driver for this model. */
        val limited: Boolean get() = RouterCapability.WifiSettings !in capabilities
    }
}
