package com.roteador.app

import androidx.compose.ui.window.ComposeUIViewController
import com.roteador.app.di.initKoin

/** Entry point for iosApp: SwiftUI wraps this in a UIViewControllerRepresentable. */
@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController(configure = { initKoin() }) { App() }
