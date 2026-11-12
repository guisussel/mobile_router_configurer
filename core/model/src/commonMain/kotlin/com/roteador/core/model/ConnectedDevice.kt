package com.roteador.core.model

import kotlin.time.Duration

data class ConnectedDevice(
    val name: String?,
    val ipv4: String?,
    val mac: String,
    val link: DeviceLink,
    /** How long the device has been associated to the Wi-Fi radio (Wi-Fi only). */
    val connectedFor: Duration?,
    val addressing: Addressing,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: mac
    val online: Boolean get() = link !is DeviceLink.Offline
}

sealed interface DeviceLink {
    data class Wifi(val band: WifiBand) : DeviceLink
    data class Wired(val port: Int?) : DeviceLink
    data object Offline : DeviceLink
}

enum class Addressing { Dhcp, Static }
