package org.jellyfin.mobile.utils

import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttp DNS override for IP2P.
 *
 * Maps IP2P domain names to their decoded real IPv4 addresses so that
 * connections use the domain in the URL (TLS SNI and Host header match
 * the certificate) while the actual socket connects to the decoded IP.
 *
 * Usage:
 *   // After IP2P resolution:
 *   Ip2pDns.register("y.e.com", "203.0.113.42")
 *
 *   // Configure OkHttp:
 *   OkHttpClient.Builder().dns(Ip2pDns)
 *
 *   // Non-IP2P domains fall through to SYSTEM DNS.
 */
object Ip2pDns : Dns {

    private val mapping = ConcurrentHashMap<String, InetAddress>()

    /**
     * Register a domain → IP mapping for IP2P connections.
     * Called after successful IP2P DNS resolution.
     */
    fun register(hostname: String, ipv4: String) {
        mapping[hostname.lowercase()] = InetAddress.getByName(ipv4)
    }

    /**
     * Remove the mapping for [hostname] (e.g. on disconnect).
     */
    fun unregister(hostname: String) {
        mapping.remove(hostname.lowercase())
    }

    /**
     * Clear all IP2P DNS mappings.
     */
    fun clear() {
        mapping.clear()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        mapping[hostname.lowercase()]?.let { return listOf(it) }
        return Dns.SYSTEM.lookup(hostname)
    }
}
