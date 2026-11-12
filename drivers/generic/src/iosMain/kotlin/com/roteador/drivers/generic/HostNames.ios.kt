package com.roteador.drivers.generic

/** Not implemented on iOS yet (needs getnameinfo via cinterop); devices show their IP. */
actual suspend fun reverseLookup(ip: String): String? = null
