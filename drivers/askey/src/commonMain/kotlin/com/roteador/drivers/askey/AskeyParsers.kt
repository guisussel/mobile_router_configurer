package com.roteador.drivers.askey

import com.roteador.core.model.Addressing
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.InternetBlock
import com.roteador.core.model.IpReservation
import com.roteador.core.model.Ipv4
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.InterfaceKind
import com.roteador.core.model.InternetStatus
import com.roteador.core.model.LanPort
import com.roteador.core.model.OpticalPower
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.model.WifiRadioSummary
import com.roteador.core.router.html.HtmlText
import com.roteador.core.router.html.PageScript
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Pure functions from page HTML to the app's model. No I/O, so every one is unit-tested. */
internal object AskeyParsers {

    fun routerInfo(aboutHtml: String): RouterInfo? {
        val manufacturer = HtmlText.valueAfterLabel(aboutHtml, "Fabricante:") ?: return null
        val model = HtmlText.valueAfterLabel(aboutHtml, "Modelo:") ?: return null
        return RouterInfo(
            manufacturer = manufacturer,
            model = model,
            firmware = HtmlText.valueAfterLabel(aboutHtml, "Versão do Software:") ?: "?",
            hardware = HtmlText.valueAfterLabel(aboutHtml, "Versão do Hardware:"),
            serialNumber = HtmlText.valueAfterLabel(aboutHtml, "Número de Série:"),
        )
    }

    /** `bruteTime` > 0 on the login page means the router is refusing logins for that many seconds. */
    fun lockoutSeconds(loginHtml: String): Int = PageScript(loginHtml)["bruteTime"]?.toIntOrNull() ?: 0

    fun sessionKey(html: String): String? = PageScript(html)["sessionKey"]

    fun status(indexHtml: String): RouterStatus {
        val script = PageScript(indexHtml)
        val radios = WifiBand.entries.mapNotNull { band ->
            val i = AskeyCodes.radioIndex(band)
            val ssid = script["wlSsid_main$i"] ?: return@mapNotNull null
            WifiRadioSummary(
                band = band,
                ssid = ssid,
                enabled = script["wlEnbl_main$i"] == "1",
                currentChannel = script["wlCurrentChannel_main$i"]?.toIntOrNull()?.takeIf { it > 0 },
            )
        }
        return RouterStatus(
            internet = InternetStatus(
                connected = script["pppStatus"] == "1",
                uptime = script["pppUptime"]?.toLongOrNull()?.seconds,
                publicIpv4 = script["pppIpv4Address"]?.takeIf { it.isNotBlank() },
                publicIpv6 = script["pppIpv6Address"]?.substringBefore('/')?.takeIf { it.isNotBlank() },
                dnsServers = script["dns4"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            ),
            optical = opticalPower(script["opticalPower"]),
            radios = radios,
            lanPorts = lanPorts(script["enetStatus"]),
        )
    }

    /** `TX:2.73 dBm;RX:-25.85 dBm;LT:-40 dBm;LR:-40 dBm` */
    fun opticalPower(raw: String?): OpticalPower? {
        if (raw == null) return null
        val rx = Regex("""RX:\s*(-?[\d.]+)""").find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val tx = Regex("""TX:\s*(-?[\d.]+)""").find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return OpticalPower(rxDbm = rx, txDbm = tx)
    }

    /** `0,eth0.1,0|1,eth0.2,0|2,eth0.3,0|3,eth0.4,1` = port index, interface, link up. */
    private fun lanPorts(raw: String?): List<LanPort> = raw.orEmpty().split('|').mapNotNull { entry ->
        val parts = entry.split(',')
        val index = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        LanPort(number = index + 1, linkUp = parts.getOrNull(2) == "1")
    }

    /**
     * Host table + association times. `lanHostList` rows are
     * `[active, name, ?, ipv4, ETH|WIFI|NONE, ?, mac, port-or-radio, DHCP|Static, ipv6]`.
     */
    fun devices(indexHtml: String, statisticsHtml: String): List<ConnectedDevice> {
        val connectedFor = associationTimes(statisticsHtml)
        val hosts = PageScript.stringRows(indexHtml, "lanHostList").mapNotNull { row ->
            val mac = row.getOrNull(6)?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val active = row.getOrNull(0) == "1"
            val slot = row.getOrNull(7)?.toIntOrNull()
            val link = when {
                !active -> DeviceLink.Offline
                row.getOrNull(4) == "WIFI" -> DeviceLink.Wifi(if (slot == 1) WifiBand.Band5GHz else WifiBand.Band2_4GHz)
                row.getOrNull(4) == "ETH" -> DeviceLink.Wired(port = slot?.takeIf { it >= 0 }?.plus(1))
                else -> DeviceLink.Offline
            }
            ConnectedDevice(
                name = row.getOrNull(1)?.takeIf { it.isNotBlank() },
                ipv4 = row.getOrNull(3)?.takeIf { it.isNotBlank() },
                mac = mac,
                link = link,
                connectedFor = connectedFor[mac],
                addressing = if (row.getOrNull(8).equals("Static", ignoreCase = true)) Addressing.Static else Addressing.Dhcp,
            )
        }
        return hosts.sortedWith(compareBy<ConnectedDevice> { !it.online }.thenBy { it.displayName.lowercase() })
    }

    /** `mac,DD:HH:MM:SS/mac,DD:HH:MM:SS` for both radios. */
    private fun associationTimes(statisticsHtml: String): Map<String, Duration> {
        val script = PageScript(statisticsHtml)
        return listOf("wlan2dot4GAssociatedList", "wlan5GAssociatedList")
            .flatMap { script[it].orEmpty().split('/') }
            .mapNotNull { entry ->
                val (mac, time) = entry.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
                parseDayClock(time)?.let { mac.lowercase() to it }
            }.toMap()
    }

    private fun parseDayClock(text: String): Duration? {
        val parts = text.split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size != 4) return null
        val (d, h, m, s) = parts
        return d.days + h.hours + m.minutes + s.seconds
    }

