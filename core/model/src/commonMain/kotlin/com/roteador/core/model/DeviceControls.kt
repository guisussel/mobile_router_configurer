package com.roteador.core.model

/** A fixed IP the router always gives to one MAC. */
data class IpReservation(val id: String, val name: String?, val mac: String, val ipv4: String)

/** A firewall rule, created by this app, that blocks one device's internet access. */
data class InternetBlock(val id: String, val ipv4: String, val label: String)

/** Everything the device menu needs, read in one go when it opens. */
data class DeviceControls(
    val reservations: List<IpReservation>,
    val blocks: List<InternetBlock>,
    /** Addresses the router's DHCP hands out; reservations must stay inside. */
    val dhcpRange: ClosedRange<Ipv4>?,
) {
    fun reservationFor(mac: String?): IpReservation? =
        mac?.let { m -> reservations.firstOrNull { it.mac.equals(m, ignoreCase = true) } }

    fun blockFor(ipv4: String?): InternetBlock? = ipv4?.let { ip -> blocks.firstOrNull { it.ipv4 == ip } }
}

/** IPv4 address as a comparable number. */
data class Ipv4(val value: Long) : Comparable<Ipv4> {
    override fun compareTo(other: Ipv4) = value.compareTo(other.value)

    override fun toString() = listOf(24, 16, 8, 0).joinToString(".") { ((value shr it) and 0xFF).toString() }

    companion object {
        fun parse(text: String): Ipv4? {
            val parts = text.trim().split('.')
            if (parts.size != 4) return null
            var value = 0L
            for (p in parts) {
                val n = p.toIntOrNull()?.takeIf { it in 0..255 && p.length <= 3 } ?: return null
                value = (value shl 8) or n.toLong()
            }
            return Ipv4(value)
        }
    }
}
