package com.roteador.core.router

import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import io.ktor.client.HttpClient

/**
 * Everything the app can ask of a router. One implementation per router family
 * lives in `drivers/<name>`; the rest of the app never sees HTTP or HTML.
 *
 * Contract for implementations:
 * - throw only [com.roteador.core.model.RouterException] (plus cancellation);
 * - throw `SessionExpired` when the router bounces us to its login page; the
 *   caller logs in again and retries once;
 * - calls are never made concurrently (the repository serialises them), so a
 *   driver may keep per-session state such as anti-CSRF tokens.
 */
interface RouterDriver : AutoCloseable {
    val info: RouterInfo

    /** Login user printed on the router label, used to pre-fill the login form. */
    val defaultUsername: String

    suspend fun login(username: String, password: String)

    suspend fun logout()

    suspend fun status(): RouterStatus

    suspend fun devices(): List<ConnectedDevice>

    /** Cheap call meant for 1 s polling: cumulative byte counters per interface. */
    suspend fun counters(): List<InterfaceCounters>

    suspend fun wifiConfig(): List<WifiBandConfig>

    /** Applies the change; the radio usually restarts, dropping Wi-Fi clients for a few seconds. */
    suspend fun applyWifi(change: WifiBandChange)

    /** Asks the router to restart. Returns as soon as the router accepted the order. */
    suspend fun reboot()
}

/** Recognises one router family and builds its driver. */
interface RouterDriverFactory {
    /** Short id for logs, e.g. "askey-rtf". */
    val id: String

    /**
     * Looks at public (pre-login) pages and returns the router identity when this
     * driver can handle it, or `null` otherwise. Must not log in or change anything.
     */
    suspend fun identify(http: HttpClient, baseUrl: String): RouterInfo?

    fun create(http: HttpClient, baseUrl: String, info: RouterInfo): RouterDriver
}