    /**
     * `wl0,rxPkts,rxBytes,rxErr,rxDrop,txPkts,txBytes,txErr,txDrop/wl1,…` and the same
     * shape in `ethIntfSts` for the four LAN ports.
     */
    fun counters(statisticsHtml: String): List<InterfaceCounters> {
        val script = PageScript(statisticsHtml)
        fun parse(raw: String?, kindOf: (String) -> InterfaceKind?): List<InterfaceCounters> =
            raw.orEmpty().split('/').mapNotNull { entry ->
                val fields = entry.split(',')
                if (fields.size < 7) return@mapNotNull null
                val kind = kindOf(fields[0]) ?: return@mapNotNull null
                InterfaceCounters(
                    interfaceId = fields[0],
                    kind = kind,
                    rxBytes = fields[2].toLongOrNull() ?: return@mapNotNull null,
                    txBytes = fields[6].toLongOrNull() ?: return@mapNotNull null,
                )
            }
        val wifi = parse(script["wlanIntfSts"]) { id ->
            when (id) {
                "wl0" -> InterfaceKind.WifiRadio(WifiBand.Band2_4GHz)
                "wl1" -> InterfaceKind.WifiRadio(WifiBand.Band5GHz)
                else -> null
            }
        }
        val lan = parse(script["ethIntfSts"]) { id ->
            id.removePrefix("eth").toIntOrNull()?.let { InterfaceKind.LanPort(it) }
        }
        return wifi + lan
    }

    /** Raw per-radio variables from the Wi-Fi settings page, keyed by band. */
    fun wifiBlocks(wifiHtml: String): Map<WifiBand, Map<String, String>> =
        PageScript(wifiHtml).blocks("ssid_id")
            .mapNotNull { block -> AskeyCodes.band(block["wlBand"])?.let { it to block } }
            .toMap()

