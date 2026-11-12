package com.roteador.drivers.askey

import com.roteador.core.router.RouterDriverFactory
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val askeyDriverModule = module {
    factoryOf(::AskeyRtfDriverFactory) bind RouterDriverFactory::class
}
