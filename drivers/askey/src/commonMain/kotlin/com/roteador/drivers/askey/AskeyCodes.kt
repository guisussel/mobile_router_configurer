package com.roteador.drivers.askey

import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiSecurity

/** Numeric codes used by the web UI's forms, mapped to the app's model. */
internal object AskeyCodes {
    fun band(code: String?): WifiBand? = when (code) {
        "1" -> WifiBand.Band2_4GHz
        "2" -> WifiBand.Band5GHz
        else -> null
    }

    /** `ssid_id` / `acc_pt_id` / `wifi_id` of each radio. */
    fun radioIndex(band: WifiBand): Int = when (band) {
        WifiBand.Band2_4GHz -> 0
        WifiBand.Band5GHz -> 1
    }

    fun security(code: String?): WifiSecurity = when (code?.uppercase()) {
        "00", "94", "84" -> WifiSecurity.Open
        "B4" -> WifiSecurity.Wpa2Wpa3
        "A4" -> WifiSecurity.Wpa3
        else -> WifiSecurity.Wpa2 // "34" = WPA2-PSK AES, the factory setting
    }

    fun securityCode(security: WifiSecurity): String = when (security) {
        WifiSecurity.Open -> "00"
        WifiSecurity.Wpa2 -> "34"
        WifiSecurity.Wpa2Wpa3 -> "B4"
        WifiSecurity.Wpa3 -> "A4"
    }

    fun width(code: String?): ChannelWidth = when (code) {
        "1" -> ChannelWidth.Mhz20
        "2" -> ChannelWidth.Mhz40
        "3" -> ChannelWidth.Mhz80
        "4" -> ChannelWidth.Mhz160
        else -> ChannelWidth.Auto
    }

    fun widthCode(width: ChannelWidth): String = when (width) {
        ChannelWidth.Auto -> "0"
        ChannelWidth.Mhz20 -> "1"
        ChannelWidth.Mhz40 -> "2"
        ChannelWidth.Mhz80 -> "3"
        ChannelWidth.Mhz160 -> "4"
    }

    fun supportedWidths(band: WifiBand): List<ChannelWidth> = when (band) {
        WifiBand.Band2_4GHz -> listOf(ChannelWidth.Auto, ChannelWidth.Mhz20, ChannelWidth.Mhz40)
        WifiBand.Band5GHz -> ChannelWidth.entries
    }

    /** The UI offers power in steps of 10 %. */
    fun roundPower(percent: Int): Int = ((percent.coerceIn(0, 100) + 5) / 10) * 10
}
