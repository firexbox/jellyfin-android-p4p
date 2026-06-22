package org.jellyfin.mobile.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * IP2P dual A-record DNS resolver.
 *
 * Resolves two DNS A records:
 *   1. {hostname}        → IP-encoded IPv4 → XOR decode → real server IPv4
 *   2. {prefix}.{hostname} → port-encoded IPv4 → extract last two octets → port
 *
 * Returns a domain-based URL (http://{hostname}:{port}) — the real IP is
 * injected via [Ip2pDns] into OkHttp's DNS layer, keeping TLS SNI and
 * Host header aligned with the domain certificate.
 */
object Ip2pResolver {

    /** DNS resolution timeout per query. */
    private val DNS_TIMEOUT = 5.seconds

    /** Default fallback ports for Jellyfin when port A record is unavailable. */
    private const val DEFAULT_PORT_HTTP = 8096
    private const val DEFAULT_PORT_HTTPS = 8920

    /** Service suffixes: m2 → m2-jellyfin */
    private val SERVICE_SUFFIXES = listOf("jellyfin", "jf")

    /**
     * Resolve an IP2P hostname — only the port is decoded from DNS.
     * The domain is used as-is in the URL, so system DNS resolves it
     * normally and SSL certificate validation works without bypass.
     *
     * Steps:
     *   1. DNS resolve {first}-jellyfin.{rest} → port-encoded A record → decode port
     *   2. Return "http://{hostname}:{port}" (domain-based, SSL friendly)
     *
     * @param hostname  e.g. "m2.firedragon19.gq"
     * @param https     if true, use "https" scheme
     * @return [Ip2pResult.Success] or an error type
     */
    suspend fun resolveToUrl(hostname: String, https: Boolean = false): Ip2pResult =
        withContext(Dispatchers.IO) {
            val scheme = if (https) "https" else "http"

            // Only resolve the port — domain resolves normally via system DNS
            val port = resolvePort(hostname)
                ?: if (https) DEFAULT_PORT_HTTPS else DEFAULT_PORT_HTTP
                    .also { Timber.w("IP2P: Port decode failed, fallback to $it") }
            Timber.d("IP2P: Port → $port")

            // Domain-based URL: SSL works normally, system DNS resolves to real IP
            val url = "$scheme://$hostname:$port"
            Timber.i("IP2P: $url")

            Ip2pResult.Success(ipv4 = hostname, port = port, url = url)
        }

    /**
     * Resolve an A record and return the raw IPv4 address string.
     */
    private suspend fun resolveARecord(hostname: String): String? {
        return try {
            val addresses = withTimeoutOrNull(DNS_TIMEOUT) {
                InetAddress.getAllByName(hostname)
            }
            addresses
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull()
                ?.hostAddress
        } catch (e: UnknownHostException) {
            Timber.d("IP2P: DNS lookup failed for $hostname")
            null
        }
    }

    /**
     * Resolve port by replacing the first label: {first}-{suffix}.{rest}
     *   m2.firedragon19.gq → m2-jellyfin.firedragon19.gq
     */
    private suspend fun resolvePort(hostname: String): Int? {
        val parts = hostname.split(".", limit = 2)
        if (parts.size == 2) {
            val first = parts[0]
            val rest = parts[1]
            for (suffix in SERVICE_SUFFIXES) {
                val portDomain = "$first-$suffix.$rest"
                val encoded = resolveARecord(portDomain) ?: continue
                val port = Ip2pCodec.decodePort(encoded)
                if (port != null) {
                    Timber.d("IP2P: Port from $portDomain → $encoded → $port")
                    return port
                }
            }
        }
        return null
    }
}
