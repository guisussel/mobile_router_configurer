package com.roteador.app

import com.roteador.app.di.driverModules
import com.roteador.app.di.featureModules
import com.roteador.core.data.di.dataModule
import com.roteador.core.model.WifiLink
import com.roteador.core.platform.SecureStore
import com.roteador.core.platform.WifiLinkProvider
import com.roteador.core.router.RouterDriverFactory
import com.roteador.feature.dashboard.DashboardViewModel
import com.roteador.feature.devices.DevicesViewModel
import com.roteador.feature.login.LoginViewModel
import com.roteador.feature.settings.SettingsViewModel
import com.roteador.feature.signal.SignalViewModel
import com.roteador.feature.wifi.WifiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Fails fast if a binding is missing, instead of crashing when a tab opens. */
@OptIn(ExperimentalCoroutinesApi::class)
class DependencyGraphTest {
    private val fakePlatform = module {
        single<WifiLinkProvider> { WifiLinkProvider { WifiLink.Disconnected } }
        single<SecureStore> {
            object : SecureStore {
                val map = mutableMapOf<String, String>()
                override fun get(key: String) = map[key]
                override fun put(key: String, value: String) { map[key] = value }
                override fun remove(key: String) { map.remove(key) }
            }
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun everyViewModelCanBeCreated() {
        val koin = startKoin { modules(listOf(fakePlatform, dataModule) + driverModules + featureModules) }.koin

        assertEquals(1, koin.getAll<RouterDriverFactory>().size)
        assertNotNull(koin.get<LoginViewModel>())
        assertNotNull(koin.get<DashboardViewModel>())
        assertNotNull(koin.get<DevicesViewModel>())
        assertNotNull(koin.get<WifiViewModel>())
        assertNotNull(koin.get<SignalViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
    }
}
