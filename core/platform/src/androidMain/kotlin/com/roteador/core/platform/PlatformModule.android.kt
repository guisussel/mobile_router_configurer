package com.roteador.core.platform

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<WifiLinkProvider> { AndroidWifiLinkProvider(androidContext()) }
    single<SecureStore> { KeystoreSecureStore(androidContext()) }
}
