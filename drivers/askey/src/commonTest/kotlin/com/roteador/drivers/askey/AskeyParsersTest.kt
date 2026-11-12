package com.roteador.drivers.askey

import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.InterfaceKind
import com.roteador.core.model.Quality
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiSecurity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AskeyParsersTest {
    @Test
    fun readsRouterIdentityFromAboutPage() {
        val info = AskeyParsers.routerInfo(AskeyFixtures.about)!!
        assertEquals("Askey", info.manufacturer)
        assertEquals("RTF8225VW-SV", info.model)
        assertEquals("BR_SV_g2.5_RTF_TEF004_V3.9", info.firmware)
        assertEquals("REV4", info.hardware)
    }

    @Test
    fun readsStatusPage() {
        val status = AskeyParsers.status(AskeyFixtures.status)
        assertTrue(status.internet.connected)
        assertEquals(1621408.seconds, status.internet.uptime)
        assertEquals("203.0.113.10", status.internet.publicIpv4)
        assertEquals("2001:db8:1::10", status.internet.publicIpv6)
        assertEquals(listOf("198.51.100.1", "198.51.100.2"), status.internet.dnsServers)
        assertEquals(-25.850267, status.optical!!.rxDbm)
        assertEquals(Quality.Warning, status.optical!!.rxQuality)
        assertEquals(listOf("Casa", "Casa-5GHz"), status.radios.map { it.ssid })
        assertEquals(52, status.radios[1].currentChannel)
        assertEquals(listOf(false, false, false, true), status.lanPorts.map { it.linkUp })
    }

    @Test
    fun mergesHostTableWithAssociationTimes() {
        val devices = AskeyParsers.devices(AskeyFixtures.status, AskeyFixtures.statistics)
        assertEquals(4, devices.size)
        val tablet = devices.first { it.name == "Tablet" }
        assertEquals(DeviceLink.Wifi(WifiBand.Band2_4GHz), tablet.link)
        assertEquals(15.days + 22.hours + 37.minutes + 59.seconds, tablet.connectedFor)
        assertEquals(DeviceLink.Wifi(WifiBand.Band5GHz), devices.first { it.name == "Notebook" }.link)
        assertEquals(DeviceLink.Wired(port = 4), devices.first { it.ipv4 == "192.168.15.5" }.link)
        // Offline devices go last.
        assertEquals("TV-Sala", devices.last().name)
        assertFalse(devices.last().online)
    }

    @Test
    fun readsByteCountersPerInterface() {
        val counters = AskeyParsers.counters(AskeyFixtures.statistics)
        val wl0 = counters.first { it.interfaceId == "wl0" }
        assertEquals(InterfaceKind.WifiRadio(WifiBand.Band2_4GHz), wl0.kind)
        assertEquals(12193082277, wl0.rxBytes)
        assertEquals(192567937680, wl0.txBytes)
        assertEquals(InterfaceKind.LanPort(4), counters.first { it.interfaceId == "eth4" }.kind)
        assertEquals(6, counters.size)
    }

    @Test
    fun readsBothRadiosFromWifiPage() {
        val blocks = AskeyParsers.wifiBlocks(AskeyFixtures.wifi)
        assertEquals(setOf(WifiBand.Band2_4GHz, WifiBand.Band5GHz), blocks.keys)

        val main = AskeyParsers.wifiConfig(WifiBand.Band2_4GHz, blocks.getValue(WifiBand.Band2_4GHz), listOf(1, 6, 11))
        assertEquals("Casa & Cia", main.ssid)
        assertEquals("senha-de-teste", main.password)
        assertEquals(WifiSecurity.Wpa2, main.security)
        assertNull(main.channel) // 0 = automatic
        assertEquals(11, main.currentChannel)
        assertEquals(ChannelWidth.Mhz20, main.width)
        assertTrue(main.wpsEnabled)
        assertTrue(main.credentialsEditable)

        val fast = AskeyParsers.wifiConfig(WifiBand.Band5GHz, blocks.getValue(WifiBand.Band5GHz), emptyList())
        assertEquals(ChannelWidth.Mhz80, fast.width)
        assertFalse(fast.credentialsEditable)
    }

    @Test
    fun parsesChannelListAndLockout() {
        assertEquals(listOf(36, 40, 44), AskeyParsers.channelList("36,40,44\n"))
        assertEquals(0, AskeyParsers.lockoutSeconds(AskeyFixtures.login()))
        assertEquals(42, AskeyParsers.lockoutSeconds(AskeyFixtures.login(bruteTime = 42)))
    }

    @Test
    fun obfuscationIsTheWebUiXor() {
        assertEquals("~{rvq", AskeyCodec.obfuscate("admin"))
        assertEquals("admin", AskeyCodec.obfuscate(AskeyCodec.obfuscate("admin")))
    }
}
