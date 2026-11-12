package com.roteador.core.router

import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceControls
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.RouterException
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

    /** What this router supports. Methods outside it throw [RouterException.Unsupported]. */
    val capabilities: Set<RouterCapability>

    /** `false` when the driver works without a password (generic mode). */
    val needsPassword: Boolean get() = true

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

    // --- Optional: only when the capability is listed -----------------------

    suspend fun deviceControls(): DeviceControls = unsupported("controle de aparelhos")

    /** Creates or replaces the reservation of [mac]. */
    suspend fun reserveIp(mac: String, name: String, ipv4: String): Unit = unsupported("reserva de IP")

    suspend fun removeReservation(reservationId: String): Unit = unsupported("reserva de IP")

    suspend fun blockInternet(ipv4: String, mac: String?): Unit = unsupported("bloqueio de internet")

    suspend fun unblockInternet(blockId: String): Unit = unsupported("bloqueio de internet")
}

fun unsupported(what: String): Nothing = throw RouterException.Unsupported("$what não disponível neste roteador")

/** Recognises one router family and builds its driver. */
interface RouterDriverFactory {
    /** Short id for logs, e.g. "askey-rtf". */
    val id: String

    /** Factories are tried from the highest priority down; the generic fallback is last. */
    val priority: Int get() = 0

    /**
     * Looks at public (pre-login) pages and returns the router identity when this
     * driver can handle it, or `null` otherwise. Must not log in or change anything.
     */
    suspend fun identify(http: HttpClient, baseUrl: String): RouterInfo?

    fun create(http: HttpClient, baseUrl: String, info: RouterInfo): RouterDriver
}
