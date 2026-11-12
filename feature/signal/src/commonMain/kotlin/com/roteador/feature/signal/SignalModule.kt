package com.roteador.feature.signal

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val signalModule = module {
    viewModelOf(::SignalViewModel)
}
