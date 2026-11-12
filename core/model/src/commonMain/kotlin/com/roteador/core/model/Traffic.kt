package com.roteador.core.model

/**
 * Cumulative byte counters of one router interface, as the router reports them.
 * "rx" and "tx" are from the router's point of view: on a Wi-Fi radio, `txBytes`
 * is what the clients downloaded.
 */
data class InterfaceCounters(
    val interfaceId: String,
    val kind: InterfaceKind,
    val rxBytes: Long,
    val txBytes: Long,
)

sealed interface InterfaceKind {
    data class WifiRadio(val band: WifiBand) : InterfaceKind
    data class LanPort(val number: Int) : InterfaceKind
}

/** Rate over the last sampling interval, from the clients' point of view. */
data class Throughput(val downloadBitsPerSecond: Double, val uploadBitsPerSecond: Double) {
    val totalBitsPerSecond: Double get() = downloadBitsPerSecond + uploadBitsPerSecond

    operator fun plus(other: Throughput) = Throughput(
        downloadBitsPerSecond + other.downloadBitsPerSecond,
        uploadBitsPerSecond + other.uploadBitsPerSecond,
    )

    companion object {
        val Zero = Throughput(0.0, 0.0)
    }
}
