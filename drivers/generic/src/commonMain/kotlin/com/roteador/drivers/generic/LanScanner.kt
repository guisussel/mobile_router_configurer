package com.roteador.drivers.generic

import com.roteador.core.model.Ipv4
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * Finds hosts on a /24 without any special permission: for each address it
 * tries a few TCP ports at once. A host counts as present when a port accepts
 * *or refuses* the connection (a refusal is an answer); silence means nobody.
 * MAC addresses are not visible to apps on Android 10+ or iOS, so only IPs come back.
 */
class LanScanner(
    private val ports: List<Int> = DEFAULT_PORTS,
    private val timeoutMillis: Long = 600,
    private val parallelHosts: Int = 32,
) {
    suspend fun scan(subnetOf: Ipv4): List<Ipv4> = SelectorManager(Dispatchers.IO).use { selector ->
        val base = subnetOf.value and 0xFFFFFF00
        val gate = Semaphore(parallelHosts)
        coroutineScope {
            (1L..254L).map { base or it }.map { value ->
                async { gate.withPermit { Ipv4(value).takeIf { isPresent(selector, it.toString()) } } }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun isPresent(selector: SelectorManager, ip: String): Boolean = coroutineScope {
        ports.map { port -> async { probe(selector, ip, port) } }.awaitAll().any { it }
    }

    private suspend fun probe(selector: SelectorManager, ip: String, port: Int): Boolean = try {
        withTimeout(timeoutMillis) { aSocket(selector).tcp().connect(InetSocketAddress(ip, port)).close() }
        true
    } catch (e: CancellationException) {
        if (e is kotlinx.coroutines.TimeoutCancellationException) false else throw e
    } catch (e: Throwable) {
        isRefusal(e)
    }

    companion object {
        /** Web, HTTPS, SSH, SMB, iPhone sync, Chromecast, printers, RDP. */
        val DEFAULT_PORTS = listOf(80, 443, 22, 445, 62078, 8009, 631, 3389)

        /** "Connection refused" = a live host said no. "No route to host" = nobody there. */
        fun isRefusal(error: Throwable): Boolean {
            val text = generateSequence(error) { it.cause }.joinToString(" ") { "${it::class.simpleName} ${it.message}" }
            return text.contains("refused", ignoreCase = true) || text.contains("ECONNREFUSED")
        }
    }
}
