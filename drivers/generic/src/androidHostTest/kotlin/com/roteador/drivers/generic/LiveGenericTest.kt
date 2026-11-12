package com.roteador.drivers.generic

import com.roteador.core.router.createRouterHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Runs the generic driver against the real network (read-only: UPnP queries,
 * a reachability check and a TCP scan of the /24). Skipped unless ROUTER_HOST is set:
 *
 *     ROUTER_HOST=192.168.15.1 ./gradlew :drivers:generic:testAndroidHostTest --tests '*LiveGenericTest*' -i
 */
class LiveGenericTest {
    @Test
    fun scansTheRealNetwork() = runBlocking {
        val host = System.getenv("ROUTER_HOST") ?: return@runBlocking println("LiveGenericTest skipped: ROUTER_HOST not set")
        val factory = GenericDriverFactory()
        val http = createRouterHttpClient()
        val info = factory.identify(http, "http://$host")
        val driver = factory.create(http, "http://$host", info)
        try {
            println("Info: $info")
            println("Capabilities: ${driver.capabilities}")
            println("Status: ${driver.status().internet}")
            val started = TimeSource.Monotonic.markNow()
            val devices = driver.devices()
            println("Scan: ${devices.size} devices in ${started.elapsedNow()}")
            devices.forEach { println("Device: ${it.ipv4} ${it.name ?: "-"}") }
        } finally {
            driver.close()
        }
    }
}
