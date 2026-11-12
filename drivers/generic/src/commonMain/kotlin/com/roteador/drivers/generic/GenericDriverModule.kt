package com.roteador.drivers.generic

import com.roteador.core.router.RouterDriverFactory
import org.koin.dsl.bind
import org.koin.dsl.module

val genericDriverModule = module {
    factory { GenericDriverFactory() } bind RouterDriverFactory::class
}
