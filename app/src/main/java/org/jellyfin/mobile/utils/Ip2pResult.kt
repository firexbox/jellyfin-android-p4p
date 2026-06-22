package org.jellyfin.mobile.utils

/**
 * Result of IP2P dual A-record resolution.
 */
sealed class Ip2pResult {
    /** Both A records resolved and decoded successfully. */
    data class Success(
        val ipv4: String,       // decoded real IPv4
        val port: Int,          // decoded port
        val url: String,        // http://{domain}:{port} (domain-based, TLS friendly)
    ) : Ip2pResult()

    /** Failed to decode the IP A record (wrong format or not an IP2P domain). */
    object IpDecodeFailed : Ip2pResult()

    /** Failed to decode or resolve the port A record. */
    object PortDecodeFailed : Ip2pResult()

    /** DNS resolution timed out for either domain. */
    object DnsTimeout : Ip2pResult()

    /** DNS resolution failed (host not found, network error, etc.). */
    data class DnsError(val message: String?) : Ip2pResult()
}
