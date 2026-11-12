package com.roteador.drivers.generic

import co.touchlab.kermit.Logger
import com.roteador.core.model.Addressing
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InterfaceKind
import com.roteador.core.model.InternetStatus
import com.roteador.core.model.Ipv4
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.RouterException
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.router.RouterDriver
import com.roteador.core.router.RouterDriverFactory
import com.roteador.core.router.unsupported
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Fallback for routers without a dedicated driver. Works on any network, with
 * what does not depend on the router's web UI:
 * - UPnP IGD, when the router has it on: identity, public IP, uptime, WAN traffic;
 * - otherwise a reachability check for "is the internet up";
 * - devices found by scanning the LAN from the phone.
 * Wi-Fi settings and reboot are not possible: the app offers the router's own page.
 */
class GenericDriverFactory(
    private val scanner: LanScanner = LanScanner(),
) : RouterDriverFactory {
    override val id = "generic"
    override val priority = -100

    /** UPnP answer per address, kept between [identify] and [create]. */
    private val discovered = mutableMapOf<String, UpnpIgd?>()

    override suspend fun identify(http: HttpClient, baseUrl: String): RouterInfo {
        val host = Url(baseUrl).host
        val igd = UpnpIgd.discover(host, http)
        discovered[baseUrl] = igd
        if (igd == null && !answers(host)) throw RouterException.Unreachable()
        return igd?.description?.let {
            RouterInfo(
                manufacturer = it.manufacturer ?: "Roteador",
                model = listOfNotNull(it.modelName, it.modelNumber).joinToString(" ").ifBlank { it.friendlyName ?: "UPnP" },
                firmware = "?",
            )
        } ?: RouterInfo(manufacturer = "Roteador", model = "sem driver específico", firmware = "?")
    }

    override fun create(http: HttpClient, baseUrl: String, info: RouterInfo): RouterDriver =
        GenericDriver(http, Url(baseUrl).host, info, discovered.remove(baseUrl), scanner)

    /** Something must live at the address: web page, HTTPS or DNS (routers answer at least one). */
    private suspend fun answers(host: String): Boolean = SelectorManager(Dispatchers.IO).use { selector ->
        coroutineScope {
            listOf(80, 443, 53).map { port ->
                async {
                    runCatching {
                        withTimeout(1_500) { aSocket(selector).tcp().connect(InetSocketAddress(host, port)).close() }
                    }.fold({ true }, { LanScanner.isRefusal(it) })
                }
            }.awaitAll().any { it }
        }
    }
}

internal class GenericDriver(
    private val http: HttpClient,
    private val host: String,
    override val info: RouterInfo,
    private val igd: UpnpIgd?,
    private val scanner: LanScanner,
) : RouterDriver {
    private val log = Logger.withTag("GenericDriver")
    private var cachedDevices: Pair<TimeMark, List<ConnectedDevice>>? = null

    override val defaultUsername = ""
    override val needsPassword = false

    override val capabilities: Set<RouterCapability> = buildSet {
        add(RouterCapability.Status)
        if (Ipv4.parse(host) != null) add(RouterCapability.Devices)
        if (igd?.description?.counters != null) add(RouterCapability.Traffic)
    }

    /** Nothing to log into: the generic mode never touches the router's admin page. */
    override suspend fun login(username: String, password: String) = Unit

    override suspend fun logout() = Unit

    override suspend fun status(): RouterStatus {
        val upnp = igd?.let { runCatching { it.statusInfo() to it.externalIp() }.getOrNull() }
        val state = upnp?.first
        return RouterStatus(
            internet = InternetStatus(
                connected = state?.first?.equals("Connected", ignoreCase = true) ?: internetReachable(),
                uptime = state?.second?.seconds,
                publicIpv4 = upnp?.second,
                publicIpv6 = null,
                dnsServers = emptyList(),
            ),
            optical = null,
            radios = emptyList(),
            lanPorts = emptyList(),
        )
    }

    /** Scanning takes a few seconds, so results are reused for a minute. */
    override suspend fun devices(): List<ConnectedDevice> {
        cachedDevices?.let { (at, list) -> if (at.elapsedNow() < DEVICE_CACHE) return list }
        val gateway = Ipv4.parse(host) ?: return emptyList()
        val found = scanner.scan(gateway).filter { it != gateway }
        log.i { "LAN scan found ${found.size} hosts" }
        val devices = coroutineScope {
            found.map { ip ->
                async {
                    ConnectedDevice(
                        name = reverseLookup(ip.toString()),
                        ipv4 = ip.toString(),
                        mac = null,
                        link = DeviceLink.Unknown,
                        connectedFor = null,
                        addressing = Addressing.Dhcp,
                    )
                }
            }.awaitAll()
        }.sortedBy { Ipv4.parse(it.ipv4.orEmpty())?.value ?: 0 }
        cachedDevices = TimeSource.Monotonic.markNow() to devices
        return devices
    }

    override suspend fun counters(): List<InterfaceCounters> {
        val (rx, tx) = igd?.totalBytes() ?: unsupported("tráfego (ligue o UPnP no roteador)")
        return listOf(InterfaceCounters("wan", InterfaceKind.Wan, rxBytes = rx, txBytes = tx))
    }

    override suspend fun wifiConfig(): List<WifiBandConfig> = unsupported("configurar o Wi-Fi")

    override suspend fun applyWifi(change: WifiBandChange) = unsupported("configurar o Wi-Fi")

    override suspend fun reboot() = unsupported("reiniciar")

    override fun close() = http.close()

    /** A TCP handshake with two public DNS resolvers; no data is sent. */
    private suspend fun internetReachable(): Boolean = SelectorManager(Dispatchers.IO).use { selector ->
        coroutineScope {
            listOf("1.1.1.1" to 443, "8.8.8.8" to 53).map { (ip, port) ->
                async {
                    runCatching {
                        withTimeout(2_000) { aSocket(selector).tcp().connect(InetSocketAddress(ip, port)).close() }
                    }.isSuccess
                }
            }.awaitAll().any { it }
        }
    }

    private companion object {
        val DEVICE_CACHE = 60.seconds
    }
}
