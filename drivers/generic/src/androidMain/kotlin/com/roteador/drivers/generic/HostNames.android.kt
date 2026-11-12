package com.roteador.drivers.generic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

actual suspend fun reverseLookup(ip: String): String? = withTimeoutOrNull(1_500) {
    withContext(Dispatchers.IO) {
        runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()
            ?.takeIf { it != ip && it.isNotBlank() }
            ?.removeSuffix(".")
            ?.removeSuffix(".lan")
            ?.removeSuffix(".home")
    }
}
