package com.roteador.drivers.askey

import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.RouterException
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiBandChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AskeyRtfDriverTest {
    private val router = FakeAskeyRouter()
    private val factory = AskeyRtfDriverFactory()
    private val baseUrl = "http://192.168.15.1"

    private suspend fun driver() = factory.create(router.client, baseUrl, factory.identify(router.client, baseUrl)!!)

    @Test
    fun identifiesModelWithoutLoggingIn() = runTest {
        val info = assertNotNull(factory.identify(router.client, baseUrl))
        assertEquals("RTF8225VW-SV", info.model)
        assertTrue(router.calls.none { "te_acceso" in it })
    }

    @Test
    fun loginWithWrongPasswordIsReported() = runTest {
        assertFailsWith<RouterException.InvalidCredentials> { driver().login("admin", "errada") }
    }

    @Test
    fun loginThenReadsEverything() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        assertEquals("Casa", driver.status().radios.first().ssid)
        assertEquals(4, driver.devices().size)
        assertEquals(6, driver.counters().size)
        val wifi = driver.wifiConfig()
        assertEquals(listOf(1, 6, 11), wifi.first { it.band == WifiBand.Band2_4GHz }.supportedChannels)
        assertEquals(listOf(36, 40, 44, 48), wifi.first { it.band == WifiBand.Band5GHz }.supportedChannels)
    }

    @Test
    fun droppedSessionIsReportedAsExpired() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        router.loggedIn = false
        assertFailsWith<RouterException.SessionExpired> { driver.status() }
    }

    @Test
    fun radioChangeKeepsUntouchedFieldsAndUsesFreshKey() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        router.rotateSessionKey()
        driver.applyWifi(WifiBandChange(WifiBand.Band5GHz, channel = 36, width = ChannelWidth.Mhz40))

        val sent = assertNotNull(router.lastWifiApply)
        assertEquals("cgi-bin/te_wifi_5ghz.wl", sent["path"])
        assertEquals("36", sent["wlChannel"])
        assertEquals("2", sent["wlNBwCap"])
        assertEquals("57", sent["wlPhyType"]) // unchanged
        assertEquals("1", sent["wl_wsc_mode"]) // unchanged
        assertEquals("1", sent["ssid_id"])
    }

    @Test
    fun credentialChangeSendsObfuscatedPassword() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        driver.applyWifi(WifiBandChange(WifiBand.Band2_4GHz, ssid = "Nova Rede", password = "outra-senha-1"))

        val sent = assertNotNull(router.lastWifiApply)
        assertEquals("cgi-bin/te_wifi.wl", sent["path"])
        assertEquals("Nova Rede", sent["wlSsid"])
        assertEquals("outra-senha-1", AskeyCodec.obfuscate(sent.getValue("wlWpaPsk")))
        assertEquals("34", sent["wlAuthMode"])
    }

    @Test
    fun rejectedValidationDoesNotApply() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        router.validationAnswer = "1"
        assertFailsWith<RouterException.Rejected> {
            driver.applyWifi(WifiBandChange(WifiBand.Band2_4GHz, channel = 6))
        }
        assertEquals(null, router.lastWifiApply)
    }

    @Test
    fun fiveGhzCredentialsAreNotEditable() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        assertFailsWith<RouterException.Rejected> {
            driver.applyWifi(WifiBandChange(WifiBand.Band5GHz, ssid = "Outra"))
        }
    }

    @Test
    fun rebootUsesPopupSessionKey() = runTest {
        val driver = driver()
        driver.login("admin", "certa")
        driver.reboot()
        assertTrue(router.rebooted)
    }
}
