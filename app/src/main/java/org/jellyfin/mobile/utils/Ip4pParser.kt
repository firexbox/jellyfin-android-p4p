package org.jellyfin.mobile.utils

/**
 * IP4P address parser.
 *
 * IP4P is defined by [natmap](https://github.com/heiher/natmap) to encode an IPv4 address
 * and port into an IPv6-like address for distribution via DNS AAAA records.
 *
 * Format (from natmap source `hev-exec.c`):
 * ```
 * "2001::%02x%02x:%02x%02x:%02x%02x"
 * Arguments: p[0], p[1], q[0], q[1], q[2], q[3]
 * ```
 *
 * Where p[0],p[1] are the raw bytes of the port (stored as a network-byte-order `unsigned short`,
 * so on little-endian hosts the port bytes appear swapped in the string), and q[0..3] are the
 * IPv4 address bytes in natural order.
 *
 * Because the port byte order depends on the natmap host's endianness, both byte orders are
 * computed. The caller (or the SDK's server discovery) selects the working one.
 */
object Ip4pParser {
    /**
     * The fixed IPv6 prefix for all IP4P addresses.
     */
    private const val PREFIX = "2001"

    /**
     * Regex matching the canonical IP4P form: `2001::{port}:{hi16}:{lo16}`
     *
     * Each group is 1-4 lowercase hex digits. Trailing `::` or extra zero groups after the
     * three data groups are accepted (e.g. `2001::1f90:cb00:712a::`).
     */
    private val IP4P_PATTERN = Regex(
        """^2001::([0-9a-f]{1,4}):([0-9a-f]{1,4}):([0-9a-f]{1,4})(?::|::?[0-9a-f]*)?${'$'}""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parsed IP4P address data.
     *
     * @param ipv4  Dotted-quad IPv4 string, e.g. "203.0.113.42"
     * @param port Port number — the port hex group interpreted directly as a 16-bit integer.
     *             This matches the IP4P spec where `{port}` is the port in hex.
     * @param portSwapped Port with bytes swapped — workaround for natmap hosts where
     *                    the C code's raw byte access produces a byte-swapped hex group
     *                    (little-endian hosts storing a network-byte-order value).
     */
    data class Ip4pData(
        val ipv4: String,
        val port: Int,
        val portSwapped: Int,
    )

    /**
     * Returns `true` if [address] is an IP4P-formatted address.
     */
    fun isIp4p(address: String): Boolean = IP4P_PATTERN.matches(address.trim().lowercase())

    /**
     * Parses an IP4P address string and returns the decoded data, or `null` if the
     * string does not match the IP4P format.
     */
    fun parse(address: String): Ip4pData? {
        val match = IP4P_PATTERN.matchEntire(address.trim().lowercase()) ?: return null

        val portGroup = match.groupValues[1].padStart(4, '0')
        val hiGroup = match.groupValues[2].padStart(4, '0')
        val loGroup = match.groupValues[3].padStart(4, '0')

        // Parse each 16-bit hex group
        val portRaw = portGroup.toInt(16)
        val hiRaw = hiGroup.toInt(16)
        val loRaw = loGroup.toInt(16)

        // Split port into its two bytes as they appear in the string
        // In the string: byte0 is the first two hex chars, byte1 is the last two
        val portByte0 = (portRaw shr 8) and 0xff  // high byte of the hex group
        val portByte1 = portRaw and 0xff          // low byte of the hex group

        // Port: the hex group directly represents the port number per the IP4P spec
        val port = (portByte0 shl 8) or portByte1
        // Port swapped: workaround for little-endian natmap hosts where raw byte access
        // of a network-byte-order short produces byte-swapped output
        val portSwapped = (portByte1 shl 8) or portByte0

        // IPv4 bytes are always in natural order (network byte order)
        val b0 = (hiRaw shr 8) and 0xff
        val b1 = hiRaw and 0xff
        val b2 = (loRaw shr 8) and 0xff
        val b3 = loRaw and 0xff
        val ipv4 = "$b0.$b1.$b2.$b3"

        return Ip4pData(ipv4, port, portSwapped)
    }

    /**
     * Converts an IP4P address to HTTP/HTTPS URL candidates.
     * Returns both port interpretations as distinct URLs (direct hex + byte-swapped),
     * so the SDK's server discovery can probe both and select the working one.
     */
    fun toUrls(address: String, https: Boolean = false): List<String> {
        val data = parse(address) ?: return emptyList()
        val scheme = if (https) "https" else "http"
        return listOf(
            "$scheme://${data.ipv4}:${data.port}",
            "$scheme://${data.ipv4}:${data.portSwapped}",
        ).distinct()
    }

    /**
     * Converts an IP4P address to a single URL using the direct port
     * interpretation (the port hex group read as a 16-bit integer).
     * This matches the IP4P spec `2001::{port}:{hi16}:{lo16}`.
     */
    fun toUrl(address: String, https: Boolean = false): String? {
        val data = parse(address) ?: return null
        val scheme = if (https) "https" else "http"
        return "$scheme://${data.ipv4}:${data.port}"
    }
}
