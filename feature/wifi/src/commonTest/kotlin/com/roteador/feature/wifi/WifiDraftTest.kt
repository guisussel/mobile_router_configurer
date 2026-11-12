package com.roteador.feature.wifi

import com.roteador.core.model.ChannelWidth
import com.roteador.core.model.WifiBand
import com.roteador.core.model.WifiBandChange
import com.roteador.core.testing.FakeRouterRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WifiDraftTest {
    private val main = FakeRouterRepository.sampleWifi(WifiBand.Band2_4GHz)
    private val fast = FakeRouterRepository.sampleWifi(WifiBand.Band5GHz)

    @Test
    fun untouchedDraftHasNoChange() {
        assertNull(WifiDraft(main).change)
    }

    @Test
    fun changeContainsOnlyEditedFields() {
        val change = assertNotNull(WifiDraft(main).copy(channel = 1, wpsEnabled = false).change)
        assertEquals(WifiBandChange(WifiBand.Band2_4GHz, channel = 1, wpsEnabled = false), change)
        assertEquals(listOf("Canal: 1", "Desligar WPS"), WifiDraft(main).copy(channel = 1, wpsEnabled = false).summary)
    }

    @Test
    fun goingBackToAutomaticChannelIsExplicit() {
        val manual = main.copy(channel = 6)
        assertEquals(WifiBandChange.AUTO_CHANNEL, WifiDraft(manual).copy(channel = null).change?.channel)
    }

    @Test
    fun invalidPasswordBlocksApply() {
        val draft = WifiDraft(main).copy(password = "curta")
        assertNotNull(draft.passwordError)
        assertFalse(draft.valid)
    }

    @Test
    fun credentialsOfFollowerRadioAreNeverSent() {
        val change = assertNotNull(WifiDraft(fast).copy(ssid = "Outro", width = ChannelWidth.Mhz40).change)
        assertNull(change.ssid)
        assertEquals(ChannelWidth.Mhz40, change.width)
    }
}
