package com.roteador.drivers.askey

import com.roteador.core.model.Ipv4
import com.roteador.core.model.RouterException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskeyDeviceControlsTest {
    private val router = FakeAskeyRouter()
    private val factory = AskeyRtfDriverFactory()
    private val baseUrl = "http://192.168.15.1"

    private suspend fun driver() = factory.create(router.client, baseUrl, factory.identify(router.client, baseUrl)!!)
        .also { it.login("admin", "certa") }

    @Test
    fun parsesReservationsRangeAndOnlyOwnFirewallRules() {
        val list = AskeyParsers.reservations("0/b8:27:eb:37:8d:30/b8:27:eb:37:8d:30/192.168.15.5|3/TV/AA:BB:CC:DD:EE:FF/192.168.15.50")
        assertNull(list[0].name) // host equal to MAC = no name
        assertEquals("TV", list[1].name)
        assertEquals("aa:bb:cc:dd:ee:ff", list[1].mac)
        assertEquals(Ipv4.parse("192.168.15.2")!!..Ipv4.parse("192.168.15.200")!!, AskeyParsers.dhcpRange(AskeyFixtures.lan))
        assertNull(AskeyParsers.internetBlock(AskeyFixtures.DEFAULT_RULE))
        assertEquals("192.168.15.7", AskeyParsers.internetBlock("inst=3,6,1,1,Bloq-192.168.15.7,0,2,1")?.ipv4)
    }

    @Test
    fun reservesChangesAndRemovesAnIp() = runTest {
        val driver = driver()
        driver.reserveIp("0a:b1:a6:a2:a8:58", "Tablet / sala", "192.168.15.60")
        var controls = driver.deviceControls()
        val reservation = controls.reservationFor("0A:B1:A6:A2:A8:58")!!
        assertEquals("192.168.15.60", reservation.ipv4)
        assertEquals("Tablet  sala", reservation.name) // '/' would break the router's format

        driver.reserveIp("0a:b1:a6:a2:a8:58", "Tablet", "192.168.15.61") // same MAC = edit, not a second entry
        controls = driver.deviceControls()
        assertEquals(2, controls.reservations.size)

        driver.removeReservation(controls.reservationFor("0a:b1:a6:a2:a8:58")!!.id)
        assertNull(driver.deviceControls().reservationFor("0a:b1:a6:a2:a8:58"))
    }

    @Test
    fun refusesIpOutsideRangeOrTaken() = runTest {
        val driver = driver()
        assertFailsWith<RouterException.Rejected> { driver.reserveIp("0a:b1:a6:a2:a8:58", "x", "192.168.15.250") }
        assertFailsWith<RouterException.Rejected> { driver.reserveIp("0a:b1:a6:a2:a8:58", "x", "192.168.15.5") }
    }

    @Test
    fun blockCreatesRejectRuleForTheIpAndUnblockDeletesIt() = runTest {
        val driver = driver()
        driver.blockInternet("192.168.15.7", null)
        assertEquals("-1,1,Bloq-192.168.15.7,0,2,1,0,*,192.168.15.7,176,-1,-1,-1,-1,32,32", router.firewallCreates.single())

        val block = driver.deviceControls().blockFor("192.168.15.7")!!
        driver.blockInternet("192.168.15.7", null) // already blocked: no second rule
        assertEquals(1, router.firewallCreates.size)

        driver.unblockInternet(block.id)
        assertNull(driver.deviceControls().blockFor("192.168.15.7"))
        assertTrue("5" in router.firewallRules) // the router's default rule is never touched
    }
}
