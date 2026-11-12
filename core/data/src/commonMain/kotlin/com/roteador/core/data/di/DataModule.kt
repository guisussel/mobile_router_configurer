package com.roteador.core.data.di

import com.roteador.core.data.CredentialsStore
import com.roteador.core.data.DefaultRouterRepository
import com.roteador.core.data.LiveTrafficMonitor
import com.roteador.core.data.RouterRepository
import com.roteador.core.router.RouterConnector
import com.roteador.core.router.RouterDriverRegistry
import org.koin.dsl.module

/**
 * Needs, from other modules: `platformModule()` (WifiLinkProvider, SecureStore)
 * and at least one `RouterDriverFactory` binding (driver modules provide them).
 */
val dataModule = module {
    single<RouterConnector> { RouterDriverRegistry(factories = getAll()) }
    single { CredentialsStore(get()) }
    single<RouterRepository> { DefaultRouterRepository(get(), get(), get()) }
    factory { LiveTrafficMonitor(get()) }
}
