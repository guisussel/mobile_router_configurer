package com.roteador.core.data

import app.cash.turbine.test
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InterfaceKind
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTrafficTest {
    private val wifi5 = InterfaceKind.WifiRadio(WifiBand.Band5GHz)

    @Test
    fun ratesComeFromCounterDeltas() {
        val before = listOf(InterfaceCounters("wl1", wifi5, rxBytes = 1_000, txBytes = 10_000))
        val after = listOf(InterfaceCounters("wl1", wifi5, rxBytes = 2_000, txBytes = 1_260_000))
        val traffic = ThroughputCalculator.between(before, after, 2.seconds)
        // 1,25 MB in 2 s = 5 Mbit/s down; 1 kB in 2 s = 4 kbit/s up.
        assertEquals(5_000_000.0, traffic.total.downloadBitsPerSecond)
        assertEquals(4_000.0, traffic.total.uploadBitsPerSecond)
    }

    @Test
    fun wanCountersAreTheHouseTotal() {
        val wan = InterfaceKind.Wan
        val before = listOf(InterfaceCounters("wan", wan, 0, 0), InterfaceCounters("wl1", wifi5, 0, 0))
        val after = listOf(InterfaceCounters("wan", wan, rxBytes = 250_000, txBytes = 0), InterfaceCounters("wl1", wifi5, 0, 250_000))
        val traffic = ThroughputCalculator.between(before, after, 1.seconds)
        // WAN rx = download; the radio's tx is the same bytes and must not double the total.
        assertEquals(2_000_000.0, traffic.total.downloadBitsPerSecond)
    }

    @Test
    fun counterResetCountsAsZero() {
        val before = listOf(InterfaceCounters("wl1", wifi5, rxBytes = 5_000, txBytes = 5_000))
        val after = listOf(InterfaceCounters("wl1", wifi5, rxBytes = 10, txBytes = 10))
        assertEquals(0.0, ThroughputCalculator.between(before, after, 1.seconds).total.totalBitsPerSecond)
    }

    @Test
    fun monitorMeasuresBetweenRouterRefreshesAndHoldsInBetween() = runTest {
        val driver = FakeDriver()
        val repository = DefaultRouterRepository({ driver }, CredentialsStore(MemorySecureStore()), { WifiLink.Disconnected })
        repository.connect(LoginRequest("h", "admin", "certa", remember = false))
        driver.counters = listOf(InterfaceCounters("wl1", wifi5, 0, 0))

        LiveTrafficMonitor(repository, testScheduler.timeSource, idleAfter = 8.seconds).stream(1.seconds).test {
            // Router refreshes its counters only every 5 s: 625 kB in 5 s = 1 Mbit/s.
            advanceTimeBy(4_500)
            driver.counters = listOf(InterfaceCounters("wl1", wifi5, 0, 625_000))
            assertEquals(1_000_000.0, awaitItem().getOrThrow().total.downloadBitsPerSecond)
            // Next polls see the same numbers: the last rate is held.
            assertEquals(1_000_000.0, awaitItem().getOrThrow().total.downloadBitsPerSecond)
            // Nothing changes for longer than idleAfter: idle.
            advanceTimeBy(9_000)
            assertEquals(LiveTraffic.Idle, expectMostRecentItem().getOrThrow())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
