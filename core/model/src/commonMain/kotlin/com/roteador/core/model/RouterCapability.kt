package com.roteador.core.model

/** What a driver can do. Screens hide or explain what the connected router lacks. */
enum class RouterCapability {
    /** Internet status (connected, uptime, public IP). */
    Status,

    /** Fiber optical power. */
    OpticalPower,

    /** List of devices on the network. */
    Devices,

    /** Live byte counters. */
    Traffic,

    /** Read and change Wi-Fi settings. */
    WifiSettings,

    Reboot,

    /** Fixed IP (DHCP reservation) per device, with a name. */
    IpReservation,

    /** Cut a device's internet access with a firewall rule. */
    InternetBlock,
}
