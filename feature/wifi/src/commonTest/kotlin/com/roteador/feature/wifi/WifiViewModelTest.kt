package com.roteador.feature.wifi

import com.roteador.core.model.WifiBand
import com.roteador.core.testing.FakeRouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class WifiViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRouterRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun appliesOnlyTheDiffAndReloads() = runTest(dispatcher) {
        val viewModel = WifiViewModel(repository, settleTime = 1.seconds)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.drafts.size)

        viewModel.edit(WifiBand.Band5GHz) { it.copy(channel = 36) }
        viewModel.apply(WifiBand.Band5GHz)
        advanceUntilIdle()

        val sent = repository.applied.single()
        assertEquals(36, sent.channel)
        assertEquals(null, sent.ssid)
        assertNotNull(viewModel.state.value.message)
        assertEquals(null, viewModel.state.value.applying)
    }
}
