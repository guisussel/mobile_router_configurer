package com.roteador.core.model

/** What this phone sees of its own Wi-Fi connection. Fields are `null` when the OS does not expose them. */
data class WifiLink(
    val connected: Boolean,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val gatewayIpv4: String? = null,
    /** `true` when the OS hides signal details from apps (iOS does), not because Wi-Fi is off. */
    val limitedByPlatform: Boolean = false,
) {
    val band: WifiBand?
        get() = when (frequencyMhz) {
            null -> null
            in 2400..2500 -> WifiBand.Band2_4GHz
            else -> WifiBand.Band5GHz
        }

    val signalQuality: Quality?
        get() = rssiDbm?.let {
            when {
                it >= -60 -> Quality.Good
                it >= -72 -> Quality.Warning
                else -> Quality.Critical
            }
        }

    companion object {
        val Disconnected = WifiLink(connected = false)
    }
}
