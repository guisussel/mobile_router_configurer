package com.roteador.core.platform

import com.roteador.core.model.WifiLink

/**
 * iOS does not give regular apps the signal strength, link speed or the list of
 * nearby networks (only "Hotspot Helper" apps, an entitlement Apple rarely grants).
 * The SSID needs location permission plus the "Access Wi-Fi Information"
 * entitlement (NEHotspotNetwork.fetchCurrent), left for a later version.
 */
class IosWifiLinkProvider : WifiLinkProvider {
    override fun current(): WifiLink = WifiLink(connected = true, limitedByPlatform = true)
}
