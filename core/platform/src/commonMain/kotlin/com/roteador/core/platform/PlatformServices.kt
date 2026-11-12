package com.roteador.core.platform

import com.roteador.core.model.WifiLink
import org.koin.core.module.Module

/** This phone's own Wi-Fi connection. Cheap and synchronous: safe to call every second. */
fun interface WifiLinkProvider {
    fun current(): WifiLink
}

/**
 * Small encrypted key-value store for secrets (the router password).
 * Android: AES-GCM key in the Android Keystore. iOS: the Keychain.
 */
interface SecureStore {
    fun get(key: String): String?

    fun put(key: String, value: String)

    fun remove(key: String)
}

/** Koin bindings for [WifiLinkProvider] and [SecureStore] on the current platform. */
expect fun platformModule(): Module
