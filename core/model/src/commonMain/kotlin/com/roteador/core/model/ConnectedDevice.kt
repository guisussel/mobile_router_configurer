package com.roteador.core.model

import kotlin.time.Duration

data class ConnectedDevice(
    val name: String?,
    val ipv4: String?,
    /** `null` when the router does not tell (generic mode only sees IP addresses). */
    val mac: String?,
    val link: DeviceLink,
    /** How long the device has been associated to the Wi-Fi radio (Wi-Fi only). */
    val connectedFor: Duration?,
    val addressing: Addressing,
) {
    /** Stable key for lists and actions. */
    val id: String get() = mac ?: ipv4 ?: name.orEmpty()
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: mac ?: ipv4 ?: "?"
    val online: Boolean get() = link !is DeviceLink.Offline
}

sealed interface DeviceLink {
    /** Seen on the network, but the router does not say how it is connected. */
    data object Unknown : DeviceLink

    data class Wifi(val band: WifiBand) : DeviceLink
    data class Wired(val port: Int?) : DeviceLink
    data object Offline : DeviceLink
}

enum class Addressing { Dhcp, Static }
