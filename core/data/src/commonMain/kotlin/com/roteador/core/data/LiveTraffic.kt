package com.roteador.core.data

import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InterfaceKind
import com.roteador.core.model.Throughput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

data class LiveTraffic(
    val total: Throughput,
    val byInterface: Map<InterfaceKind, Throughput>,
) {
    companion object {
        val Idle = LiveTraffic(Throughput.Zero, emptyMap())
    }
}

/** Turns two readings of cumulative counters into rates. */
object ThroughputCalculator {
    fun between(previous: List<InterfaceCounters>, current: List<InterfaceCounters>, elapsed: Duration): LiveTraffic {
        val seconds = elapsed.inWholeMilliseconds / 1000.0
        if (seconds <= 0.0) return LiveTraffic(Throughput.Zero, emptyMap())
        val before = previous.associateBy { it.interfaceId }
        val rates = current.mapNotNull { now ->
            val old = before[now.interfaceId] ?: return@mapNotNull null
            now.kind to Throughput(
                // On a router port, tx = data going to the clients = their download.
                downloadBitsPerSecond = delta(old.txBytes, now.txBytes) * 8 / seconds,
                uploadBitsPerSecond = delta(old.rxBytes, now.rxBytes) * 8 / seconds,
            )
        }.toMap()
        return LiveTraffic(rates.values.fold(Throughput.Zero, Throughput::plus), rates)
    }

    /** Counters reset on reboot and may wrap; a negative step is reported as zero. */
    private fun delta(old: Long, new: Long): Double = (new - old).coerceAtLeast(0).toDouble()
}

/**
 * Polls the router's counters and emits a rate per tick.
 *
 * Routers refresh these counters on their own schedule (the Askey RTF every
 * ~5-6 s), so most 1 s polls return the same numbers. The rate is therefore
 * computed between two readings that actually changed, and held in between;
 * if nothing changes for [idleAfter], the link is idle and the rate drops to zero.
 * The first value arrives after the router's first refresh.
 */
class LiveTrafficMonitor(
    private val repository: RouterRepository,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val idleAfter: Duration = 8.seconds,
) {
    fun stream(period: Duration = 1.seconds): Flow<Result<LiveTraffic>> = flow {
        var lastChange: Pair<ComparableTimeMark, List<InterfaceCounters>>? = null
        var lastRate: LiveTraffic? = null
        while (true) {
            val now = timeSource.markNow()
            runCatchingCancellable { repository.counters() }
                .onSuccess { counters ->
                    val base = lastChange
                    when {
                        base == null -> lastChange = now to counters
                        counters != base.second -> {
                            val rate = ThroughputCalculator.between(base.second, counters, now - base.first)
                            lastRate = rate
                            lastChange = now to counters
                            emit(Result.success(rate))
                        }
                        now - base.first > idleAfter -> emit(Result.success(LiveTraffic.Idle))
                        else -> lastRate?.let { emit(Result.success(it)) }
                    }
                }
                .onFailure { emit(Result.failure(it)) }
            delay(period)
        }
    }
}
