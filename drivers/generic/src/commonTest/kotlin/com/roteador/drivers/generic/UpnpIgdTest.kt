package com.roteador.drivers.generic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpnpIgdTest {
    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
            <friendlyName>Roteador Sala</friendlyName>
            <manufacturer>TP-Link</manufacturer>
            <modelName>Archer C6</modelName>
            <modelNumber>3.0</modelNumber>
            <deviceList><device><deviceList><device>
              <serviceList>
                <service>
                  <serviceType>urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1</serviceType>
                  <controlURL>/upnp/control/WANCommonIFC1</controlURL>
                </service>
                <service>
                  <serviceType>urn:schemas-upnp-org:service:WANPPPConnection:1</serviceType>
                  <controlURL>/upnp/control/WANPPPConn1</controlURL>
                </service>
              </serviceList>
            </device></deviceList></device></deviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun readsLocationFromSsdpAnswer() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=120\r\nLocation: http://192.168.0.1:1900/igd.xml\r\nST: urn:x\r\n\r\n"
        assertEquals("http://192.168.0.1:1900/igd.xml", UpnpIgd.parseLocation(answer))
        assertNull(UpnpIgd.parseLocation("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test
    fun parsesIdentityAndMakesControlUrlsAbsolute() {
        val d = UpnpIgd.parseDescription(description, "http://192.168.0.1:1900/igd.xml")
        assertEquals("TP-Link", d.manufacturer)
        assertEquals("Archer C6", d.modelName)
        assertEquals("http://192.168.0.1:1900/upnp/control/WANPPPConn1", d.connection?.controlUrl)
        assertEquals("http://192.168.0.1:1900/upnp/control/WANCommonIFC1", d.counters?.controlUrl)
    }

    @Test
    fun soapCallsReadStatusIpAndCounters() = runTest {
        val http = HttpClient(MockEngine { request ->
            val action = request.headers["SOAPAction"].orEmpty()
            val body = when {
                "GetStatusInfo" in action -> "<NewConnectionStatus>Connected</NewConnectionStatus><NewUptime>3600</NewUptime>"
                "GetExternalIPAddress" in action -> "<NewExternalIPAddress>203.0.113.7</NewExternalIPAddress>"
                "GetTotalBytesReceived" in action -> "<NewTotalBytesReceived>1000</NewTotalBytesReceived>"
                "GetTotalBytesSent" in action -> "<NewTotalBytesSent>500</NewTotalBytesSent>"
                else -> ""
            }
            respond("<s:Envelope><s:Body><u:R>$body</u:R></s:Body></s:Envelope>", headers = headersOf(HttpHeaders.ContentType, "text/xml"))
        })
        val igd = UpnpIgd(UpnpIgd.parseDescription(description, "http://192.168.0.1:1900/igd.xml"), http)
        assertEquals("Connected" to 3600L, igd.statusInfo())
        assertEquals("203.0.113.7", igd.externalIp())
        assertEquals(1000L to 500L, igd.totalBytes())
    }

    @Test
    fun refusedConnectionMeansSomeoneIsThere() {
        assertTrue(LanScanner.isRefusal(Exception("Connection refused")))
        assertTrue(LanScanner.isRefusal(RuntimeException("wrapped", Exception("connect failed: ECONNREFUSED"))))
        assertFalse(LanScanner.isRefusal(Exception("No route to host")))
    }
}
