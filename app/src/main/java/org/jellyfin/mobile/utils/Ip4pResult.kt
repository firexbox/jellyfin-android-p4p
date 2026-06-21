package org.jellyfin.mobile.utils

/**
 * Result of IP4P address resolution.
 */
sealed class Ip4pResult {
    /** Resolution succeeded. */
    data class Success(
        val ipv4: String,
        val port: Int,
        val url: String,
    ) : Ip4pResult()

    /** The input is not a valid IP4P address format. */
    object InvalidFormat : Ip4pResult()

    /** DNS resolution timed out (no response within the timeout). */
    object DnsTimeout : Ip4pResult()

    /** DNS resolved but no AAAA record with IP4P format was found. */
    object NoIp4pRecord : Ip4pResult()

    /** DNS resolution failed (host not found, network error, etc.). */
    data class DnsError(val message: String?) : Ip4pResult()
}
