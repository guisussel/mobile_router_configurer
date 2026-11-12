package com.roteador.core.platform

import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<WifiLinkProvider> { IosWifiLinkProvider() }
    single<SecureStore> { KeychainSecureStore() }
}
