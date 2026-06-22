package org.jellyfin.mobile.utils

/**
 * IP2P address codec — IP XOR obfuscation and port prefix encoding.
 *
 * IP2P splits the IPv4 address and port into two separate DNS A records,
 * both returned as encoded IPv4 values that require client-side decoding.
 *
 * IP encoding: XOR each byte with 0x5A to obfuscate the real IP.
 *   encode("203.0.113.42") → "145.90.43.112"
 *   decode("145.90.43.112") → "203.0.113.42"
 *
 * Port encoding: prefix 198.51 + port in last two octets.
 *   encode(8096) → "198.51.31.160"
 *   decode("198.51.31.160") → 8096
 */
object Ip2pCodec {

    /** XOR byte for IP obfuscation. Encodes and decodes via the same operation. */
    private const val XOR_BYTE = 0x5A

    /** Fixed prefix for port-encoded IPv4 addresses. 198.51 = TEST-NET-2 /16 extension (RFC 5737). */
    private const val PORT_PREFIX_OCTET1 = 198
    private const val PORT_PREFIX_OCTET2 = 51

    // ═══════════════════════════════════════════════════════════════
    // IP XOR Codec
    // ═══════════════════════════════════════════════════════════════

    /**
     * Encode an IPv4 address by XOR-ing each byte with [XOR_BYTE].
     * Returns null if [ipv4] is not a valid dotted-quad IPv4.
     */
    fun encodeIp(ipv4: String): String? {
        val parts = ipv4.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return null
        return parts.joinToString(".") { (it xor XOR_BYTE).toString() }
    }

    /**
     * Decode an XOR-obfuscated IPv4 address back to the real IP.
     * XOR is self-inverse, so decode == encode.
     */
    fun decodeIp(encoded: String): String? = encodeIp(encoded)

    // ═══════════════════════════════════════════════════════════════
    // Port Prefix Codec
    // ═══════════════════════════════════════════════════════════════

    /**
     * Encode a port number (1–65535) into an IPv4 address string.
     * Format: 198.51.{portHi}.{portLo}
     */
    fun encodePort(port: Int): String? {
        if (port !in 1..65535) return null
        val hi = port shr 8
        val lo = port and 0xFF
        return "$PORT_PREFIX_OCTET1.$PORT_PREFIX_OCTET2.$hi.$lo"
    }

    /**
     * Decode a port from an IPv4 address string.
     * Extracts the last two octets regardless of prefix (compatible with
     * 198.51.x.x, 0.0.x.x, etc.).
     * Returns null if the address is invalid or the port is out of range.
     */
    fun decodePort(ipv4: String): Int? {
        val parts = ipv4.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return null
        val hi = parts[2]
        val lo = parts[3]
        if (hi !in 0..255 || lo !in 0..255) return null
        val port = (hi shl 8) or lo
        return if (port in 1..65535) port else null
    }
}
