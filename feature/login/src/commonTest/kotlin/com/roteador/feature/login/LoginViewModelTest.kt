package com.roteador.feature.login

import com.roteador.core.data.SavedLogin
import com.roteador.core.testing.FakeRouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val repository = FakeRouterRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun wrongPasswordShowsMessageAndKeepsForm() {
        val viewModel = LoginViewModel(repository)
        viewModel.start(autoLogin = true)
        viewModel.onPasswordChange("errada")
        viewModel.connect()
        assertEquals(LoginUiState.Phase.Editing, viewModel.state.value.phase)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun rightPasswordConnects() {
        val viewModel = LoginViewModel(repository)
        viewModel.start(autoLogin = true)
        viewModel.onPasswordChange("certa")
        viewModel.connect()
        assertEquals(LoginUiState.Phase.Connected, viewModel.state.value.phase)
    }

    @Test
    fun savedPasswordLogsInOnlyWhenAutoLoginIsOn() {
        repository.saved = SavedLogin("192.168.15.1", "admin", hasPassword = true)
        repository.savedPassword = "certa"

        val manual = LoginViewModel(repository)
        manual.start(autoLogin = false)
        assertEquals(LoginUiState.Phase.Editing, manual.state.value.phase)

        val automatic = LoginViewModel(repository)
        automatic.start(autoLogin = true)
        assertEquals(LoginUiState.Phase.Connected, automatic.state.value.phase)
    }
}
