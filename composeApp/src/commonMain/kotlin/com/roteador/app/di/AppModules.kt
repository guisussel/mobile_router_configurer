package com.roteador.app.di

import com.roteador.core.data.di.dataModule
import com.roteador.core.platform.platformModule
import com.roteador.drivers.askey.askeyDriverModule
import com.roteador.drivers.generic.genericDriverModule
import com.roteador.feature.dashboard.dashboardModule
import com.roteador.feature.devices.devicesModule
import com.roteador.feature.login.loginModule
import com.roteador.feature.settings.settingsModule
import com.roteador.feature.signal.signalModule
import com.roteador.feature.wifi.wifiModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Supported router families. A new driver module is added here and nowhere else;
 * the generic one (lowest priority) answers when no specific driver does.
 */
val driverModules = listOf(askeyDriverModule, genericDriverModule)

val featureModules = listOf(loginModule, dashboardModule, devicesModule, wifiModule, signalModule, settingsModule)

fun appModules() = listOf(platformModule(), dataModule) + driverModules + featureModules

/** Called once per process: from `Application.onCreate` on Android, from `MainViewController` on iOS. */
fun initKoin(platformConfig: KoinAppDeclaration = {}) = startKoin {
    platformConfig()
    modules(appModules())
}