    fun wifiConfig(band: WifiBand, block: Map<String, String>, channels: List<Int>): WifiBandConfig = WifiBandConfig(
        band = band,
        enabled = block["wlEnbl"] == "1",
        ssid = block["wlSsid"].orEmpty(),
        password = block["wlWpaPsk"]?.takeIf { it.isNotEmpty() },
        hidden = block["wlHide"] == "1",
        security = AskeyCodes.security(block["wlAuthMode"]),
        channel = block["wlChannel"]?.toIntOrNull()?.takeIf { it > 0 },
        currentChannel = block["wlCurrentChannel"]?.toIntOrNull()?.takeIf { it > 0 },
        width = AskeyCodes.width(block["wlNBwCap"]),
        txPowerPercent = block["wlTxPwrRatio"]?.toIntOrNull()?.let(AskeyCodes::roundPower) ?: 100,
        wpsEnabled = block["wlWpsMode"] == "1",
        bssid = block["wlBssid"],
        supportedChannels = channels,
        supportedWidths = AskeyCodes.supportedWidths(band),
        // The web UI edits name/password only for the main (2.4 GHz) network;
        // the 5 GHz one follows it with a "-5GHz" suffix.
        credentialsEditable = band == WifiBand.Band2_4GHz,
    )

    /** `dhcpStatic`: `iid/host/mac/ip|iid/host/mac/ip`. A host equal to the MAC means "no name". */
    fun reservations(body: String): List<IpReservation> = body.trim().split('|').mapNotNull { entry ->
        val parts = entry.split('/')
        if (parts.size < 4) return@mapNotNull null
        val (id, host, mac, ip) = parts
        if (Ipv4.parse(ip) == null) return@mapNotNull null
        IpReservation(
            id = id,
            name = host.takeUnless { it.isBlank() || it.equals(mac, ignoreCase = true) || it == "noname" },
            mac = mac.lowercase(),
            ipv4 = ip,
        )
    }

    /** `dhcpStart` / `dhcpEnd` on the LAN settings page. */
    fun dhcpRange(lanHtml: String): ClosedRange<Ipv4>? {
        val script = PageScript(lanHtml)
        val start = script["dhcpStart"]?.let(Ipv4::parse) ?: return null
        val end = script["dhcpEnd"]?.let(Ipv4::parse) ?: return null
        return start..end
    }

    /** `firewallRuleIndexList='5,7'`. */
    fun firewallRuleIds(firewallHtml: String): List<String> =
        PageScript(firewallHtml)["firewallRuleIndexList"].orEmpty().split(',', '|').map { it.trim() }.filter { it.isNotEmpty() }

    /** `firewallInterfaceList='2,1'` = WAN object, LAN object. */
    fun firewallInterfaces(firewallHtml: String): Pair<String, String>? {
        val parts = PageScript(firewallHtml)["firewallInterfaceList"]?.split(',') ?: return null
        return if (parts.size >= 2) parts[0] to parts[1] else null
    }

    /**
     * Answer of `sv_firewall.cmd?action=show`: `inst=chn,ruleId,enabled,order,name,…`.
     * Only rules this app created (name `Bloq-<ip>`) become [InternetBlock]s.
     */
    fun internetBlock(showBody: String): InternetBlock? {
        val fields = showBody.substringAfter("inst=", "").split(',')
        val ruleId = fields.getOrNull(1) ?: return null
        val name = fields.getOrNull(4) ?: return null
        val ip = name.removePrefix(BLOCK_PREFIX).takeIf { name.startsWith(BLOCK_PREFIX) && Ipv4.parse(it) != null } ?: return null
        return InternetBlock(id = ruleId, ipv4 = ip, label = name)
    }

    const val BLOCK_PREFIX = "Bloq-"

    /** Body of `sv_getvar.cmd?varName=channel_list`: `36,40,44,…`. */
    fun channelList(body: String): List<Int> =
        body.trim().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
}
