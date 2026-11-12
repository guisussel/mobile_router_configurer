package com.roteador.feature.settings

import app.cash.turbine.test
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
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRouterRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rebootWaitsUntilRouterAnswersAgain() = runTest(dispatcher) {
        // Real time: the ViewModel measures elapsed time with the monotonic clock, so keep intervals tiny.
        repository.unreachableCalls = 3
        val viewModel = SettingsViewModel(repository, pollInterval = 1.milliseconds, giveUpAfter = 30.seconds)
        viewModel.state.test {
            skipItems(1)
            viewModel.reboot()
            advanceUntilIdle()
            assertIs<RebootPhase.Back>(expectMostRecentItem().reboot)
        }
        assertEquals(1, repository.rebootCount)
    }
}
