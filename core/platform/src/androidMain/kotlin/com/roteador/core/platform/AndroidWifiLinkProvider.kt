package com.roteador.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.roteador.core.model.WifiLink
import java.net.Inet4Address

/**
 * RSSI, link speed and frequency need only `ACCESS_WIFI_STATE`. The SSID is
 * hidden by Android unless the app holds location permission, so it may be null.
 */
class AndroidWifiLinkProvider(context: Context) : WifiLinkProvider {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION") // connectionInfo is the only synchronous source on API 29-30.
    override fun current(): WifiLink {
        val network = connectivity.activeNetwork ?: return WifiLink.Disconnected
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return WifiLink.Disconnected
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return WifiLink.Disconnected

        val info = wifi.connectionInfo
        val gateway = connectivity.getLinkProperties(network)?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress

        return WifiLink(
            connected = true,
            ssid = info?.ssid?.removeSurrounding("\"")?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() },
            bssid = info?.bssid?.takeUnless { it == "02:00:00:00:00:00" },
            rssiDbm = info?.rssi?.takeIf { it in -127..0 && it != INVALID_RSSI },
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            frequencyMhz = info?.frequency?.takeIf { it > 0 },
            gatewayIpv4 = gateway,
        )
    }

    private companion object {
        const val INVALID_RSSI = -127
    }
}
