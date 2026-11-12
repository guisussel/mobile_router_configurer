package com.roteador.core.data

import com.roteador.core.platform.SecureStore

/** Last router used, kept in the platform's secure storage. */
class CredentialsStore(private val store: SecureStore) {
    data class Stored(val host: String, val username: String, val password: String?)

    fun load(): Stored? {
        val host = store.get(KEY_HOST) ?: return null
        val username = store.get(KEY_USERNAME) ?: return null
        return Stored(host, username, store.get(KEY_PASSWORD))
    }

    fun save(host: String, username: String, password: String?) {
        store.put(KEY_HOST, host)
        store.put(KEY_USERNAME, username)
        if (password != null) store.put(KEY_PASSWORD, password) else store.remove(KEY_PASSWORD)
    }

    fun forgetPassword() = store.remove(KEY_PASSWORD)

    private companion object {
        const val KEY_HOST = "router.host"
        const val KEY_USERNAME = "router.username"
        const val KEY_PASSWORD = "router.password"
    }
}
