package com.roteador.drivers.askey

/** Pages and CGI endpoints of the Askey RTF web UI (firmware BR_SV_g2.5_RTF_TEF004_V3.9). */
internal object AskeyPaths {
    const val LOGIN_PAGE = "login.asp"
    const val LOGIN = "cgi-bin/te_acceso_router.cgi"
    const val LOGOUT = "cgi-bin/te_logout.cgi"
    const val STATUS = "index_cliente.asp"
    const val STATISTICS = "device-management-statistics.asp"
    const val WIFI = "settings-wireless-network-u.asp"
    const val ABOUT = "about-power-box.asp"
    const val REBOOT_POPUP = "popup-reboot.asp"
    const val REBOOT = "cgi-bin/cbReboot.xml"
    const val GET_VAR = "cgi-bin/sv_getvar.cmd"
    const val SET_VAR = "cgi-bin/sv_setvar.cmd"
    const val LAN = "settings-local-network.asp"
    const val FIREWALL_PAGE = "settings-firewall.asp"
    const val FIREWALL = "cgi-bin/sv_firewall.cmd"

    /** Name, password and security of the main network (the UI's "Básico" tab). */
    const val WIFI_BASIC = "cgi-bin/te_wifi.wl"
    const val WIFI_ADVANCED_2_4 = "cgi-bin/te_wifi.wl"
    const val WIFI_ADVANCED_5 = "cgi-bin/te_wifi_5ghz.wl"
}

/** The web UI XORs every character of user, password and Wi-Fi key with 0x1f before sending. */
internal object AskeyCodec {
    fun obfuscate(text: String): String = buildString(text.length) {
        text.forEach { append((it.code xor 0x1f).toChar()) }
    }
}
