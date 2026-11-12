package com.roteador.drivers.askey

import co.touchlab.kermit.Logger
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceControls
import com.roteador.core.model.InternetBlock
import com.roteador.core.model.Ipv4
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.InterfaceCounters
import com.roteador.core.model.RouterException
import com.roteador.core.model.RouterInfo
import com.roteador.core.model.RouterStatus
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiBandChange
import com.roteador.core.model.WifiBandConfig
import com.roteador.core.model.WifiRules
import com.roteador.core.model.WifiSecurity
import com.roteador.core.router.RouterDriver
import com.roteador.core.router.html.PageScript
import com.roteador.core.router.routerCall
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parameters

/**
 * Talks to the Askey web UI the way its own JavaScript does:
 * - session = `_httpdSessionId_` cookie, created by opening the login page;
 * - every page carries a one-shot `sessionKey` that the next change must echo;
 * - CGI endpoints answer 400 unless the request carries a form `Content-Type`
 *   (even with an empty body) and a same-origin `Referer`.
 */
internal class AskeyRtfDriver(
    private val http: HttpClient,
    private val baseUrl: String,
    override val info: RouterInfo,
) : RouterDriver {
    private val log = Logger.withTag("AskeyRtfDriver")

    override val defaultUsername = "admin"

    override val capabilities = RouterCapability.entries.toSet()

    override suspend fun login(username: String, password: String) = routerCall {
        http.get(endpoint(AskeyPaths.LOGIN_PAGE))
        http.submitForm(
            url = endpoint(AskeyPaths.LOGIN),
            formParameters = parameters {
                append("curWebPage", "/${AskeyPaths.STATUS}")
                append("loginUsername", AskeyCodec.obfuscate(username))
                append("loginPassword", AskeyCodec.obfuscate(password))
            },
        ) { referer(AskeyPaths.LOGIN_PAGE) }

        val landing = http.get(endpoint(AskeyPaths.STATUS))
        if (landing.isLoginPage()) {
            val seconds = AskeyParsers.lockoutSeconds(landing.bodyAsText())
            throw if (seconds > 0) RouterException.LockedOut(seconds) else RouterException.InvalidCredentials()
        }
    }

    override suspend fun logout() = routerCall {
        http.get(endpoint(AskeyPaths.LOGOUT))
        Unit
    }

    override suspend fun status(): RouterStatus = AskeyParsers.status(page(AskeyPaths.STATUS))

    override suspend fun devices(): List<ConnectedDevice> =
        AskeyParsers.devices(page(AskeyPaths.STATUS), page(AskeyPaths.STATISTICS))

    override suspend fun counters(): List<InterfaceCounters> =
        AskeyParsers.counters(page(AskeyPaths.STATISTICS)).ifEmpty {
            throw RouterException.UnexpectedResponse("sem contadores em ${AskeyPaths.STATISTICS}")
        }

    override suspend fun wifiConfig(): List<WifiBandConfig> {
        val blocks = AskeyParsers.wifiBlocks(page(AskeyPaths.WIFI))
        if (blocks.isEmpty()) throw RouterException.UnexpectedResponse("sem dados de Wi-Fi")
        return blocks.map { (band, block) ->
            val channels = runCatching {
                channelList(band, standard = block["wlPhyType"].orEmpty(), widthCode = block["wlNBwCap"].orEmpty())
            }.getOrDefault(emptyList())
            AskeyParsers.wifiConfig(band, block, channels)
        }.sortedBy { it.band }
    }

    override suspend fun applyWifi(change: WifiBandChange) {
        if (change.touchesCredentials) applyCredentials(change)
        if (change.touchesRadio) applyRadio(change)
    }

    override suspend fun reboot() {
        val key = sessionKeyOf(page(AskeyPaths.REBOOT_POPUP))
        val response = routerCall {
            http.submitForm(endpoint(AskeyPaths.REBOOT), Parameters.Empty) {
                url { parameters.append("sessionKey", key) }
                referer("device-management-resets.asp")
                header("X-Requested-With", "XMLHttpRequest")
            }
        }
        if (!response.status.isSuccess()) throw RouterException.Rejected("reinício recusado (HTTP ${response.status.value})")
    }

    override fun close() = http.close()

    // --- Device controls ---------------------------------------------------------

    override suspend fun deviceControls(): DeviceControls {
        val reservations = AskeyParsers.reservations(getVar("dhcpStatic", referer = AskeyPaths.LAN))
        val range = AskeyParsers.dhcpRange(page(AskeyPaths.LAN))
        return DeviceControls(reservations, internetBlocks(), range)
    }

    /** Same request as the "Reservar" button of Rede Local: `modDhcpStatic` with `iid/host/MAC/ip` (iid -1 = new). */
    override suspend fun reserveIp(mac: String, name: String, ipv4: String) {
        val ip = Ipv4.parse(ipv4) ?: throw RouterException.Rejected("IP inválido: $ipv4")
        val lanHtml = page(AskeyPaths.LAN)
        AskeyParsers.dhcpRange(lanHtml)?.let { range ->
            if (ip !in range) throw RouterException.Rejected("o IP precisa estar entre ${range.start} e ${range.endInclusive}")
        }
        val current = AskeyParsers.reservations(getVar("dhcpStatic", referer = AskeyPaths.LAN))
        current.firstOrNull { it.ipv4 == ipv4 && !it.mac.equals(mac, ignoreCase = true) }?.let {
            throw RouterException.Rejected("o IP $ipv4 já está reservado para outro aparelho")
        }
        val iid = current.firstOrNull { it.mac.equals(mac, ignoreCase = true) }?.id ?: "-1"
        val host = name.filterNot { it == '/' || it == '|' || it == '<' || it == '>' }.trim().take(32).ifEmpty { "noname" }
        setVar("modDhcpStatic", "$iid/$host/${mac.uppercase()}/$ipv4", sessionKeyOf(lanHtml))

        val saved = AskeyParsers.reservations(getVar("dhcpStatic", referer = AskeyPaths.LAN))
        if (saved.none { it.mac.equals(mac, ignoreCase = true) && it.ipv4 == ipv4 }) {
            throw RouterException.Rejected("o roteador não gravou a reserva")
        }
    }

    override suspend fun removeReservation(reservationId: String) {
        setVar("delDhcpStatic", reservationId, sessionKeyOf(page(AskeyPaths.LAN)))
    }

    /**
     * A "Rejeita Local" rule (LAN -> WAN, all protocols) whose source is the device's
     * IP, named `Bloq-<ip>` so the app can find it again. Same fields the firewall
     * page sends; the router's own default "accept LAN -> WAN" rule stays untouched.
     */
    override suspend fun blockInternet(ipv4: String, mac: String?) {
        if (Ipv4.parse(ipv4) == null) throw RouterException.Rejected("IP inválido: $ipv4")
        if (internetBlocks().any { it.ipv4 == ipv4 }) return
        val html = page(AskeyPaths.FIREWALL_PAGE)
        val (wan, lan) = AskeyParsers.firewallInterfaces(html)
            ?: throw RouterException.UnexpectedResponse("interfaces do firewall não encontradas")
        val rule = listOf(
            "-1", "1", AskeyParsers.BLOCK_PREFIX + ipv4,
            "0", // reject
            wan, lan, // destination interface, source interface
            "0", // IPv4
            "*", ipv4, // destination IP, source IP
            "176", // all protocols
            "-1", "-1", "-1", "-1", // no port filter
            "32", "32",
        ).joinToString(",")
        firewall("create", rule, sessionKeyOf(html))
        if (internetBlocks().none { it.ipv4 == ipv4 }) throw RouterException.Rejected("o roteador não gravou a regra de bloqueio")
    }

    override suspend fun unblockInternet(blockId: String) {
        firewall("delete", blockId, sessionKeyOf(page(AskeyPaths.FIREWALL_PAGE)))
    }

    private suspend fun internetBlocks(): List<InternetBlock> {
        val html = page(AskeyPaths.FIREWALL_PAGE)
        val key = sessionKeyOf(html)
        return AskeyParsers.firewallRuleIds(html).mapNotNull { id ->
            AskeyParsers.internetBlock(firewall("show", id, key))
        }
    }

    private suspend fun firewall(action: String, inst: String, sessionKey: String): String = cgiPost(
        AskeyPaths.FIREWALL,
        referer = AskeyPaths.FIREWALL_PAGE,
        query = listOf("action" to action, "inst" to inst, "sessionKey" to sessionKey),
    )

    private suspend fun getVar(name: String, referer: String): String =
        cgiPost(AskeyPaths.GET_VAR, referer, listOf("varName" to name))

    private suspend fun setVar(name: String, value: String, sessionKey: String): String = cgiPost(
        AskeyPaths.SET_VAR,
        referer = AskeyPaths.LAN,
        query = listOf("sessionKey" to sessionKey, "varName" to name, "varValue" to value),
    )

    /** CGI calls: parameters in the query, an empty form body (else HTTP 400). */
    private suspend fun cgiPost(path: String, referer: String, query: List<Pair<String, String>>): String = routerCall {
        val response = http.submitForm(endpoint(path), Parameters.Empty) {
            url { query.forEach { (k, v) -> parameters.append(k, v) } }
            referer(referer)
            header("X-Requested-With", "XMLHttpRequest")
        }
        if (response.isLoginPage()) throw RouterException.SessionExpired()
        if (!response.status.isSuccess()) throw RouterException.Rejected("HTTP ${response.status.value} em $path")
        response.bodyAsText()
    }

    // --- Wi-Fi changes -------------------------------------------------------

    /** "Básico" tab: name, password and security of the main network. */
    private suspend fun applyCredentials(change: WifiBandChange) {
        val html = page(AskeyPaths.WIFI)
        val block = AskeyParsers.wifiBlocks(html)[change.band]
            ?: throw RouterException.UnexpectedResponse("rede ${change.band.label} não encontrada")
        if (change.band != WifiBand.Band2_4GHz) {
            throw RouterException.Rejected("neste roteador, nome e senha do 5 GHz seguem os da rede 2,4 GHz")
        }
        val ssid = change.ssid ?: block["wlSsid"].orEmpty()
        WifiRules.validateSsid(ssid)?.let { throw RouterException.Rejected(it) }
        val security = change.security ?: AskeyCodes.security(block["wlAuthMode"])
        val password = change.password ?: block["wlWpaPsk"].orEmpty()

        val query = mutableListOf(
            "wlSsidIdx" to "0",
            "ssid_id" to block["ssid_id"].orEmpty(),
            "acc_pt_id" to block["acc_pt_id"].orEmpty(),
            "wlSsid" to ssid,
            "wlAuthMode" to AskeyCodes.securityCode(security),
        )
        if (security != WifiSecurity.Open) {
            WifiRules.validatePassword(password)?.let { throw RouterException.Rejected(it) }
            query += "wlWpaPsk" to AskeyCodec.obfuscate(password)
        }
        submitWifi(AskeyPaths.WIFI_BASIC, query, sessionKeyOf(html))
    }

    /** "Avançado" tabs: radio on/off, hidden, WPS, channel, width, power. Unchanged fields are sent as they are. */
    private suspend fun applyRadio(change: WifiBandChange) {
        val html = page(AskeyPaths.WIFI)
        val block = AskeyParsers.wifiBlocks(html)[change.band]
            ?: throw RouterException.UnexpectedResponse("rede ${change.band.label} não encontrada")
        val script = PageScript(html)

        val enabled = change.enabled ?: (block["wlEnbl"] == "1")
        val hidden = change.hidden ?: (block["wlHide"] == "1")
        val wps = (change.wpsEnabled ?: (block["wlWpsMode"] == "1")) && !hidden
        val macFilter = block["wlMacFlMode"] == "1"
        val wmm = block["wlWmm"] ?: "1"

        val query = mutableListOf(
            "wlSsidIdx" to "0",
            "ssid_id" to block["ssid_id"].orEmpty(),
            "acc_pt_id" to block["acc_pt_id"].orEmpty(),
            "wlPhyType" to block["wlPhyType"].orEmpty(),
            "wlEnbl" to enabled.flag(),
            "wlHide" to hidden.flag(),
            "wl_wsc_mode" to wps.flag(),
            "wlChannel" to (change.channel ?: block["wlChannel"]?.toIntOrNull() ?: 0).toString(),
            "wlNBwCap" to (change.width?.let(AskeyCodes::widthCode) ?: block["wlNBwCap"] ?: "0"),
            "wlTxPwrPcnt" to (change.txPowerPercent?.let(AskeyCodes::roundPower) ?: block["wlTxPwrRatio"]?.toIntOrNull()?.let(AskeyCodes::roundPower) ?: 100).toString(),
            "wlWme" to wmm,
        )
        if (wmm == "1") query += "wlWmeApsd" to (block["wlWmmUapsd"] ?: "1")
        query += "wlFltMacMode" to macFilter.flag()
        if (macFilter) query += "wlFltMacList" to script["fltmaclist"].orEmpty()
        query += "wlNCtrlsb" to if (block["wlNCtrlsb"] == "1") "1" else "0"

        val path = if (change.band == WifiBand.Band5GHz) AskeyPaths.WIFI_ADVANCED_5 else AskeyPaths.WIFI_ADVANCED_2_4
        submitWifi(path, query, sessionKeyOf(html))
    }

    /**
     * Mirrors the page's `applySetting()`: a dry run with `validation_only=1` that
     * must answer `0`, then the real request as a GET (the page navigates to it).
     */
    private suspend fun submitWifi(path: String, query: List<Pair<String, String>>, sessionKey: String) {
        val common = query + listOf(
            "wlSyncNvram" to "1",
            "unitNum" to "0",
            "sessionKey" to sessionKey,
            "curWebPage" to "/${AskeyPaths.WIFI}",
        )
        val validation = routerCall {
            http.submitForm(endpoint(path), Parameters.Empty) {
                url { (common + ("validation_only" to "1")).forEach { (k, v) -> parameters.append(k, v) } }
                referer(AskeyPaths.WIFI)
                header("X-Requested-With", "XMLHttpRequest")
            }
        }
        val verdict = validation.bodyAsText().trim()
        if (validation.isLoginPage()) throw RouterException.SessionExpired()
        if (verdict != "0") throw RouterException.Rejected("validação do roteador falhou ($verdict)")

        log.i { "Applying Wi-Fi change on $path (${query.joinToString { it.first }})" }
        val applied = routerCall {
            http.get(endpoint(path)) {
                url { common.forEach { (k, v) -> parameters.append(k, v) } }
                referer(AskeyPaths.WIFI)
            }
        }
        if (applied.isLoginPage()) throw RouterException.SessionExpired()
        if (!applied.status.isSuccess()) throw RouterException.Rejected("HTTP ${applied.status.value}")
    }

    private suspend fun channelList(band: WifiBand, standard: String, widthCode: String): List<Int> {
        val response = routerCall {
            http.submitForm(endpoint(AskeyPaths.GET_VAR), Parameters.Empty) {
                url {
                    parameters.append("varName", "channel_list")
                    parameters.append("wifi_id", AskeyCodes.radioIndex(band).toString())
                    parameters.append("standard", standard)
                    parameters.append("bandwidth", widthCode)
                }
                referer(AskeyPaths.WIFI)
                header("X-Requested-With", "XMLHttpRequest")
            }
        }
        return if (response.status.isSuccess()) AskeyParsers.channelList(response.bodyAsText()) else emptyList()
    }

    // --- Plumbing --------------------------------------------------------------

    /** GETs a page; a bounce to the login page means the session is gone. */
    private suspend fun page(path: String): String = routerCall {
        val response = http.get(endpoint(path))
        if (response.isLoginPage()) throw RouterException.SessionExpired()
        if (!response.status.isSuccess()) throw RouterException.UnexpectedResponse("HTTP ${response.status.value} em $path")
        response.bodyAsText()
    }

    private fun sessionKeyOf(html: String): String =
        AskeyParsers.sessionKey(html) ?: throw RouterException.UnexpectedResponse("página sem sessionKey")

    private fun endpoint(path: String) = "$baseUrl/$path"

    private fun HttpRequestBuilder.referer(page: String) {
        header(HttpHeaders.Referrer, endpoint(page))
        header(HttpHeaders.Origin, baseUrl)
    }

    private fun HttpResponse.isLoginPage(): Boolean =
        call.request.url.encodedPath.endsWith("/${AskeyPaths.LOGIN_PAGE}")

    private fun Boolean.flag() = if (this) "1" else "0"
}
