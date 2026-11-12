package com.roteador.core.model

import kotlin.time.Duration

/** Snapshot of the router's home page: internet link, fiber signal, Wi-Fi radios, LAN ports. */
data class RouterStatus(
    val internet: InternetStatus,
    val optical: OpticalPower?,
    val radios: List<WifiRadioSummary>,
    val lanPorts: List<LanPort>,
)

data class InternetStatus(
    val connected: Boolean,
    val uptime: Duration?,
    val publicIpv4: String?,
    val publicIpv6: String?,
    val dnsServers: List<String>,
)

data class WifiRadioSummary(
    val band: WifiBand,
    val ssid: String,
    val enabled: Boolean,
    val currentChannel: Int?,
)

data class LanPort(val number: Int, val linkUp: Boolean)

/**
 * Optical power at the fiber terminal, in dBm.
 *
 * GPON class B+ receivers work from -8 to -27 dBm; below about -24 dBm there is
 * little margin left, so the app flags it before it turns into dropouts.
 */
data class OpticalPower(val rxDbm: Double, val txDbm: Double) {
    val rxQuality: Quality
        get() = when {
            rxDbm > -8.0 -> Quality.Critical
            rxDbm >= -24.0 -> Quality.Good
            rxDbm >= -27.0 -> Quality.Warning
            else -> Quality.Critical
        }

    companion object {
        const val RX_MIN_DBM = -27.0
        const val RX_MAX_DBM = -8.0
        const val RX_COMFORT_DBM = -24.0
    }
}

enum class Quality { Good, Warning, Critical }
