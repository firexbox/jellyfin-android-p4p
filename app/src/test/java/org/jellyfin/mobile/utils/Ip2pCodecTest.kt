package org.jellyfin.mobile.utils

import org.junit.Assert.*
import org.junit.Test

class Ip2pCodecTest {

    // ═══════════════════════════════════════════════════════════════
    // IP XOR Codec tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `encode IP with XOR 0x5A`() {
        assertEquals("145.90.43.112", Ip2pCodec.encodeIp("203.0.113.42"))
        assertEquals("150.138.150.138", Ip2pCodec.encodeIp("192.168.192.168"))
        assertEquals("90.180.90.180", Ip2pCodec.encodeIp("0.0.0.0")?.let { it }) // XOR self
    }

    @Test
    fun `decode IP reverses XOR`() {
        assertEquals("203.0.113.42", Ip2pCodec.decodeIp("145.90.43.112"))
        assertEquals("192.168.192.168", Ip2pCodec.decodeIp("150.138.150.138"))
        assertEquals("10.0.0.1", Ip2pCodec.decodeIp("80.90.90.91"))
    }

    @Test
    fun `round-trip IP encode-decode`() {
        val testIps = listOf("203.0.113.42", "192.168.1.1", "10.0.0.1", "172.16.254.1", "8.8.8.8")
        for (ip in testIps) {
            val encoded = Ip2pCodec.encodeIp(ip)
            assertNotNull("encode($ip) should not be null", encoded)
            val decoded = Ip2pCodec.decodeIp(encoded!!)
            assertEquals("Round-trip failed for $ip", ip, decoded)
        }
    }

    @Test
    fun `encode IP returns null for invalid input`() {
        assertNull(Ip2pCodec.encodeIp("not.an.ip"))
        assertNull(Ip2pCodec.encodeIp("256.0.0.1"))
        assertNull(Ip2pCodec.encodeIp("1.2.3"))
        assertNull(Ip2pCodec.encodeIp("1.2.3.4.5"))
        assertNull(Ip2pCodec.encodeIp(""))
    }

    // ═══════════════════════════════════════════════════════════════
    // Port prefix codec tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `encode port to IPv4 format`() {
        assertEquals("198.51.31.160", Ip2pCodec.encodePort(8096))
        assertEquals("198.51.1.187", Ip2pCodec.encodePort(443))
        assertEquals("198.51.0.80", Ip2pCodec.encodePort(80))
    }

    @Test
    fun `decode port from IPv4 format`() {
        assertEquals(8096, Ip2pCodec.decodePort("198.51.31.160"))
        assertEquals(443, Ip2pCodec.decodePort("198.51.1.187"))
        assertEquals(80, Ip2pCodec.decodePort("198.51.0.80"))
    }

    @Test
    fun `round-trip port encode-decode`() {
        val testPorts = listOf(1, 80, 443, 8080, 8096, 8920, 30000, 65535)
        for (port in testPorts) {
            val encoded = Ip2pCodec.encodePort(port)
            assertNotNull("encode($port) should not be null", encoded)
            val decoded = Ip2pCodec.decodePort(encoded!!)
            assertEquals("Round-trip failed for port $port", port, decoded)
        }
    }

    @Test
    fun `decode port is prefix-agnostic`() {
        // Decode should work regardless of the first two octets
        assertEquals(8096, Ip2pCodec.decodePort("0.0.31.160"))
        assertEquals(8096, Ip2pCodec.decodePort("198.51.31.160"))
        assertEquals(8096, Ip2pCodec.decodePort("255.255.31.160"))
        assertEquals(443, Ip2pCodec.decodePort("0.0.1.187"))
    }

    @Test
    fun `decode port returns null for invalid input`() {
        assertNull(Ip2pCodec.decodePort("not.an.ip"))
        assertNull(Ip2pCodec.decodePort("1.2.3"))
        assertNull(Ip2pCodec.decodePort(""))
        assertNull(Ip2pCodec.decodePort("198.51.0.0"))  // port 0 invalid
    }

    @Test
    fun `encode port returns null for invalid port`() {
        assertNull(Ip2pCodec.encodePort(0))
        assertNull(Ip2pCodec.encodePort(65536))
        assertNull(Ip2pCodec.encodePort(-1))
    }

    // ═══════════════════════════════════════════════════════════════
    // End-to-end: full IP2P scenario
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `full IP2P scenario - decode IP and port from encoded A records`() {
        // Simulated DNS A record responses for y.e.com
        val ipEncoded = "145.90.43.112"       // XOR of 203.0.113.42
        val portEncoded = "198.51.31.160"     // port 8096

        val realIp = Ip2pCodec.decodeIp(ipEncoded)
        val port = Ip2pCodec.decodePort(portEncoded)

        assertEquals("203.0.113.42", realIp)
        assertEquals(8096, port)

        // Final URL would be http://y.e.com:8096 (domain-based, TLS-friendly)
    }

    @Test
    fun `edge case - port 65535 max`() {
        assertEquals("198.51.255.255", Ip2pCodec.encodePort(65535))
        assertEquals(65535, Ip2pCodec.decodePort("198.51.255.255"))
    }

    @Test
    fun `edge case - port 1 min`() {
        assertEquals("198.51.0.1", Ip2pCodec.encodePort(1))
        assertEquals(1, Ip2pCodec.decodePort("198.51.0.1"))
    }

    @Test
    fun `edge case - IP 255_255_255_255`() {
        val encoded = Ip2pCodec.encodeIp("255.255.255.255")
        assertEquals("165.165.165.165", encoded) // 255^90=165
        assertEquals("255.255.255.255", Ip2pCodec.decodeIp(encoded!!))
    }

    @Test
    fun `edge case - IP 0_0_0_0`() {
        val encoded = Ip2pCodec.encodeIp("0.0.0.0")
        assertEquals("90.90.90.90", encoded)
        assertEquals("0.0.0.0", Ip2pCodec.decodeIp(encoded!!))
    }
}
