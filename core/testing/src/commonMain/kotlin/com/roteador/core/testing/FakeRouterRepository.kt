package com.roteador.core.testing

import com.roteador.core.data.ConnectionState
import com.roteador.core.data.LoginRequest
import com.roteador.core.data.RouterRepository
import com.roteador.core.data.SavedLogin
import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InternetStatus
import com.roteador.core.model.RouterException
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.model.WifiSecurity
import kotlinx.coroutines.flow.MutableStateFlow

/** Scriptable in-memory repository: set the fields, then assert on what was called. */
class FakeRouterRepository : RouterRepository {
    val info = RouterInfo("Fake", "R1", "1.0")
    var password = "certa"
    var saved: SavedLogin? = null
    var savedPassword: String? = null
    var status = sampleStatus()
    var devices: List<ConnectedDevice> = emptyList()
    var wifi: List<WifiBandConfig> = listOf(sampleWifi(WifiBand.Band2_4GHz), sampleWifi(WifiBand.Band5GHz))
    /** Next N calls to [status] fail as if the router were rebooting. */
    var unreachableCalls = 0
    val applied = mutableListOf<WifiBandChange>()
    var rebootCount = 0

    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    override fun suggestedHost() = saved?.host ?: "192.168.15.1"

    override fun savedLogin() = saved

    override suspend fun connect(request: LoginRequest): RouterInfo {
        if (request.password != password) throw RouterException.InvalidCredentials()
        connection.value = ConnectionState.Connected(request.host, info, request.username)
        if (request.remember) {
            saved = SavedLogin(request.host, request.username, hasPassword = true)
            savedPassword = request.password
        }
        return info
    }

    override suspend fun restoreSession(): Boolean {
        val s = saved ?: return false
        val p = savedPassword ?: return false
        connect(LoginRequest(s.host, s.username, p, remember = true))
        return true
    }

    override suspend fun disconnect(forgetPassword: Boolean) {
        connection.value = ConnectionState.Disconnected
        if (forgetPassword) {
            savedPassword = null
            saved = saved?.copy(hasPassword = false)
        }
    }

    override suspend fun status(): RouterStatus {
        if (unreachableCalls > 0) {
            unreachableCalls--
            throw RouterException.Unreachable()
        }
        return status
    }

    override suspend fun devices() = devices

    override suspend fun counters(): List<InterfaceCounters> = emptyList()

    override suspend fun wifiConfig() = wifi

    override suspend fun applyWifi(change: WifiBandChange) {
        applied += change
    }

    override suspend fun reboot() {
        rebootCount++
    }

    companion object {
        fun sampleStatus() = RouterStatus(InternetStatus(true, null, "203.0.113.1", null, emptyList()), null, emptyList(), emptyList())

        fun sampleWifi(band: WifiBand) = WifiBandConfig(
            band = band,
            enabled = true,
            ssid = if (band == WifiBand.Band2_4GHz) "Casa" else "Casa-5GHz",
            password = "senha-antiga",
            hidden = false,
            security = WifiSecurity.Wpa2,
            channel = null,
            currentChannel = if (band == WifiBand.Band2_4GHz) 11 else 52,
            width = if (band == WifiBand.Band2_4GHz) ChannelWidth.Mhz20 else ChannelWidth.Mhz80,
            txPowerPercent = 100,
            wpsEnabled = true,
            bssid = null,
            supportedChannels = if (band == WifiBand.Band2_4GHz) listOf(1, 6, 11) else listOf(36, 40, 44, 48),
            credentialsEditable = band == WifiBand.Band2_4GHz,
        )
    }
}
