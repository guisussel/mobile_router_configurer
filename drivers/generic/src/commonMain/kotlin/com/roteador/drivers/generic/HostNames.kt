package com.roteador.drivers.generic

/** Name the network's DNS gives to [ip] (many routers register DHCP host names), or null. */
expect suspend fun reverseLookup(ip: String): String?
