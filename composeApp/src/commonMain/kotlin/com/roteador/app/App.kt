package com.roteador.app

import androidx.compose.runtime.Composable
import com.roteador.app.navigation.AppNavHost
import com.roteador.core.designsystem.RoteadorTheme

@Composable
fun App() {
    RoteadorTheme {
        AppNavHost()
    }
}
