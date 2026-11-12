package com.roteador.drivers.generic

import com.roteador.core.router.html.HtmlText
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * UPnP Internet Gateway Device: the one standard most home routers speak
 * (when UPnP is on). Read-only here: identity, WAN status and byte counters.
 */
class UpnpIgd(
    val description: Description,
    private val http: HttpClient,
) {
    data class Service(val type: String, val controlUrl: String)

    data class Description(
        val manufacturer: String?,
        val modelName: String?,
        val modelNumber: String?,
        val friendlyName: String?,
        val services: List<Service>,
    ) {
        /** WANIPConnection (DHCP/IPoE) or WANPPPConnection (PPPoE, typical in Brazil). */
        val connection: Service? get() = services.firstOrNull { "WANIPConnection" in it.type || "WANPPPConnection" in it.type }
        val counters: Service? get() = services.firstOrNull { "WANCommonInterfaceConfig" in it.type }
    }

    suspend fun externalIp(): String? = connection("GetExternalIPAddress")?.let { tag(it, "NewExternalIPAddress") }

    /** `Connected`, `Disconnected`… and uptime in seconds. */
    suspend fun statusInfo(): Pair<String?, Long?>? = connection("GetStatusInfo")?.let {
        tag(it, "NewConnectionStatus") to tag(it, "NewUptime")?.toLongOrNull()
    }

    /** Total bytes received and sent on the WAN (32-bit on many routers: they wrap). */
    suspend fun totalBytes(): Pair<Long, Long>? {
        val service = description.counters ?: return null
        val rx = tag(soap(service, "GetTotalBytesReceived"), "NewTotalBytesReceived")?.toLongOrNull() ?: return null
        val tx = tag(soap(service, "GetTotalBytesSent"), "NewTotalBytesSent")?.toLongOrNull() ?: return null
        return rx to tx
    }

    private suspend fun connection(action: String): String? = description.connection?.let { soap(it, action) }

    private suspend fun soap(service: Service, action: String): String {
        val envelope = """<?xml version="1.0"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:$action xmlns:u="${service.type}"/></s:Body></s:Envelope>"""
        val response = http.post(service.controlUrl) {
            header("SOAPAction", "\"${service.type}#$action\"")
            contentType(ContentType.parse("text/xml; charset=\"utf-8\""))
            setBody(envelope)
        }
        return if (response.status.isSuccess()) response.bodyAsText() else ""
    }

    companion object {
        /**
         * Asks [host] directly (unicast, so no multicast permission is needed on
         * iOS) for its root device. Returns null when UPnP is off.
         */
        suspend fun discover(host: String, http: HttpClient, timeoutMillis: Long = 2_000): UpnpIgd? {
            val location = ssdpLocation(host, timeoutMillis) ?: return null
            val response = runCatching { http.get(location) }.getOrNull() ?: return null
            if (!response.status.isSuccess()) return null
            return UpnpIgd(parseDescription(response.bodyAsText(), location), http)
        }

        private suspend fun ssdpLocation(host: String, timeoutMillis: Long): String? = runCatching {
            SelectorManager(Dispatchers.IO).use { selector ->
                aSocket(selector).udp().bind().use { socket ->
                    val search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
                    socket.send(Datagram(Buffer().apply { writeString(search) }, InetSocketAddress(host, 1900)))
                    withTimeoutOrNull(timeoutMillis) { parseLocation(socket.receive().packet.readString()) }
                }
            }
        }.getOrNull()

        /** `LOCATION: http://192.168.0.1:5000/rootDesc.xml` from an SSDP answer. */
        fun parseLocation(ssdpAnswer: String): String? = ssdpAnswer.lineSequence()
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

        /** Device description XML -> identity + services, control URLs made absolute. */
        fun parseDescription(xml: String, location: String): Description {
            val base = tag(xml, "URLBase")?.takeIf { it.isNotBlank() } ?: location
            val services = Regex("""<service>(.*?)</service>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
                val block = m.groupValues[1]
                val type = tag(block, "serviceType") ?: return@mapNotNull null
                val control = tag(block, "controlURL") ?: return@mapNotNull null
                Service(type, URLBuilder().takeFrom(base).apply { takeFrom(control) }.buildString())
            }.toList()
            return Description(
                manufacturer = tag(xml, "manufacturer"),
                modelName = tag(xml, "modelName"),
                modelNumber = tag(xml, "modelNumber"),
                friendlyName = tag(xml, "friendlyName"),
                services = services,
            )
        }

        internal fun tag(xml: String, name: String): String? =
            Regex("""<(?:\w+:)?$name>(.*?)</(?:\w+:)?$name>""", RegexOption.DOT_MATCHES_ALL).find(xml)
                ?.groupValues?.get(1)?.trim()?.let(HtmlText::decodeEntities)
    }
}
