package com.roteador.core.model

/** Identity of the box, read before login so the right driver can be picked. */
data class RouterInfo(
    val manufacturer: String,
    val model: String,
    val firmware: String,
    val hardware: String? = null,
    val serialNumber: String? = null,
)
