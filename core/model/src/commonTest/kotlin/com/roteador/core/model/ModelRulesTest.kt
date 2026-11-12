package com.roteador.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelRulesTest {
    @Test
    fun opticalPowerQualityFollowsGponLimits() {
        assertEquals(Quality.Good, OpticalPower(rxDbm = -19.0, txDbm = 2.5).rxQuality)
        assertEquals(Quality.Warning, OpticalPower(rxDbm = -25.85, txDbm = 2.7).rxQuality)
        assertEquals(Quality.Critical, OpticalPower(rxDbm = -28.1, txDbm = 2.7).rxQuality)
        assertEquals(Quality.Critical, OpticalPower(rxDbm = -6.0, txDbm = 2.7).rxQuality)
    }

    @Test
    fun wifiRulesMatchRouterValidation() {
        assertNull(WifiRules.validateSsid("Casa"))
        assertNotNull(WifiRules.validateSsid(" Casa"))
        assertNotNull(WifiRules.validateSsid("a".repeat(33)))
        assertNull(WifiRules.validatePassword("senha-forte-123"))
        assertNotNull(WifiRules.validatePassword("curta"))
        assertNotNull(WifiRules.validatePassword("acentuação!!"))
    }

    @Test
    fun bandComesFromFrequency() {
        assertEquals(WifiBand.Band2_4GHz, WifiLink(connected = true, frequencyMhz = 2462).band)
        assertEquals(WifiBand.Band5GHz, WifiLink(connected = true, frequencyMhz = 5260).band)
    }
}
