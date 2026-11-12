package com.roteador.core.model

enum class WifiBand(val label: String) {
    Band2_4GHz("2,4 GHz"),
    Band5GHz("5 GHz"),
}

enum class WifiSecurity(val label: String) {
    Open("Aberta"),
    Wpa2("WPA2"),
    Wpa2Wpa3("WPA2/WPA3"),
    Wpa3("WPA3"),
}

/** Channel width. [Auto] lets the router decide. */
enum class ChannelWidth(val label: String) {
    Auto("Automática"),
    Mhz20("20 MHz"),
    Mhz40("40 MHz"),
    Mhz80("80 MHz"),
    Mhz160("160 MHz"),
}

/** Full configuration of one radio, as the router currently has it. */
data class WifiBandConfig(
    val band: WifiBand,
    val enabled: Boolean,
    val ssid: String,
    val password: String?,
    val hidden: Boolean,
    val security: WifiSecurity,
    /** `null` = automatic channel selection. */
    val channel: Int?,
    val currentChannel: Int?,
    val width: ChannelWidth,
    val txPowerPercent: Int,
    val wpsEnabled: Boolean,
    val bssid: String?,
    val supportedChannels: List<Int> = emptyList(),
    val supportedWidths: List<ChannelWidth> = ChannelWidth.entries,
    /** `false` when the router derives this radio's name and password from another one. */
    val credentialsEditable: Boolean = true,
)

/** A requested change. `null` fields are left as they are. */
data class WifiBandChange(
    val band: WifiBand,
    val enabled: Boolean? = null,
    val ssid: String? = null,
    val password: String? = null,
    val hidden: Boolean? = null,
    val security: WifiSecurity? = null,
    /** Use [AUTO_CHANNEL] to switch back to automatic selection. */
    val channel: Int? = null,
    val width: ChannelWidth? = null,
    val txPowerPercent: Int? = null,
    val wpsEnabled: Boolean? = null,
) {
    val touchesCredentials: Boolean get() = ssid != null || password != null || security != null
    val touchesRadio: Boolean
        get() = enabled != null || hidden != null || channel != null || width != null ||
            txPowerPercent != null || wpsEnabled != null

    companion object {
        const val AUTO_CHANNEL = 0
    }
}

object WifiRules {
    const val SSID_MAX_LENGTH = 32
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 63

    /** Returns a user-facing error message, or `null` when the SSID is valid. */
    fun validateSsid(ssid: String): String? = when {
        ssid.isBlank() -> "O nome da rede não pode ficar vazio."
        ssid.length > SSID_MAX_LENGTH -> "Use no máximo $SSID_MAX_LENGTH caracteres."
        ssid.startsWith(' ') || ssid.endsWith(' ') || "  " in ssid -> "Evite espaços no início, no fim ou duplicados."
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.length < PASSWORD_MIN_LENGTH -> "A senha precisa de pelo menos $PASSWORD_MIN_LENGTH caracteres."
        password.length > PASSWORD_MAX_LENGTH -> "Use no máximo $PASSWORD_MAX_LENGTH caracteres."
        password.any { it.code !in 0x20..0x7e } -> "Use só letras sem acento, números e símbolos comuns."
        password.startsWith(' ') || password.endsWith(' ') -> "Evite espaços no início ou no fim."
        else -> null
    }
}
