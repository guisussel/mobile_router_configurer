package com.roteador.feature.wifi

import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.model.WifiRules
import com.roteador.core.model.WifiSecurity

/** Editable copy of one radio's settings. [change] is the diff the router will receive. */
data class WifiDraft(
    val original: WifiBandConfig,
    val ssid: String = original.ssid,
    val password: String = original.password.orEmpty(),
    val security: WifiSecurity = original.security,
    val channel: Int? = original.channel,
    val width: ChannelWidth = original.width,
    val txPowerPercent: Int = original.txPowerPercent,
    val wpsEnabled: Boolean = original.wpsEnabled,
    val hidden: Boolean = original.hidden,
    val enabled: Boolean = original.enabled,
) {
    val ssidError: String? get() = if (original.credentialsEditable) WifiRules.validateSsid(ssid) else null
    val passwordError: String?
        get() = if (original.credentialsEditable && security != WifiSecurity.Open) WifiRules.validatePassword(password) else null
    val valid: Boolean get() = ssidError == null && passwordError == null

    val change: WifiBandChange?
        get() {
            val c = original
            val editable = c.credentialsEditable
            val result = WifiBandChange(
                band = c.band,
                ssid = ssid.takeIf { editable && it != c.ssid },
                password = password.takeIf { editable && it != c.password.orEmpty() },
                security = security.takeIf { editable && it != c.security },
                channel = when {
                    channel == c.channel -> null
                    channel == null -> WifiBandChange.AUTO_CHANNEL
                    else -> channel
                },
                width = width.takeIf { it != c.width },
                txPowerPercent = txPowerPercent.takeIf { it != c.txPowerPercent },
                wpsEnabled = wpsEnabled.takeIf { it != c.wpsEnabled },
                hidden = hidden.takeIf { it != c.hidden },
                enabled = enabled.takeIf { it != c.enabled },
            )
            return result.takeIf { it.touchesCredentials || it.touchesRadio }
        }

    /** Plain-language list of what changes, for the confirmation dialog. */
    val summary: List<String>
        get() {
            val c = change ?: return emptyList()
            return buildList {
                c.ssid?.let { add("Nome da rede: $it") }
                c.password?.let { add("Senha nova") }
                c.security?.let { add("Segurança: ${it.label}") }
                c.channel?.let { add("Canal: ${if (it == WifiBandChange.AUTO_CHANNEL) "automático" else it}") }
                c.width?.let { add("Largura: ${it.label}") }
                c.txPowerPercent?.let { add("Potência: $it%") }
                c.wpsEnabled?.let { add(if (it) "Ligar WPS" else "Desligar WPS") }
                c.hidden?.let { add(if (it) "Ocultar a rede" else "Mostrar a rede") }
                c.enabled?.let { add(if (it) "Ligar esta rede" else "Desligar esta rede") }
            }
        }
}
