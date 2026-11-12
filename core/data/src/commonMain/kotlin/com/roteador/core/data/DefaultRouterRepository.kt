package com.roteador.core.data

import co.touchlab.kermit.Logger
import com.roteador.core.model.RouterException
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.WifiBandChange
import com.roteador.core.platform.WifiLinkProvider
import com.roteador.core.router.RouterDriver
import com.roteador.core.router.RouterConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the one live driver. Calls are serialised with a mutex because router
 * web UIs keep a single session and hand out one-shot tokens per page; when the
 * router drops the session, the call logs in again once and retries.
 */
class DefaultRouterRepository(
    private val connector: RouterConnector,
    private val credentials: CredentialsStore,
    private val wifiLink: WifiLinkProvider,
) : RouterRepository {
    private val log = Logger.withTag("RouterRepository")
    private val mutex = Mutex()
    private var driver: RouterDriver? = null
    private var session: LoginRequest? = null

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    override fun suggestedHost(): String =
        credentials.load()?.host ?: wifiLink.current().gatewayIpv4 ?: DEFAULT_HOST

    override fun savedLogin(): SavedLogin? = credentials.load()?.let {
        SavedLogin(it.host, it.username, hasPassword = it.password != null)
    }

    override suspend fun connect(request: LoginRequest): RouterInfo = mutex.withLock {
        closeDriver()
        val opened = connector.open(request.host)
        try {
            opened.login(request.username, request.password)
        } catch (e: Throwable) {
            opened.close()
            throw e
        }
        driver = opened
        session = request
        credentials.save(request.host, request.username, request.password.takeIf { request.remember })
        _connection.value = ConnectionState.Connected(request.host, opened.info, request.username)
        opened.info
    }

    override suspend fun restoreSession(): Boolean {
        val saved = credentials.load() ?: return false
        val password = saved.password ?: return false
        connect(LoginRequest(saved.host, saved.username, password, remember = true))
        return true
    }

    override suspend fun disconnect(forgetPassword: Boolean) = mutex.withLock {
        runCatching { driver?.logout() }
        closeDriver()
        if (forgetPassword) credentials.forgetPassword()
    }

    override suspend fun status() = call { status() }

    override suspend fun devices() = call { devices() }

    override suspend fun counters() = call { counters() }

    override suspend fun wifiConfig() = call { wifiConfig() }

    override suspend fun applyWifi(change: WifiBandChange) = call { applyWifi(change) }

    override suspend fun reboot() = call { reboot() }

    private suspend fun <T> call(block: suspend RouterDriver.() -> T): T = mutex.withLock {
        val current = driver ?: throw RouterException.SessionExpired()
        try {
            current.block()
        } catch (e: RouterException.SessionExpired) {
            val login = session ?: throw e
            log.i { "Session expired, logging in again" }
            current.login(login.username, login.password)
            current.block()
        }
    }

    private fun closeDriver() {
        driver?.close()
        driver = null
        session = null
        _connection.value = ConnectionState.Disconnected
    }

    private companion object {
        /** Default LAN address of Vivo Fibra routers; other ISPs mostly use 192.168.0.1 or 192.168.1.1. */
        const val DEFAULT_HOST = "192.168.15.1"
    }
}
