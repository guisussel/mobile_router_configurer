package com.roteador.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    // Explicit lambda: the timing parameters have defaults (tests pass shorter ones).
    viewModel { SettingsViewModel(get()) }
}
