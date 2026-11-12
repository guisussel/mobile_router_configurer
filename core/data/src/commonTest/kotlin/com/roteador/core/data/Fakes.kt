package com.roteador.core.data

import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InternetStatus
import com.roteador.core.model.RouterException
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.platform.SecureStore
import com.roteador.core.router.RouterDriver

class MemorySecureStore : SecureStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

class FakeDriver(
    private val password: String = "certa",
) : RouterDriver {
    override val info = RouterInfo("Fake", "R1", "1.0")
    override val defaultUsername = "admin"
    override val capabilities = com.roteador.core.model.RouterCapability.entries.toSet()
    var loggedIn = false
    var logins = 0
    var expireNextCall = false
    var closed = false
    var counters: List<InterfaceCounters> = emptyList()

    override suspend fun login(username: String, password: String) {
        logins++
        if (password != this.password) throw RouterException.InvalidCredentials()
        loggedIn = true
    }

    override suspend fun logout() { loggedIn = false }

    override suspend fun status(): RouterStatus {
        guard()
        return RouterStatus(InternetStatus(true, null, null, null, emptyList()), null, emptyList(), emptyList())
    }

    override suspend fun devices(): List<ConnectedDevice> = guard().let { emptyList() }
    override suspend fun counters(): List<InterfaceCounters> = guard().let { counters }
    override suspend fun wifiConfig(): List<WifiBandConfig> = guard().let { emptyList() }
    override suspend fun applyWifi(change: WifiBandChange) = guard()
    override suspend fun reboot() = guard()
    override fun close() { closed = true }

    private fun guard() {
        if (expireNextCall) {
            expireNextCall = false
            loggedIn = false
        }
        if (!loggedIn) throw RouterException.SessionExpired()
    }
}
