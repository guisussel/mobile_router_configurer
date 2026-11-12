package com.roteador.android

import android.app.Application
import com.roteador.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class RoteadorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RoteadorApplication) }
    }
}
