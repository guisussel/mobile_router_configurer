package com.roteador.feature.devices

import com.roteador.core.data.LoginRequest
import com.roteador.core.model.Addressing
import com.roteador.core.model.ConnectedDevice
import com.roteador.core.model.DeviceLink
import com.roteador.core.model.RouterCapability
import com.roteador.core.model.WifiBand
import com.roteador.core.testing.FakeRouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    private val repository = FakeRouterRepository()
    private val tablet = ConnectedDevice("Tablet", "192.168.15.6", "0a:b1:a6:a2:a8:58", DeviceLink.Wifi(WifiBand.Band2_4GHz), null, Addressing.Dhcp)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModel(): DevicesViewModel {
        repository.connect(LoginRequest("192.168.15.1", "admin", "certa", remember = false))
        return DevicesViewModel(repository)
    }

    @Test
    fun reservationIsValidatedAgainstRangeAndOtherDevices() = runTest {
        val vm = viewModel()
        assertNull(vm.validateReservation(tablet, "192.168.15.60"))
        assertNotNull(vm.validateReservation(tablet, "192.168.15.250"))
        assertNotNull(vm.validateReservation(tablet, "abc"))
        repository.reserveIp("aa:aa:aa:aa:aa:aa", "TV", "192.168.15.60")
        vm.refreshControls()
        assertNotNull(vm.validateReservation(tablet, "192.168.15.60"))
    }

    @Test
    fun blockingReservesTheCurrentIpFirst() = runTest {
        val vm = viewModel()
        vm.blockInternet(tablet)
        assertEquals("192.168.15.6", repository.controls.reservationFor(tablet.mac)?.ipv4)
        assertNotNull(repository.controls.blockFor("192.168.15.6"))

        vm.refreshControls()
        vm.unblockInternet(tablet)
        assertNull(repository.controls.blockFor("192.168.15.6"))
    }

    @Test
    fun genericRouterHasNoDeviceControls() = runTest {
        repository.capabilities = setOf(RouterCapability.Status, RouterCapability.Devices)
        val vm = viewModel()
        assertFalse(vm.state.value.canReserve)
        assertFalse(vm.state.value.canBlock)
    }
}
