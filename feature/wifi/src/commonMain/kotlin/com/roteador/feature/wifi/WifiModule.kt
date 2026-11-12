package com.roteador.feature.wifi

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val wifiModule = module {
    // Explicit lambda: the second constructor parameter has a default value.
    viewModel { WifiViewModel(get()) }
}
