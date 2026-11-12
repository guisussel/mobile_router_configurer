package com.roteador.drivers.askey

import com.roteador.core.data.ThroughputCalculator
import com.roteador.core.router.RouterDriverRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Read-only check against a real router on the LAN. Skipped unless
 * `ROUTER_PASSWORD` is set (and optionally `ROUTER_HOST`, `ROUTER_USER`):
 *
 *     ROUTER_PASSWORD=… ./gradlew :drivers:askey:testAndroidHostTest --tests '*LiveRouterTest*' -i
 *
 * Never changes settings: no Wi-Fi apply, no reboot.
 */
class LiveRouterTest {
    private val password: String? = System.getenv("ROUTER_PASSWORD")
    private val host = System.getenv("ROUTER_HOST") ?: "192.168.15.1"
    private val user = System.getenv("ROUTER_USER") ?: "admin"

    @Test
    fun readsTheRealRouterAndSustainsOneSecondPolling() = runBlocking {
        val password = password ?: return@runBlocking println("LiveRouterTest skipped: ROUTER_PASSWORD not set")
        val driver = RouterDriverRegistry(listOf(AskeyRtfDriverFactory())).open(host)
        try {
            println("Router: ${driver.info}")
            driver.login(user, password)

            val status = driver.status()
            println("Internet: ${status.internet}")
            println("Fiber: ${status.optical} -> ${status.optical?.rxQuality}")
            println("Radios: ${status.radios}")

            driver.devices().forEach { println("Device: ${it.displayName} ${it.ipv4} ${it.link} ${it.connectedFor}") }

            driver.wifiConfig().forEach {
                println("Wi-Fi ${it.band.label}: ssid=${it.ssid} security=${it.security} channel=${it.channel ?: "auto"}(${it.currentChannel}) width=${it.width} power=${it.txPowerPercent}% wps=${it.wpsEnabled} channels=${it.supportedChannels} password=${if (it.password != null) "<lida>" else "<ausente>"}")
            }

            // 30 polls at 1 s, like the dashboard does.
            val latencies = mutableListOf<Long>()
            var previous = driver.counters()
            var previousMark = TimeSource.Monotonic.markNow()
            repeat(30) {
                delay(1000.milliseconds)
                val started = TimeSource.Monotonic.markNow()
                val counters = driver.counters()
                latencies += started.elapsedNow().inWholeMilliseconds
                val traffic = ThroughputCalculator.between(previous, counters, previousMark.elapsedNow())
                previous = counters
                previousMark = TimeSource.Monotonic.markNow()
                if (it % 5 == 0) {
                    println("Traffic: down %.2f Mbit/s, up %.2f Mbit/s".format(traffic.total.downloadBitsPerSecond / 1e6, traffic.total.uploadBitsPerSecond / 1e6))
                }
            }
            latencies.sort()
            println("Polling latency ms: min=${latencies.first()} median=${latencies[latencies.size / 2]} max=${latencies.last()}")
            assertTrue(latencies[latencies.size / 2] < 1000, "median poll must fit in 1 s")
        } finally {
            runCatching { driver.logout() }
            driver.close()
        }
    }
}
